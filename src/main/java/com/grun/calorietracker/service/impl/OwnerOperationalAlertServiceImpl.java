package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.dto.OwnerDailySummaryDto;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.service.OwnerOperationalAlertService;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerOperationalAlertServiceImpl implements OwnerOperationalAlertService {
    private static final List<String> DELIVERABLE = List.of("PENDING", "RETRY");
    private final OwnerOperationalAlertRepository repository;
    private final UserRepository userRepository;
    private final MailDeliveryService mailDeliveryService;
    @Value("${grun.owner-alerts.enabled:false}") private boolean deliveryEnabled;
    @Value("${grun.security.owner-bootstrap.primary-email:}") private String primaryOwnerEmail;

    @Override
    @Transactional
    public void recordCriticalBackendError(OwnerErrorEvent event) {
        if (event == null || event.status() == null || event.status() < 500) return;
        Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        String route = safe(event.route(), 300, "/api/[unmapped]");
        String method = safe(event.method(), 12, "OTHER");
        String bucket = occurredAt.truncatedTo(ChronoUnit.HOURS).toString();
        String key = "BACKEND_5XX|" + sha256(event.status() + "|" + method + "|" + route + "|" + bucket);
        OwnerOperationalAlertEntity alert = repository.findByDedupeKey(key).orElseGet(OwnerOperationalAlertEntity::new);
        if (alert.getId() == null) {
            alert.setDedupeKey(key); alert.setCategory("BACKEND_ERROR"); alert.setSeverity("CRITICAL"); alert.setStatus("PENDING");
            alert.setTitleEn("Critical backend request failure"); alert.setTitleTr("Kritik backend istek hatası");
            alert.setMessageEn(method + " " + route + " returned HTTP " + event.status() + ". Open Error Center for the sanitized request record.");
            alert.setMessageTr(method + " " + route + " isteği HTTP " + event.status() + " döndürdü. Temizlenmiş istek kaydı için Hata Merkezi'ni açın.");
            alert.setTargetPath("/admin/system/errors" + (event.correlationId() == null || event.correlationId().isBlank() ? "" : "?correlationId=" + URLEncoder.encode(event.correlationId(), StandardCharsets.UTF_8)));
            alert.setOccurrenceCount(0L); alert.setFirstOccurredAt(occurredAt); alert.setAttemptCount(0); alert.setCreatedAt(Instant.now());
        }
        alert.setOccurrenceCount(alert.getOccurrenceCount() + 1); alert.setLastOccurredAt(occurredAt);
        if (!"SENT".equals(alert.getStatus())) { alert.setStatus("PENDING"); alert.setNextAttemptAt(Instant.now()); }
        alert.setUpdatedAt(Instant.now()); repository.save(alert);
    }

    @Override
    @Transactional
    public void recordApprovalRequired(long approvalId, AdminApprovalActionType actionType, Instant createdAt) {
        if (approvalId <= 0 || actionType == null) return;
        Instant occurredAt = createdAt == null ? Instant.now() : createdAt;
        String key = "APPROVAL_REQUIRED|" + approvalId;
        OwnerOperationalAlertEntity alert = repository.findByDedupeKey(key).orElseGet(OwnerOperationalAlertEntity::new);
        if (alert.getId() != null) return;
        boolean financial = switch (actionType) {
            case SUBSCRIPTION_UPDATE, AI_QUOTA_RESET, AI_ADDON_QUOTA_GRANT, ENTITLEMENT_MATRIX_APPLY,
                    PLAN_FEATURE_UPDATE, AI_QUOTA_REFUND -> true;
            default -> false;
        };
        alert.setDedupeKey(key); alert.setCategory(financial ? "FINANCIAL_APPROVAL" : "OPERATIONAL_APPROVAL");
        alert.setSeverity(financial ? "CRITICAL" : "HIGH"); alert.setStatus("PENDING");
        alert.setTitleEn(financial ? "Financial owner approval required" : "Owner approval required");
        alert.setTitleTr(financial ? "Finansal owner onayı gerekiyor" : "Owner onayı gerekiyor");
        alert.setMessageEn("Approval #" + approvalId + " for " + actionType.name() + " is waiting for an owner decision.");
        alert.setMessageTr(actionType.name() + " işlemi için #" + approvalId + " onayı owner kararını bekliyor.");
        alert.setTargetPath("/admin/approvals?approvalId=" + approvalId);
        alert.setOccurrenceCount(1L); alert.setFirstOccurredAt(occurredAt); alert.setLastOccurredAt(occurredAt);
        alert.setAttemptCount(0); alert.setNextAttemptAt(Instant.now()); alert.setCreatedAt(Instant.now()); alert.setUpdatedAt(Instant.now());
        repository.save(alert);
    }

    @Override
    @Transactional
    public void recordDailySummary(OwnerDailySummaryDto summary) {
        if (summary == null || summary.date() == null) return;
        String key = "DAILY_SUMMARY|" + summary.date();
        if (repository.findByDedupeKey(key).isPresent()) return;
        Instant now = Instant.now(); OwnerOperationalAlertEntity alert = new OwnerOperationalAlertEntity();
        alert.setDedupeKey(key); alert.setCategory("DAILY_SUMMARY"); alert.setSeverity("INFO"); alert.setStatus("PENDING");
        alert.setTitleEn("Owner daily operations summary · " + summary.date());
        alert.setTitleTr("Owner günlük operasyon özeti · " + summary.date());
        alert.setMessageEn("Backend failures: " + summary.backendErrorOccurrences() + ", approval requests: "
                + (summary.financialApprovalRequests() + summary.operationalApprovalRequests()) + ", pending approvals: " + summary.pendingApprovals()
                + ", error groups new/investigating/resolved/reopened: " + summary.newErrorGroups()+"/"+summary.investigatingErrorGroups()+"/"+summary.resolvedErrorGroups()+"/"+summary.reopenedErrorGroups()
                + ", failed alert deliveries: " + summary.failedAlerts() + ".");
        alert.setMessageTr("Backend hataları: " + summary.backendErrorOccurrences() + ", onay talepleri: "
                + (summary.financialApprovalRequests() + summary.operationalApprovalRequests()) + ", bekleyen onaylar: " + summary.pendingApprovals()
                + ", hata grupları yeni/inceleniyor/çözüldü/yeniden açıldı: " + summary.newErrorGroups()+"/"+summary.investigatingErrorGroups()+"/"+summary.resolvedErrorGroups()+"/"+summary.reopenedErrorGroups()
                + ", başarısız uyarı teslimatları: " + summary.failedAlerts() + ".");
        alert.setTargetPath("/admin/reports/owner-alerts?reportDate=" + summary.date());
        alert.setOccurrenceCount(1L); alert.setFirstOccurredAt(now); alert.setLastOccurredAt(now); alert.setAttemptCount(0);
        alert.setNextAttemptAt(now); alert.setCreatedAt(now); alert.setUpdatedAt(now); repository.save(alert);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${grun.owner-alerts.scan-interval-ms:60000}")
    public int deliverDue() {
        if (!deliveryEnabled || primaryOwnerEmail == null || primaryOwnerEmail.isBlank()) return 0;
        UserEntity owner = userRepository.findByEmail(primaryOwnerEmail).filter(user -> Boolean.TRUE.equals(user.getAccountEnabled())).orElse(null);
        if (owner == null) return 0;
        int delivered = 0;
        for (OwnerOperationalAlertEntity alert : repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(DELIVERABLE, Instant.now(), PageRequest.of(0, 20))) {
            try {
                boolean tr = owner.getPreferredLanguage() == PreferredLanguage.TR;
                String subject = tr ? alert.getTitleTr() : alert.getTitleEn();
                String message = (tr ? alert.getMessageTr() : alert.getMessageEn()) + "\n\n" + alert.getTargetPath()
                        + "\nOccurrences: " + alert.getOccurrenceCount();
                mailDeliveryService.sendTransactionalEmail(owner.getEmail(), subject, message);
                alert.setStatus("SENT"); alert.setSentAt(Instant.now()); alert.setLastErrorType(null); delivered++;
            } catch (RuntimeException failure) {
                int attempts = alert.getAttemptCount() + 1;
                alert.setAttemptCount(attempts); alert.setStatus(attempts >= 5 ? "FAILED" : "RETRY");
                alert.setNextAttemptAt(Instant.now().plus(Math.min(60, 1L << Math.min(attempts, 5)), ChronoUnit.MINUTES));
                alert.setLastErrorType(safe(failure.getClass().getName(), 240, "RuntimeException"));
            }
            alert.setUpdatedAt(Instant.now()); repository.save(alert);
        }
        return delivered;
    }

    private String safe(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.repository.TestFeedbackScreenshotEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminTestFeedbackService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminTestFeedbackServiceImpl implements AdminTestFeedbackService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final TestFeedbackSubmissionRepository repository;
    private final UserRepository userRepository;
    private final AdminAuditService auditService;
    private final TestFeedbackScreenshotEventRepository screenshotEventRepository;
    @PersistenceContext private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public AdminTestFeedbackPageDto list(TestFeedbackStatus status, TestFeedbackType type,
                                         TestFeedbackPlatform platform, String route, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TestFeedbackSubmissionEntity> result = repository.findAll(filters(status, type, platform, route), pageable);
        return new AdminTestFeedbackPageDto(result.map(this::dto).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTestFeedbackDto detail(Long id) {
        return dto(requireFeedback(id), true);
    }

    @Override
    @Transactional
    public AdminTestFeedbackDto update(Long id, AdminTestFeedbackUpdateRequestDto request,
                                       String adminEmail, String correlationId) {
        TestFeedbackSubmissionEntity entity = requireFeedback(id);
        UserEntity admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        Map<String, Object> oldValue = Map.of("status", entity.getStatus(), "internalNote", nullable(entity.getAdminNote()));
        entity.setStatus(request.status());
        entity.setAdminNote(trimToNull(request.internalNote()));
        entity.setReviewedBy(admin);
        entity.setReviewedAt(LocalDateTime.now());
        TestFeedbackSubmissionEntity saved = repository.save(entity);
        Map<String, Object> newValue = Map.of("status", saved.getStatus(), "internalNote", nullable(saved.getAdminNote()));
        auditService.record(adminEmail, AdminAuditActionType.RUNTIME_RECORD_CREATE,
                AdminAuditTargetType.RUNTIME_OPERATIONS, "test-feedback:" + id,
                oldValue, newValue, correlationId);
        return dto(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTestFeedbackAnalyticsDto analytics() {
        long total = repository.count();
        long lastSevenDays = entityManager.createQuery(
                        "select count(f) from TestFeedbackSubmissionEntity f where f.createdAt >= :from", Long.class)
                .setParameter("from", LocalDateTime.now().minusDays(7)).getSingleResult();
        long httpFailures = countWhere("f.lastHttpStatus >= 400");
        long slowRequests = countWhere("f.lastHttpDurationMs >= 2000");
        return new AdminTestFeedbackAnalyticsDto(total, lastSevenDays, httpFailures, slowRequests,
                grouped("status"), grouped("feedbackType"), grouped("platform"),
                groupedLimited("route", 8), groupedBuilds(8));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(TestFeedbackStatus status, TestFeedbackType type,
                            TestFeedbackPlatform platform, String route) {
        Page<TestFeedbackSubmissionEntity> result = repository.findAll(filters(status, type, platform, route),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "createdAt")));
        StringBuilder csv = new StringBuilder("id,createdAt,userEmail,type,status,platform,route,appVersion,buildNumber,description\r\n");
        result.forEach(item -> csv.append(item.getId()).append(',')
                .append(csv(item.getCreatedAt())).append(',').append(csv(item.getUser().getEmail())).append(',')
                .append(item.getFeedbackType()).append(',').append(item.getStatus()).append(',')
                .append(item.getPlatform()).append(',').append(csv(item.getRoute())).append(',')
                .append(csv(item.getAppVersion())).append(',').append(csv(item.getBuildNumber())).append(',')
                .append(csv(item.getDescription())).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<TestFeedbackSubmissionEntity> filters(TestFeedbackStatus status, TestFeedbackType type,
                                                                  TestFeedbackPlatform platform, String route) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (type != null) predicates.add(cb.equal(root.get("feedbackType"), type));
            if (platform != null) predicates.add(cb.equal(root.get("platform"), platform));
            if (route != null && !route.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("route")), "%" + route.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private long countWhere(String predicate) {
        return entityManager.createQuery(
                "select count(f) from TestFeedbackSubmissionEntity f where " + predicate, Long.class)
                .getSingleResult();
    }

    private Map<String, Long> groupedLimited(String field, int limit) {
        List<Object[]> rows = entityManager.createQuery(
                        "select f." + field + ", count(f) from TestFeedbackSubmissionEntity f " +
                                "where f." + field + " is not null group by f." + field + " order by count(f) desc",
                        Object[].class)
                .setMaxResults(limit)
                .getResultList();
        Map<String, Long> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row[0]), (Long) row[1]));
        return values;
    }

    private Map<String, Long> groupedBuilds(int limit) {
        List<Object[]> rows = entityManager.createQuery(
                        "select concat(coalesce(f.appVersion, '-'), ' (', coalesce(f.buildNumber, '-'), ')'), count(f) " +
                                "from TestFeedbackSubmissionEntity f group by f.appVersion, f.buildNumber order by count(f) desc",
                        Object[].class)
                .setMaxResults(limit)
                .getResultList();
        Map<String, Long> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row[0]), (Long) row[1]));
        return values;
    }
    private Map<String, Long> grouped(String field) {
        List<Object[]> rows = entityManager.createQuery(
                "select f." + field + ", count(f) from TestFeedbackSubmissionEntity f group by f." + field,
                Object[].class).getResultList();
        Map<String, Long> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row[0]), (Long) row[1]));
        return values;
    }

    private TestFeedbackSubmissionEntity requireFeedback(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Test feedback not found."));
    }

    private AdminTestFeedbackDto dto(TestFeedbackSubmissionEntity e) { return dto(e, false); }

    private AdminTestFeedbackDto dto(TestFeedbackSubmissionEntity e, boolean includeScreenshotEvents) {
        boolean screenshotAvailable = e.getScreenshotAttachedAt() != null && e.getScreenshotDeletedAt() == null
                && e.getScreenshotExpiresAt() != null && e.getScreenshotExpiresAt().isAfter(LocalDateTime.now());
        List<AdminTestFeedbackScreenshotEventDto> events = includeScreenshotEvents
                ? screenshotEventRepository.findByFeedbackIdOrderByCreatedAtAscIdAsc(e.getId()).stream()
                    .map(event -> new AdminTestFeedbackScreenshotEventDto(event.getEventType(), event.getOutcome(),
                            event.getReportedSizeBytes(), event.getActualSizeBytes(), event.getContentType(),
                            event.getErrorCode(), event.getDetail(), event.getCreatedAt())).toList()
                : List.of();
        String screenshotState = screenshotAvailable ? "ATTACHED" : events.isEmpty() ? "NOT_PROVIDED"
                : "ERROR".equals(events.get(events.size() - 1).outcome()) ? "FAILED" : "PENDING";
        return new AdminTestFeedbackDto(e.getId(), e.getUser().getEmail(), e.getFeedbackType(), e.getStatus(),
                e.getPlatform(), e.getRoute(), e.getPreviousRoute(), e.getDescription(), e.getAppVersion(),
                e.getBuildNumber(), e.getEasBuildId(), e.getCommitSha(), e.getOsVersion(), e.getDeviceModel(),
                e.getLanguageTag(), e.getMarketRegion(), e.getLastHttpStatus(), e.getLastHttpDurationMs(),
                e.getLastCorrelationId(), e.getNetworkState(), screenshotAvailable, e.getScreenshotExpiresAt(), screenshotState, events, e.getAdminNote(),
                e.getReviewedBy() == null ? null : e.getReviewedBy().getEmail(), e.getReviewedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private Object nullable(Object value) { return value == null ? "" : value; }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String csv(Object value) {
        if (value == null) return "";
        return "\"" + String.valueOf(value).replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}

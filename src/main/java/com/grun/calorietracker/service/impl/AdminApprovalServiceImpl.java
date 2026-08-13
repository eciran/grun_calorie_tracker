package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AdminApprovalRequestEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.AdminApprovalRequestRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminApprovalServiceImpl implements AdminApprovalService {
    private final AdminApprovalRequestRepository repository;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final SubscriptionService subscriptionService;
    private final AdminAiMealDraftService adminAiMealDraftService;
    private final AdminNotificationCampaignService campaignService;
    private final RuntimeOperationsService runtimeOperationsService;
    private final AdminAuditService auditService;
    private final Validator validator;

    @Override @Transactional
    public AdminApprovalRequestDto create(String maker, AdminApprovalCreateRequestDto request, String correlationId) {
        String safePayload = normalizePayload(request.actionType(), request.payload());
        AdminApprovalRequestEntity entity = new AdminApprovalRequestEntity();
        entity.setActionType(request.actionType()); entity.setStatus(AdminApprovalStatus.PENDING);
        entity.setMakerEmail(maker); entity.setTargetKey(request.targetKey().trim()); entity.setPayloadJson(safePayload);
        entity.setRequestReason(request.reason().trim()); entity.setCorrelationId(correlationId);
        entity.setCreatedAt(Instant.now()); entity.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        AdminApprovalRequestEntity saved = repository.save(entity);
        auditService.record(maker, AdminAuditActionType.ADMIN_APPROVAL_REQUEST, AdminAuditTargetType.ADMIN_APPROVAL,
                saved.getId().toString(), null, Map.of("actionType", saved.getActionType(), "targetKey", saved.getTargetKey()), correlationId);
        return toDto(saved);
    }

    @Override @Transactional(readOnly=true)
    public AdminApprovalPageDto list(AdminApprovalStatus status,int page,int size) {
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),50),Sort.by(Sort.Direction.DESC,"createdAt"));
        Page<AdminApprovalRequestEntity> result=status==null?repository.findAll(pageable):repository.findByStatus(status,pageable);
        return new AdminApprovalPageDto(result.getContent().stream().map(this::toDto).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.isFirst(),result.isLast());
    }

    @Override @Transactional
    public AdminApprovalRequestDto approve(Long id,String checker,boolean owner,String reauthToken,String reason,String correlationId) {
        AdminApprovalRequestEntity entity=requirePending(id);
        requireChecker(entity,checker,owner,reauthToken);
        execute(entity,checker,correlationId);
        entity.setStatus(AdminApprovalStatus.APPROVED);
            entity.setCheckerEmail(checker); entity.setDecisionReason(reason.trim()); entity.setDecidedAt(Instant.now());
            AdminApprovalRequestEntity saved=repository.save(entity);
            auditService.record(checker,AdminAuditActionType.ADMIN_APPROVAL_APPROVE,AdminAuditTargetType.ADMIN_APPROVAL,id.toString(),null,Map.of("actionType",entity.getActionType(),"maker",entity.getMakerEmail()),correlationId);
            return toDto(saved);
    }

    @Override @Transactional
    public AdminApprovalRequestDto reject(Long id,String checker,boolean owner,String reauthToken,String reason,String correlationId) {
        AdminApprovalRequestEntity entity=requirePending(id); requireChecker(entity,checker,owner,reauthToken);
        entity.setStatus(AdminApprovalStatus.REJECTED); entity.setCheckerEmail(checker);
        entity.setDecisionReason(reason.trim()); entity.setDecidedAt(Instant.now());
        AdminApprovalRequestEntity saved=repository.save(entity);
        auditService.record(checker,AdminAuditActionType.ADMIN_APPROVAL_REJECT,AdminAuditTargetType.ADMIN_APPROVAL,id.toString(),null,Map.of("actionType",entity.getActionType(),"maker",entity.getMakerEmail()),correlationId);
        return toDto(saved);
    }

    private void execute(AdminApprovalRequestEntity entity,String checker,String correlationId) {
        Long target=entity.getActionType()==AdminApprovalActionType.PLAN_FEATURE_UPDATE ? null : positiveTarget(entity.getTargetKey());
        JsonNode payload=readTree(entity.getPayloadJson());
        switch(entity.getActionType()) {
            case SUBSCRIPTION_UPDATE -> subscriptionService.updateUserSubscription(target,convert(payload,AdminSubscriptionUpdateRequestDto.class));
            case AI_QUOTA_RESET -> subscriptionService.resetUserAiQuota(target);
            case AI_ADDON_QUOTA_GRANT -> { AdminAiQuotaGrantRequestDto dto=convert(payload,AdminAiQuotaGrantRequestDto.class); subscriptionService.grantAiAddonQuota(target,dto.getAmount(),dto.getValidityDays()); }
            case ENTITLEMENT_MATRIX_APPLY -> subscriptionService.applyCurrentFeatureMatrixToUser(target);
            case PLAN_FEATURE_UPDATE -> executePlanFeature(entity.getTargetKey(), payload);
            case AI_QUOTA_REFUND -> adminAiMealDraftService.refundQuota(checker,target,convert(payload,AdminAiQuotaRefundRequestDto.class));
            case NOTIFICATION_CAMPAIGN_SCHEDULE -> { AdminNotificationCampaignScheduleRequestDto dto=convert(payload,AdminNotificationCampaignScheduleRequestDto.class); campaignService.schedule(target,dto.getScheduledAt(),checker,correlationId); }
            case RUNTIME_POLICY_UPDATE -> runtimeOperationsService.updatePolicy(checker,convert(payload,AdminRuntimeOperationsPolicyUpdateRequestDto.class));
        }
    }

    private void executePlanFeature(String targetKey, JsonNode payload) {
        String[] parts = targetKey.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Plan feature target must contain plan and feature.");
        SubscriptionPlan plan = SubscriptionPlan.valueOf(parts[0]);
        SubscriptionFeature feature = SubscriptionFeature.valueOf(parts[1]);
        AdminSubscriptionPlanFeatureUpdateRequestDto dto = convert(payload, AdminSubscriptionPlanFeatureUpdateRequestDto.class);
        subscriptionService.updatePlanFeature(plan, feature, dto.getEnabled(), dto.getEffectiveFrom(), dto.getAiCreditCost());
    }
    private String normalizePayload(AdminApprovalActionType type,JsonNode payload) {
        Object typed=switch(type) {
            case SUBSCRIPTION_UPDATE -> convert(payload,AdminSubscriptionUpdateRequestDto.class);
            case AI_QUOTA_RESET -> Map.of();
            case AI_ADDON_QUOTA_GRANT -> convert(payload,AdminAiQuotaGrantRequestDto.class);
            case ENTITLEMENT_MATRIX_APPLY -> Map.of();
            case PLAN_FEATURE_UPDATE -> convert(payload,AdminSubscriptionPlanFeatureUpdateRequestDto.class);
            case AI_QUOTA_REFUND -> convert(payload,AdminAiQuotaRefundRequestDto.class);
            case NOTIFICATION_CAMPAIGN_SCHEDULE -> convert(payload,AdminNotificationCampaignScheduleRequestDto.class);
            case RUNTIME_POLICY_UPDATE -> convert(payload,AdminRuntimeOperationsPolicyUpdateRequestDto.class);
        };
        if (!validator.validate(typed).isEmpty()) throw new IllegalArgumentException("Approval payload failed validation.");
        try { return objectMapper.writeValueAsString(typed); } catch(Exception e){ throw new IllegalArgumentException("Approval payload is invalid."); }
    }
    private <T>T convert(JsonNode node,Class<T> type){try{return objectMapper.treeToValue(node,type);}catch(Exception e){throw new IllegalArgumentException("Approval payload does not match the selected action.");}}
    private JsonNode readTree(String value){try{return objectMapper.readTree(value);}catch(Exception e){throw new IllegalStateException("Stored approval payload is invalid.");}}
    private Long positiveTarget(String key){try{long value=Long.parseLong(key);if(value<=0)throw new Exception();return value;}catch(Exception e){throw new IllegalArgumentException("Approval target must be a positive id.");}}
    private AdminApprovalRequestEntity requirePending(Long id){AdminApprovalRequestEntity e=repository.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Approval request was not found."));if(e.getStatus()!=AdminApprovalStatus.PENDING)throw new IllegalArgumentException("Approval request is no longer pending.");if(e.getExpiresAt().isBefore(Instant.now())){e.setStatus(AdminApprovalStatus.EXPIRED);repository.save(e);throw new IllegalArgumentException("Approval request has expired.");}return e;}
    private void requireChecker(AdminApprovalRequestEntity entity,String checker,boolean owner,String token){if(entity.getMakerEmail().equalsIgnoreCase(checker)&&!owner)throw new IllegalArgumentException("Only an owner can approve or reject their own request.");if(token==null||!jwtUtil.isAdminReauthenticationTokenValid(token,checker))throw new IllegalArgumentException("Fresh MFA re-authentication is required.");}
    private AdminApprovalRequestDto toDto(AdminApprovalRequestEntity e){return new AdminApprovalRequestDto(e.getId(),e.getActionType(),e.getStatus(),e.getMakerEmail(),e.getCheckerEmail(),e.getTargetKey(),readTree(e.getPayloadJson()),e.getRequestReason(),e.getDecisionReason(),e.getCreatedAt(),e.getExpiresAt(),e.getDecidedAt());}
}

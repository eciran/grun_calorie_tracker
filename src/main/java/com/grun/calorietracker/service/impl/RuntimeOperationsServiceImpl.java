package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminRuntimeApiMetricsDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordRequestDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRuntimePolicyRollbackRequestDto;
import com.grun.calorietracker.dto.RuntimeClientConfigDto;
import com.grun.calorietracker.entity.RuntimeOperationRecordEntity;
import com.grun.calorietracker.entity.RuntimeOperationsPolicyEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import com.grun.calorietracker.enums.RuntimeRolloutSegment;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.RuntimeOperationRecordRepository;
import com.grun.calorietracker.repository.RuntimeOperationsPolicyRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RuntimeApiMetricsService;
import com.grun.calorietracker.service.RuntimeOperationsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RuntimeOperationsServiceImpl implements RuntimeOperationsService {
    private static final long POLICY_ID = 1L;

    private final RuntimeOperationsPolicyRepository policyRepository;
    private final RuntimeOperationRecordRepository recordRepository;
    private final RuntimeApiMetricsService apiMetricsService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private volatile AdminRuntimeOperationsPolicyDto cachedPolicy;

    @PostConstruct
    void initializeCache() {
        policyRepository.findById(POLICY_ID).ifPresent(policy -> cachedPolicy = toDto(policy));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRuntimeOperationsPolicyDto getPolicy() {
        AdminRuntimeOperationsPolicyDto result = toDto(requiredPolicy());
        cachedPolicy = result;
        return result;
    }

    @Override
    @Transactional
    public AdminRuntimeOperationsPolicyDto updatePolicy(
            String adminEmail, AdminRuntimeOperationsPolicyUpdateRequestDto request) {
        RuntimeOperationsPolicyEntity policy = requiredPolicy();
        requireVersion(policy, request.getVersion());
        policy.setPreviousSnapshot(writeSnapshot(snapshot(policy)));
        applyRequest(policy, request);
        policy.setChangeReason(request.getReason().trim());
        policy.setUpdatedBy(adminEmail);
        policy.setUpdatedAt(LocalDateTime.now());
        AdminRuntimeOperationsPolicyDto result = toDto(policyRepository.saveAndFlush(policy));
        cachedPolicy = result;
        return result;
    }

    @Override
    @Transactional
    public AdminRuntimeOperationsPolicyDto rollbackPolicy(
            String adminEmail, AdminRuntimePolicyRollbackRequestDto request) {
        RuntimeOperationsPolicyEntity policy = requiredPolicy();
        requireVersion(policy, request.getVersion());
        if (policy.getPreviousSnapshot() == null || policy.getPreviousSnapshot().isBlank()) {
            throw new IllegalArgumentException("No previous runtime policy is available.");
        }
        PolicySnapshot current = snapshot(policy);
        PolicySnapshot previous = readSnapshot(policy.getPreviousSnapshot());
        applySnapshot(policy, previous);
        policy.setPreviousSnapshot(writeSnapshot(current));
        policy.setChangeReason(request.getReason().trim());
        policy.setUpdatedBy(adminEmail);
        policy.setUpdatedAt(LocalDateTime.now());
        AdminRuntimeOperationsPolicyDto result = toDto(policyRepository.saveAndFlush(policy));
        cachedPolicy = result;
        return result;
    }

    @Override
    public AdminRuntimeApiMetricsDto getApiMetrics() {
        AdminRuntimeOperationsPolicyDto policy = currentPolicy();
        return apiMetricsService.snapshot(policy.getApiLatencyWarningMs(), policy.getApiErrorRateThreshold());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminRuntimeOperationRecordDto> getRecords(
            RuntimeOperationRecordType type, RuntimeOperationStatus status, Pageable pageable) {
        Page<RuntimeOperationRecordEntity> records;
        if (type != null && status != null) records = recordRepository.findByRecordTypeAndStatus(type, status, pageable);
        else if (type != null) records = recordRepository.findByRecordType(type, pageable);
        else if (status != null) records = recordRepository.findByStatus(status, pageable);
        else records = recordRepository.findAll(pageable);
        return records.map(this::toDto);
    }

    @Override
    @Transactional
    public AdminRuntimeOperationRecordDto createRecord(
            String adminEmail, AdminRuntimeOperationRecordRequestDto request) {
        validateRecord(request);
        RuntimeOperationRecordEntity entity = new RuntimeOperationRecordEntity();
        entity.setRecordType(request.getRecordType());
        entity.setStatus(request.getStatus());
        entity.setOperationKey(request.getOperationKey().trim());
        entity.setTitle(request.getTitle().trim());
        entity.setSummary(request.getSummary().trim());
        entity.setStartedAt(request.getStartedAt());
        entity.setCompletedAt(request.getCompletedAt());
        entity.setNextRunAt(request.getNextRunAt());
        entity.setRetryable(request.getRetryable());
        entity.setRetryCount(0);
        entity.setCreatedBy(adminEmail);
        entity.setCreatedAt(LocalDateTime.now());
        return toDto(recordRepository.save(entity));
    }

    @Override
    @Transactional
    public AdminRuntimeOperationRecordDto retryRecord(String adminEmail, Long id) {
        RuntimeOperationRecordEntity source = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Runtime operation record was not found."));
        if (source.getRecordType() != RuntimeOperationRecordType.SCHEDULED_JOB
                || !Boolean.TRUE.equals(source.getRetryable())
                || (source.getStatus() != RuntimeOperationStatus.FAILED
                && source.getStatus() != RuntimeOperationStatus.DEAD_LETTER)) {
            throw new IllegalArgumentException("Only retryable failed or dead-letter scheduled jobs can be queued.");
        }
        RuntimeOperationRecordEntity retry = new RuntimeOperationRecordEntity();
        retry.setRecordType(RuntimeOperationRecordType.SCHEDULED_JOB);
        retry.setStatus(RuntimeOperationStatus.SCHEDULED);
        retry.setOperationKey(source.getOperationKey());
        retry.setTitle("Retry: " + source.getTitle());
        retry.setSummary("Manual retry queued from record #" + source.getId() + ".");
        retry.setNextRunAt(LocalDateTime.now());
        retry.setRetryable(true);
        retry.setRetryCount(source.getRetryCount() + 1);
        retry.setParentRecordId(source.getId());
        retry.setCreatedBy(adminEmail);
        retry.setCreatedAt(LocalDateTime.now());
        return toDto(recordRepository.save(retry));
    }

    @Override
    public boolean maintenanceEnabled() {
        return Boolean.TRUE.equals(currentPolicy().getMaintenanceEnabled());
    }

    @Override
    public String maintenanceMessage() {
        return currentPolicy().getMaintenanceMessage();
    }

    @Override
    @Transactional(readOnly = true)
    public RuntimeClientConfigDto getClientConfig(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."));
        SubscriptionPlan plan = subscriptionRepository.findByUser(user)
                .map(SubscriptionEntity::getPlanType)
                .orElse(SubscriptionPlan.FREE);
        AdminRuntimeOperationsPolicyDto policy = currentPolicy();
        boolean eligible = Boolean.TRUE.equals(policy.getRolloutEnabled());
        if (policy.getRolloutPlan() != null && policy.getRolloutPlan() != plan) eligible = false;
        if (policy.getRolloutRegion() != null && policy.getRolloutRegion() != user.getMarketRegion()) eligible = false;
        if (policy.getRolloutSegment() == RuntimeRolloutSegment.NEW_USERS
                && (user.getCreatedAt() == null
                || user.getCreatedAt().isBefore(Instant.now().minus(30, ChronoUnit.DAYS)))) {
            eligible = false;
        }
        int bucket = Math.floorMod(email.toLowerCase(Locale.ROOT).hashCode(), 100);
        eligible = eligible && bucket < policy.getRolloutPercentage();
        return new RuntimeClientConfigDto(
                policy.getReleaseVersion(), policy.getMinimumIosVersion(),
                policy.getMinimumAndroidVersion(), policy.getMaintenanceEnabled(),
                policy.getMaintenanceMessage(), policy.getRolloutFeature(), eligible);
    }
    private AdminRuntimeOperationsPolicyDto currentPolicy() {
        AdminRuntimeOperationsPolicyDto value = cachedPolicy;
        return value == null ? getPolicy() : value;
    }

    private RuntimeOperationsPolicyEntity requiredPolicy() {
        return policyRepository.findById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("Runtime operations policy is not initialized."));
    }

    private void requireVersion(RuntimeOperationsPolicyEntity policy, Long version) {
        if (version == null || !version.equals(policy.getVersion())) {
            throw new IllegalArgumentException("Runtime operations policy changed. Refresh before saving.");
        }
    }

    private void validateRecord(AdminRuntimeOperationRecordRequestDto request) {
        if (request.getRecordType() == RuntimeOperationRecordType.SCHEDULED_JOB) {
            throw new IllegalArgumentException("Scheduled job registry records are backend-owned.");
        }
        if (request.getRecordType() == RuntimeOperationRecordType.INCIDENT
                && request.getStatus() != RuntimeOperationStatus.OPEN
                && request.getStatus() != RuntimeOperationStatus.MONITORING
                && request.getStatus() != RuntimeOperationStatus.RESOLVED) {
            throw new IllegalArgumentException("Incident status must be OPEN, MONITORING, or RESOLVED.");
        }
        if (request.getRecordType() != RuntimeOperationRecordType.INCIDENT
                && request.getStatus() != RuntimeOperationStatus.RUNNING
                && request.getStatus() != RuntimeOperationStatus.SUCCEEDED
                && request.getStatus() != RuntimeOperationStatus.FAILED) {
            throw new IllegalArgumentException("Backup and restore drill status must be RUNNING, SUCCEEDED, or FAILED.");
        }
    }

    private void applyRequest(RuntimeOperationsPolicyEntity policy, AdminRuntimeOperationsPolicyUpdateRequestDto request) {
        policy.setMaintenanceEnabled(request.getMaintenanceEnabled());
        policy.setMaintenanceMessage(request.getMaintenanceMessage().trim());
        policy.setReleaseVersion(request.getReleaseVersion().trim());
        policy.setDeploymentEnvironment(request.getDeploymentEnvironment().trim());
        policy.setMinimumIosVersion(request.getMinimumIosVersion().trim());
        policy.setMinimumAndroidVersion(request.getMinimumAndroidVersion().trim());
        policy.setRolloutFeature(request.getRolloutFeature());
        policy.setRolloutEnabled(request.getRolloutEnabled());
        policy.setRolloutPlan(request.getRolloutPlan());
        policy.setRolloutRegion(request.getRolloutRegion());
        policy.setRolloutSegment(request.getRolloutSegment());
        policy.setRolloutPercentage(request.getRolloutPercentage());
        policy.setApiLatencyWarningMs(request.getApiLatencyWarningMs());
        policy.setApiErrorRateThreshold(request.getApiErrorRateThreshold());
        policy.setEscalationTarget(trimToNull(request.getEscalationTarget()));
    }

    private PolicySnapshot snapshot(RuntimeOperationsPolicyEntity policy) {
        return new PolicySnapshot(
                policy.getMaintenanceEnabled(), policy.getMaintenanceMessage(),
                policy.getReleaseVersion(), policy.getDeploymentEnvironment(),
                policy.getMinimumIosVersion(), policy.getMinimumAndroidVersion(),
                policy.getRolloutFeature(), policy.getRolloutEnabled(), policy.getRolloutPlan(),
                policy.getRolloutRegion(), policy.getRolloutSegment(), policy.getRolloutPercentage(),
                policy.getApiLatencyWarningMs(), policy.getApiErrorRateThreshold(),
                policy.getEscalationTarget());
    }

    private void applySnapshot(RuntimeOperationsPolicyEntity policy, PolicySnapshot snapshot) {
        policy.setMaintenanceEnabled(snapshot.maintenanceEnabled());
        policy.setMaintenanceMessage(snapshot.maintenanceMessage());
        policy.setReleaseVersion(snapshot.releaseVersion());
        policy.setDeploymentEnvironment(snapshot.deploymentEnvironment());
        policy.setMinimumIosVersion(snapshot.minimumIosVersion());
        policy.setMinimumAndroidVersion(snapshot.minimumAndroidVersion());
        policy.setRolloutFeature(snapshot.rolloutFeature());
        policy.setRolloutEnabled(snapshot.rolloutEnabled());
        policy.setRolloutPlan(snapshot.rolloutPlan());
        policy.setRolloutRegion(snapshot.rolloutRegion());
        policy.setRolloutSegment(snapshot.rolloutSegment());
        policy.setRolloutPercentage(snapshot.rolloutPercentage());
        policy.setApiLatencyWarningMs(snapshot.apiLatencyWarningMs());
        policy.setApiErrorRateThreshold(snapshot.apiErrorRateThreshold());
        policy.setEscalationTarget(snapshot.escalationTarget());
    }

    private String writeSnapshot(PolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Runtime policy snapshot could not be serialized.", ex);
        }
    }

    private PolicySnapshot readSnapshot(String value) {
        try {
            return objectMapper.readValue(value, PolicySnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Runtime policy snapshot could not be restored.", ex);
        }
    }

    private AdminRuntimeOperationsPolicyDto toDto(RuntimeOperationsPolicyEntity policy) {
        AdminRuntimeOperationsPolicyDto dto = new AdminRuntimeOperationsPolicyDto();
        dto.setVersion(policy.getVersion());
        dto.setMaintenanceEnabled(policy.getMaintenanceEnabled());
        dto.setMaintenanceMessage(policy.getMaintenanceMessage());
        dto.setReleaseVersion(policy.getReleaseVersion());
        dto.setDeploymentEnvironment(policy.getDeploymentEnvironment());
        dto.setMinimumIosVersion(policy.getMinimumIosVersion());
        dto.setMinimumAndroidVersion(policy.getMinimumAndroidVersion());
        dto.setRolloutFeature(policy.getRolloutFeature());
        dto.setRolloutEnabled(policy.getRolloutEnabled());
        dto.setRolloutPlan(policy.getRolloutPlan());
        dto.setRolloutRegion(policy.getRolloutRegion());
        dto.setRolloutSegment(policy.getRolloutSegment());
        dto.setRolloutPercentage(policy.getRolloutPercentage());
        dto.setApiLatencyWarningMs(policy.getApiLatencyWarningMs());
        dto.setApiErrorRateThreshold(policy.getApiErrorRateThreshold());
        dto.setEscalationTarget(policy.getEscalationTarget());
        dto.setRollbackAvailable(policy.getPreviousSnapshot() != null && !policy.getPreviousSnapshot().isBlank());
        dto.setUpdatedBy(policy.getUpdatedBy());
        dto.setUpdatedAt(policy.getUpdatedAt());
        return dto;
    }

    private AdminRuntimeOperationRecordDto toDto(RuntimeOperationRecordEntity entity) {
        return new AdminRuntimeOperationRecordDto(
                entity.getId(), entity.getRecordType(), entity.getStatus(), entity.getOperationKey(),
                entity.getTitle(), entity.getSummary(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getNextRunAt(), entity.getRetryable(), entity.getRetryCount(),
                entity.getParentRecordId(), entity.getCreatedBy(), entity.getCreatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PolicySnapshot(
            Boolean maintenanceEnabled,
            String maintenanceMessage,
            String releaseVersion,
            String deploymentEnvironment,
            String minimumIosVersion,
            String minimumAndroidVersion,
            SubscriptionFeature rolloutFeature,
            Boolean rolloutEnabled,
            SubscriptionPlan rolloutPlan,
            MarketRegion rolloutRegion,
            RuntimeRolloutSegment rolloutSegment,
            Integer rolloutPercentage,
            Long apiLatencyWarningMs,
            Double apiErrorRateThreshold,
            String escalationTarget
    ) {
    }
}

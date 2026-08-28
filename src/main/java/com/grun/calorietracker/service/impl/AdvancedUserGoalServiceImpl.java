package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.GoalValidationException;

import com.grun.calorietracker.dto.AdvancedGoalPreviewDto;
import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.ProductAnalyticsEventRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.entity.GoalTargetAcknowledgementEntity;
import com.grun.calorietracker.entity.AdvancedGoalPreviewEntity;
import com.grun.calorietracker.entity.AdvancedGoalSaveRequestEntity;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.mapper.UserGoalMapper;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.GoalTargetAcknowledgementRepository;
import com.grun.calorietracker.repository.AdvancedGoalPreviewRepository;
import com.grun.calorietracker.repository.AdvancedGoalSaveRequestRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdvancedUserGoalService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.ProductAnalyticsService;
import com.grun.calorietracker.service.support.AdvancedMacroTargetPolicy;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedUserGoalServiceImpl implements AdvancedUserGoalService {
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private final GoalRepository goalRepository;
    private final GoalTargetAcknowledgementRepository acknowledgementRepository;
    private final AdvancedGoalPreviewRepository previewRepository;
    private final AdvancedGoalSaveRequestRepository saveRequestRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserGoalService userGoalService;
    private final SubscriptionService subscriptionService;
    private final AdvancedMacroTargetPolicy policy;
    private final UserAnalyticsCacheRevisionService cacheRevisionService;
    private final ProductAnalyticsService productAnalyticsService;

    @Override
    public AdvancedGoalPreviewDto preview(AdvancedGoalRequestDto request, String email) {
        UserEntity user = assertEligible(email);
        AdvancedGoalPreviewDto result = policy.preview(request, userGoalService.calculateGoal(request.automaticRequest(), email));
        Long goalVersion = goalRepository.findByUser(user).map(UserGoalEntity::getVersion).orElse(null);
        String profileVersion = profileVersion(user);
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        AdvancedGoalPreviewEntity preview = new AdvancedGoalPreviewEntity();
        preview.setToken(token);
        preview.setUser(user);
        preview.setRequestHash(requestHash(request));
        preview.setProfileVersion(profileVersion);
        preview.setGoalVersion(goalVersion);
        preview.setCreatedAt(now);
        preview.setExpiresAt(now.plus(PREVIEW_TTL));
        previewRepository.save(preview);
        result.setPreviewToken(token);
        result.setProfileVersion(profileVersion);
        result.setGoalVersion(goalVersion);
        if (request.getMode() == GoalCalculationMode.CONTROLLED) {
            recordEvent(email, ProductAnalyticsEventType.CONTROLLED_MACRO_PREVIEWED, request.getMode());
        } else if (result.isRequiresAcknowledgement()) {
            recordEvent(email, ProductAnalyticsEventType.MANUAL_MACRO_WARNING_SHOWN, request.getMode());
        }
        return result;
    }

    @Override
    @Transactional
    public UserGoalDto save(AdvancedGoalRequestDto request, String email, String idempotencyKey) {
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        UserEntity user = assertEligibleForUpdate(email);
        String saveHash = sha256(requestHash(request) + "|" + Objects.toString(request.getPreviewToken(), "")
                + "|" + request.isWarningsAcknowledged());
        var existingSave = saveRequestRepository.findByUserIdAndIdempotencyKey(user.getId(), key);
        if (existingSave.isPresent()) {
            if (!existingSave.get().getRequestHash().equals(saveHash)) {
                throw new RequestConflictException("Idempotency-Key was already used with a different advanced goal payload.");
            }
            return UserGoalMapper.toDto(existingSave.get().getGoal());
        }
        validatePreview(request, user);
        AdvancedGoalPreviewDto preview = policy.preview(request, userGoalService.calculateGoal(request.automaticRequest(), email));
        if (!preview.isCanSave()) throw new GoalValidationException("GOAL_POLICY_CONSTRAINT", "Advanced goal violates required policy constraints.");
        if (preview.isRequiresAcknowledgement() && !request.isWarningsAcknowledged()) {
            throw new GoalValidationException("GOAL_ACKNOWLEDGEMENT_REQUIRED", "Warnings must be acknowledged before saving.");
        }
        LocalDateTime now = LocalDateTime.now();
        goalRepository.findByUser(user).ifPresent(active -> {
            if (request.getExpectedGoalVersion() != null && !request.getExpectedGoalVersion().equals(active.getVersion())) {
                throw new IllegalStateException("Goal was updated by another request. Preview again.");
            }
            active.setEffectiveUntil(now);
            // Flush the closure before the IDENTITY insert checks the active-goal unique index.
            goalRepository.saveAndFlush(active);
        });
        UserGoalEntity entity = new UserGoalEntity();
        entity.setUser(user);
        entity.setTargetWeight(request.getTargetWeight());
        entity.setDailyCalorieGoal(preview.getCalories());
        entity.setDailyProteinGoal(preview.getProteinGrams());
        entity.setDailyCarbGoal(preview.getCarbGrams());
        entity.setDailyFatGoal(preview.getFatGrams());
        entity.setWeeklyWeightChangeTargetKg(weeklyRate(preview));
        entity.setGoalType(request.getGoalType());
        entity.setActivityLevel(request.getActivityLevel());
        entity.setCalculationMode(request.getMode());
        entity.setControlledStrategy(request.getStrategy());
        entity.setLockedMacros(lockedMacros(request));
        entity.setMacroCalculatedCalories(preview.getCalories());
        entity.setAutomaticReferenceCalories(preview.getAutomaticReference().getCalculatedCalorieNeed());
        entity.setAutomaticReferenceProtein((double) preview.getAutomaticReference().getRecommendedProteinGrams());
        entity.setAutomaticReferenceCarbs((double) preview.getAutomaticReference().getRecommendedCarbGrams());
        entity.setAutomaticReferenceFat((double) preview.getAutomaticReference().getRecommendedFatGrams());
        entity.setCreatedAt(now);
        entity.setEffectiveFrom(now);
        ZoneId zone = zone(user);
        entity.setEffectiveLocalDate(LocalDate.now(zone));
        entity.setEffectiveTimeZone(zone.getId());
        UserGoalEntity saved = goalRepository.save(entity);
        if (preview.isRequiresAcknowledgement()) {
            GoalTargetAcknowledgementEntity acknowledgement = new GoalTargetAcknowledgementEntity();
            acknowledgement.setGoal(saved);
            acknowledgement.setUser(user);
            acknowledgement.setWarningCodes(String.join(",", preview.getWarnings()));
            acknowledgement.setPolicyVersion(AdvancedMacroTargetPolicy.VERSION);
            acknowledgement.setLocale(user.getPreferredLanguage() == null ? "EN" : user.getPreferredLanguage().name());
            acknowledgement.setAcknowledgedAt(now);
            acknowledgementRepository.save(acknowledgement);
        }
        AdvancedGoalSaveRequestEntity saveRequest = new AdvancedGoalSaveRequestEntity();
        saveRequest.setUser(user);
        saveRequest.setIdempotencyKey(key);
        saveRequest.setRequestHash(saveHash);
        saveRequest.setGoal(saved);
        saveRequest.setCreatedAt(now);
        saveRequestRepository.save(saveRequest);
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.GOAL);
        if (request.getMode() == GoalCalculationMode.MANUAL && preview.isRequiresAcknowledgement()) {
            recordEvent(email, ProductAnalyticsEventType.MANUAL_MACRO_WARNING_ACCEPTED, request.getMode());
        }
        recordEvent(email, ProductAnalyticsEventType.MACRO_TARGET_SAVED, request.getMode());
        return UserGoalMapper.toDto(saved);
    }

    @Override
    public UserGoalDto restoreAutomatic(AdvancedGoalRequestDto request, String email) {
        UserGoalDto restored = userGoalService.saveUserGoal(request.automaticRequest(), email);
        recordEvent(email, ProductAnalyticsEventType.AUTOMATIC_TARGET_RESTORED, GoalCalculationMode.AUTO);
        return restored;
    }

    private UserEntity assertEligible(String email) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_MACRO_TARGETS);
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        if (user.getAge() == null || user.getAge() < 18) {
            throw new GoalValidationException("GOAL_ADULT_ONLY", "Advanced macro targets are available to adults only.");
        }
        return user;
    }

    private UserEntity assertEligibleForUpdate(String email) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_MACRO_TARGETS);
        UserEntity user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        if (user.getAge() == null || user.getAge() < 18) {
            throw new GoalValidationException("GOAL_ADULT_ONLY", "Advanced macro targets are available to adults only.");
        }
        return user;
    }

    private void validatePreview(AdvancedGoalRequestDto request, UserEntity user) {
        if (request.getPreviewToken() == null || request.getPreviewToken().isBlank()) {
            throw new GoalValidationException("GOAL_PREVIEW_REQUIRED", "A valid preview token is required.");
        }
        AdvancedGoalPreviewEntity preview = previewRepository.findById(request.getPreviewToken())
                .orElseThrow(() -> new RequestConflictException("Preview is missing or expired. Preview the goal again."));
        LocalDateTime now = LocalDateTime.now();
        if (!preview.getUser().getId().equals(user.getId()) || preview.getConsumedAt() != null
                || !preview.getExpiresAt().isAfter(now)) {
            throw new RequestConflictException("Preview is no longer valid. Preview the goal again.");
        }
        Long activeVersion = goalRepository.findByUser(user).map(UserGoalEntity::getVersion).orElse(null);
        String currentProfileVersion = profileVersion(user);
        if (!preview.getRequestHash().equals(requestHash(request))
                || !Objects.equals(preview.getGoalVersion(), activeVersion)
                || !preview.getProfileVersion().equals(currentProfileVersion)
                || !Objects.equals(request.getExpectedGoalVersion(), preview.getGoalVersion())
                || !Objects.equals(request.getExpectedProfileVersion(), preview.getProfileVersion())) {
            throw new RequestConflictException("Profile, active goal, or preview inputs changed. Preview the goal again.");
        }
        preview.setConsumedAt(now);
        previewRepository.save(preview);
    }

    private static String requestHash(AdvancedGoalRequestDto request) {
        return sha256(String.join("|",
                Objects.toString(request.getTargetWeight(), ""),
                Objects.toString(request.getWeeklyWeightChangeTargetKg(), ""),
                Objects.toString(request.getGoalType(), ""),
                Objects.toString(request.getActivityLevel(), ""),
                Objects.toString(request.getMode(), ""),
                Objects.toString(request.getStrategy(), ""),
                Objects.toString(request.getProteinGrams(), ""),
                Objects.toString(request.getCarbGrams(), ""),
                Objects.toString(request.getFatGrams(), "")));
    }

    private static String profileVersion(UserEntity user) {
        return sha256(String.join("|",
                Objects.toString(user.getAge(), ""), Objects.toString(user.getBirthDate(), ""),
                Objects.toString(user.getGender(), ""), Objects.toString(user.getHeight(), ""),
                Objects.toString(user.getWeight(), ""), Objects.toString(user.getBodyFatPercentage(), ""),
                Objects.toString(user.getTimeZone(), "")));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static String lockedMacros(AdvancedGoalRequestDto request) {
        StringBuilder value = new StringBuilder();
        if (request.getProteinGrams() != null) value.append("PROTEIN,");
        if (request.getCarbGrams() != null) value.append("CARBS,");
        if (request.getFatGrams() != null) value.append("FAT,");
        return value.isEmpty() ? null : value.substring(0, value.length() - 1);
    }

    private static ZoneId zone(UserEntity user) {
        try { return ZoneId.of(user.getTimeZone()); }
        catch (RuntimeException ignored) { return ZoneId.of("UTC"); }
    }

    private static double weeklyRate(AdvancedGoalPreviewDto preview) {
        Integer maintenance = preview.getAutomaticReference().getMaintenanceCalories();
        if (maintenance == null) return 0.0;
        return Math.round(((preview.getCalories() - maintenance) * 7.0 / 7700.0) * 1000.0) / 1000.0;
    }

    private void recordEvent(String email, ProductAnalyticsEventType type, GoalCalculationMode mode) {
        try {
            ProductAnalyticsEventRequestDto event = new ProductAnalyticsEventRequestDto();
            event.setEventType(type);
            event.setSurface("edit_goals");
            event.setMetadata(java.util.Map.of("variant", mode.name()));
            productAnalyticsService.recordEvent(email, event);
        } catch (RuntimeException ex) {
            log.warn("Advanced goal analytics could not be recorded eventType={}", type, ex);
        }
    }
}

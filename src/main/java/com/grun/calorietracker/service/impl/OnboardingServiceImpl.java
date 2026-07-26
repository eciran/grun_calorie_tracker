package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.MyProfileDto;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.OnboardingGoalStepDto;
import com.grun.calorietracker.dto.OnboardingFitnessPreferenceStepDto;
import com.grun.calorietracker.dto.OnboardingNutritionStepDto;
import com.grun.calorietracker.dto.OnboardingPreferencesStepDto;
import com.grun.calorietracker.dto.OnboardingPreviewResponseDto;
import com.grun.calorietracker.dto.OnboardingProfileStepDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.dto.OnboardingStepUpdateRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.OnboardingDraftEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFitnessPreferenceEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.DietaryPreference;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.OnboardingDraftRepository;
import com.grun.calorietracker.repository.UserFitnessPreferenceRepository;
import com.grun.calorietracker.repository.UserNutritionPreferenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.OnboardingService;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.support.UserAgeSupport;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final UserService userService;
    private final UserGoalService userGoalService;
    private final UserRepository userRepository;
    private final OnboardingDraftRepository onboardingDraftRepository;
    private final UserNutritionPreferenceRepository userNutritionPreferenceRepository;
    private final UserFitnessPreferenceRepository userFitnessPreferenceRepository;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final UserAgeSupport userAgeSupport;

    @Override
    @Transactional(readOnly = true)
    public OnboardingStateDto getState(String email) {
        UserEntity user = findUser(email);
        OnboardingDraftEntity draft = onboardingDraftRepository.findByUser(user)
                .orElseGet(() -> buildDraft(user, email));
        return toState(draft);
    }

    @Override
    @Transactional
    public OnboardingStateDto updateStep(
            OnboardingStep step,
            OnboardingStepUpdateRequestDto request,
            String email
    ) {
        if (step == null || step == OnboardingStep.REVIEW || step == OnboardingStep.COMPLETE) {
            throw new IllegalArgumentException("Only PROFILE, PREFERENCES, GOAL, NUTRITION, and FITNESS_PREFERENCE can be updated");
        }
        validateMatchingPayload(step, request);

        UserEntity user = findUserForUpdate(email);
        OnboardingDraftEntity draft = getOrCreateDraft(user, email);
        if (draft.getStatus() == OnboardingStatus.COMPLETED) {
            throw new RequestConflictException("Completed onboarding cannot be edited");
        }

        switch (step) {
            case PROFILE -> applyProfile(draft, request.getProfile());
            case PREFERENCES -> applyPreferences(draft, request.getPreferences());
            case GOAL -> applyGoal(draft, request.getGoal());
            case NUTRITION -> applyNutrition(draft, request.getNutrition());
            case FITNESS_PREFERENCE -> applyFitnessPreference(draft, request.getFitnessPreference());
            default -> throw new IllegalArgumentException("Unsupported onboarding step");
        }
        return toState(onboardingDraftRepository.save(draft));
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingPreviewResponseDto preview(String email) {
        UserEntity user = findUser(email);
        OnboardingDraftEntity draft = onboardingDraftRepository.findByUser(user)
                .orElseGet(() -> buildDraft(user, email));
        requireCompletable(draft);

        return new OnboardingPreviewResponseDto(calculateDraft(draft), toState(draft));
    }

    @Override
    @Transactional
    public OnboardingCompleteResponseDto completeOnboarding(String email) {
        UserEntity user = findUserForUpdate(email);
        return completeDraft(getOrCreateDraft(user, email), email);
    }

    @Override
    @Transactional
    public OnboardingCompleteResponseDto completeOnboarding(OnboardingCompleteRequestDto request, String email) {
        if (request == null) {
            return completeOnboarding(email);
        }
        UserEntity user = findUserForUpdate(email);
        OnboardingDraftEntity draft = getOrCreateDraft(user, email);
        if (draft.getStatus() == OnboardingStatus.COMPLETED) {
            return currentCompletedResponse(email, draft);
        }

        applyProfile(draft, new OnboardingProfileStepDto(
                request.getName(),
                request.getAge(),
                request.getBirthDate(),
                request.getGender(),
                request.getHeight(),
                request.getWeight(),
                request.getBodyFat()
        ));
        applyPreferences(draft, new OnboardingPreferencesStepDto(
                request.getMarketRegion(),
                request.getCountryCode(),
                request.getPreferredLanguage(),
                request.getTimeZone() == null ? draft.getTimeZone() : request.getTimeZone(),
                request.getUnitPreference() == null ? draft.getUnitPreference() : request.getUnitPreference()
        ));
        applyGoal(draft, new OnboardingGoalStepDto(
                request.getTargetWeight(),
                request.getWeeklyWeightChangeTargetKg(),
                request.getGoalType(),
                request.getActivityLevel()
        ));
        applyLegacyNutritionDefaults(draft);
        onboardingDraftRepository.save(draft);
        return completeDraft(draft, email);
    }

    private OnboardingCompleteResponseDto completeDraft(OnboardingDraftEntity draft, String email) {
        if (draft.getStatus() == OnboardingStatus.COMPLETED) {
            return currentCompletedResponse(email, draft);
        }
        requireCompletable(draft);
        GoalCalculationResponse calculation = calculateDraft(draft);

        userService.updateCurrentUser(toProfileDto(draft), email);
        persistNutritionPreference(draft);
        persistFitnessPreference(draft);
        UserGoalDto goal = userGoalService.saveUserGoal(
                toGoalStep(draft).toGoalCalculationRequestDto(),
                email
        );

        draft.setStatus(OnboardingStatus.COMPLETED);
        draft.setCompletedAt(LocalDateTime.now());
        onboardingDraftRepository.save(draft);
        return completedResponse(userService.getMyProfile(email), goal, calculation);
    }

    private OnboardingCompleteResponseDto currentCompletedResponse(
            String email,
            OnboardingDraftEntity draft
    ) {
        MyProfileDto profile = userService.getMyProfile(email);
        UserGoalDto goal = userGoalService.getCurrentUserGoal(email);
        if (goal == null) {
            throw new IllegalStateException("Completed onboarding has no active goal");
        }
        GoalCalculationResponse calculation = canComplete(draft)
                ? calculateDraft(draft)
                : basicCalculation(goal);
        return completedResponse(profile, goal, calculation);
    }

    private OnboardingCompleteResponseDto completedResponse(
            MyProfileDto profile,
            UserGoalDto goal,
            GoalCalculationResponse calculation
    ) {
        return new OnboardingCompleteResponseDto(profile, goal, calculation, true);
    }

    private GoalCalculationResponse calculateDraft(OnboardingDraftEntity draft) {
        return userGoalService.calculateGoalPreview(
                toGoalStep(draft).toGoalCalculationRequestDto(),
                toProfileDto(draft)
        );
    }

    private GoalCalculationResponse basicCalculation(UserGoalDto goal) {
        return new GoalCalculationResponse(
                goal.getDailyCalorieGoal(),
                goal.getDailyProteinGoal().intValue(),
                goal.getDailyFatGoal().intValue(),
                goal.getDailyCarbGoal().intValue()
        );
    }

    private OnboardingDraftEntity getOrCreateDraft(UserEntity user, String email) {
        OnboardingDraftEntity existing = onboardingDraftRepository.findByUserForUpdate(user)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        return onboardingDraftRepository.save(buildDraft(user, email));
    }

    private OnboardingDraftEntity buildDraft(UserEntity user, String email) {
        OnboardingDraftEntity draft = new OnboardingDraftEntity();
        draft.setUser(user);
        draft.setName(user.getName());
        draft.setBirthDate(user.getBirthDate());
        draft.setAge(userAgeSupport.resolveAge(user, userTimeZoneSupport.zoneId(user)));
        draft.setGender(user.getGender());
        draft.setHeight(user.getHeight());
        draft.setWeight(user.getWeight());
        draft.setBodyFatPercentage(user.getBodyFatPercentage());
        draft.setMarketRegion(user.getMarketRegion());
        draft.setCountryCode(resolveCountryCode(user.getCountryCode(), user.getMarketRegion()));
        draft.setPreferredLanguage(user.getPreferredLanguage());
        draft.setTimeZone(user.getTimeZone());
        draft.setUnitPreference(user.getUnitPreference());

        UserGoalDto goal = userGoalService.getCurrentUserGoal(email);
        if (goal != null) {
            draft.setTargetWeight(goal.getTargetWeight());
            draft.setWeeklyWeightChangeTargetKg(goal.getWeeklyWeightChangeTargetKg());
            draft.setGoalType(goal.getGoalType());
            draft.setActivityLevel(goal.getActivityLevel());
        }
        hydratePersistedPreferences(draft);
        if (goal != null && !nutritionComplete(draft)) {
            applyLegacyNutritionDefaults(draft);
        }
        if (canComplete(draft) && goal != null) {
            draft.setStatus(OnboardingStatus.COMPLETED);
            draft.setCompletedAt(LocalDateTime.now());
        }
        return draft;
    }

    private UserEntity findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private UserEntity findUserForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void validateMatchingPayload(OnboardingStep step, OnboardingStepUpdateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Onboarding step payload is required");
        }
        int payloadCount = (request.getProfile() == null ? 0 : 1)
                + (request.getPreferences() == null ? 0 : 1)
                + (request.getGoal() == null ? 0 : 1)
                + (request.getNutrition() == null ? 0 : 1)
                + (request.getFitnessPreference() == null ? 0 : 1);
        boolean matching = switch (step) {
            case PROFILE -> request.getProfile() != null;
            case PREFERENCES -> request.getPreferences() != null;
            case GOAL -> request.getGoal() != null;
            case NUTRITION -> request.getNutrition() != null;
            case FITNESS_PREFERENCE -> request.getFitnessPreference() != null;
            default -> false;
        };
        if (payloadCount != 1 || !matching) {
            throw new IllegalArgumentException("Request must contain only the payload matching the onboarding step");
        }
    }

    private void applyProfile(OnboardingDraftEntity draft, OnboardingProfileStepDto profile) {
        if (profile.getName() != null) {
            draft.setName(profile.getName().trim());
        }
        if (profile.getBirthDate() != null || profile.getAge() != null) {
            draft.setBirthDate(profile.getBirthDate());
            draft.setAge(userAgeSupport.resolveAge(
                    profile.getBirthDate(),
                    profile.getAge(),
                    userTimeZoneSupport.zoneId(draft.getTimeZone())
            ));
        }
        if (profile.getGender() != null) {
            draft.setGender(profile.getGender().trim().toUpperCase());
        }
        if (profile.getHeight() != null) {
            draft.setHeight(profile.getHeight());
        }
        if (profile.getWeight() != null) {
            draft.setWeight(profile.getWeight());
        }
        if (profile.getBodyFat() != null) {
            draft.setBodyFatPercentage(profile.getBodyFat());
        }
    }

    private void applyPreferences(OnboardingDraftEntity draft, OnboardingPreferencesStepDto preferences) {
        draft.setMarketRegion(preferences.getMarketRegion());
        draft.setCountryCode(resolveCountryCode(preferences.getCountryCode(), preferences.getMarketRegion()));
        draft.setPreferredLanguage(preferences.getPreferredLanguage());
        draft.setTimeZone(userTimeZoneSupport.normalize(preferences.getTimeZone()));
        draft.setUnitPreference(preferences.getUnitPreference());
    }

    private void applyGoal(OnboardingDraftEntity draft, OnboardingGoalStepDto goal) {
        if (goal.getTargetWeight() != null) {
            draft.setTargetWeight(goal.getTargetWeight());
        }
        if (goal.getWeeklyWeightChangeTargetKg() != null) {
            draft.setWeeklyWeightChangeTargetKg(goal.getWeeklyWeightChangeTargetKg());
        }
        if (goal.getGoalType() != null) {
            draft.setGoalType(goal.getGoalType());
        }
        if (goal.getActivityLevel() != null) {
            draft.setActivityLevel(goal.getActivityLevel());
        }
    }
    private void applyNutrition(OnboardingDraftEntity draft, OnboardingNutritionStepDto nutrition) {
        if (nutrition.getPrimaryDietaryPreference() != null) {
            draft.setPrimaryDietaryPreference(nutrition.getPrimaryDietaryPreference());
        }
        if (nutrition.getAllergens() != null) {
            draft.setAllergens(new LinkedHashSet<>(nutrition.getAllergens()));
        }
        if (nutrition.getAllergenSelectionConfirmed() != null) {
            draft.setAllergenSelectionConfirmed(nutrition.isAllergenSelectionConfirmed());
        }
    }

    private void applyFitnessPreference(
            OnboardingDraftEntity draft,
            OnboardingFitnessPreferenceStepDto fitnessPreference
    ) {
        if (!fitnessPreference.isSelectionConfirmed()) {
            throw new IllegalArgumentException(
                    "Fitness preference must be confirmed or explicitly skipped"
            );
        }
        draft.setWeeklyWorkoutFrequency(fitnessPreference.getWeeklyWorkoutFrequency());
        draft.setFitnessPreferenceCompleted(true);
    }

    private void hydratePersistedPreferences(OnboardingDraftEntity draft) {
        userNutritionPreferenceRepository.findByUser(draft.getUser()).ifPresent(preference -> {
            preference.getDietaryPreferences().stream()
                    .map(this::parseDietaryPreference)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .ifPresent(draft::setPrimaryDietaryPreference);
            draft.setAllergens(new LinkedHashSet<>(preference.getAllergens()));
            draft.setAllergenSelectionConfirmed(true);
        });
        userFitnessPreferenceRepository.findByUser(draft.getUser()).ifPresent(preference -> {
            draft.setWeeklyWorkoutFrequency(preference.getWeeklyWorkoutFrequency());
            draft.setFitnessPreferenceCompleted(true);
        });
    }

    private Optional<DietaryPreference> parseDietaryPreference(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String canonical = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        canonical = switch (canonical) {
            case "OMNIVORE", "STANDARD" -> "BALANCED";
            case "NONE" -> "NO_PREFERENCE";
            default -> canonical;
        };
        try {
            return Optional.of(DietaryPreference.valueOf(canonical));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void applyLegacyNutritionDefaults(OnboardingDraftEntity draft) {
        if (draft.getPrimaryDietaryPreference() == null) {
            draft.setPrimaryDietaryPreference(DietaryPreference.NO_PREFERENCE);
        }
        if (draft.getAllergens() == null) {
            draft.setAllergens(new LinkedHashSet<>());
        }
        draft.setAllergenSelectionConfirmed(true);
    }

    private void persistNutritionPreference(OnboardingDraftEntity draft) {
        UserNutritionPreferenceEntity preference = userNutritionPreferenceRepository.findByUser(draft.getUser())
                .orElseGet(() -> {
                    UserNutritionPreferenceEntity created = new UserNutritionPreferenceEntity();
                    created.setUser(draft.getUser());
                    return created;
                });
        preference.setDietaryPreferences(new ArrayList<>(List.of(
                draft.getPrimaryDietaryPreference().name()
        )));
        preference.setAllergens(new LinkedHashSet<>(draft.getAllergens()));
        userNutritionPreferenceRepository.save(preference);
    }

    private void persistFitnessPreference(OnboardingDraftEntity draft) {
        if (!draft.isFitnessPreferenceCompleted()) {
            return;
        }
        Optional<UserFitnessPreferenceEntity> existing =
                userFitnessPreferenceRepository.findByUser(draft.getUser());
        if (draft.getWeeklyWorkoutFrequency() == null) {
            existing.ifPresent(userFitnessPreferenceRepository::delete);
            return;
        }
        UserFitnessPreferenceEntity preference = existing.orElseGet(() -> {
            UserFitnessPreferenceEntity created = new UserFitnessPreferenceEntity();
            created.setUser(draft.getUser());
            return created;
        });
        preference.setWeeklyWorkoutFrequency(draft.getWeeklyWorkoutFrequency());
        userFitnessPreferenceRepository.save(preference);
    }
    private void requireCompletable(OnboardingDraftEntity draft) {
        if (!canComplete(draft)) {
            throw new IllegalArgumentException("All onboarding steps must be completed first");
        }
    }

    private boolean canComplete(OnboardingDraftEntity draft) {
        return profileComplete(draft) && preferencesComplete(draft) && goalComplete(draft) && nutritionComplete(draft);
    }

    private boolean profileComplete(OnboardingDraftEntity draft) {
        return hasText(draft.getName())
                && draft.getAge() != null
                && supportedGender(draft.getGender())
                && draft.getHeight() != null
                && draft.getWeight() != null;
    }

    private boolean preferencesComplete(OnboardingDraftEntity draft) {
        return draft.getMarketRegion() != null
                && draft.getPreferredLanguage() != null
                && hasText(draft.getTimeZone())
                && draft.getUnitPreference() != null;
    }

    private boolean goalComplete(OnboardingDraftEntity draft) {
        return draft.getTargetWeight() != null
                && draft.getGoalType() != null
                && draft.getActivityLevel() != null;
    }
    private boolean nutritionComplete(OnboardingDraftEntity draft) {
        return draft.getPrimaryDietaryPreference() != null
                && draft.isAllergenSelectionConfirmed();
    }

    private OnboardingStateDto toState(OnboardingDraftEntity draft) {
        List<OnboardingStep> completedSteps = new ArrayList<>();
        if (profileComplete(draft)) {
            completedSteps.add(OnboardingStep.PROFILE);
        }
        if (preferencesComplete(draft)) {
            completedSteps.add(OnboardingStep.PREFERENCES);
        }
        if (goalComplete(draft)) {
            completedSteps.add(OnboardingStep.GOAL);
        }
        if (nutritionComplete(draft)) {
            completedSteps.add(OnboardingStep.NUTRITION);
        }
        if (draft.isFitnessPreferenceCompleted()) {
            completedSteps.add(OnboardingStep.FITNESS_PREFERENCE);
        }

        OnboardingStep currentStep;
        if (draft.getStatus() == OnboardingStatus.COMPLETED) {
            currentStep = OnboardingStep.COMPLETE;
        } else if (!profileComplete(draft)) {
            currentStep = OnboardingStep.PROFILE;
        } else if (!preferencesComplete(draft)) {
            currentStep = OnboardingStep.PREFERENCES;
        } else if (!goalComplete(draft)) {
            currentStep = OnboardingStep.GOAL;
        } else if (!nutritionComplete(draft)) {
            currentStep = OnboardingStep.NUTRITION;
        } else if (!draft.isFitnessPreferenceCompleted()) {
            currentStep = OnboardingStep.FITNESS_PREFERENCE;
        } else {
            currentStep = OnboardingStep.REVIEW;
        }

        return new OnboardingStateDto(
                draft.getStatus(),
                currentStep,
                List.copyOf(completedSteps),
                canComplete(draft),
                toProfileStep(draft),
                toPreferencesStep(draft),
                toGoalStep(draft),
                toNutritionStep(draft),
                toFitnessPreferenceStep(draft),
                draft.getUpdatedAt(),
                draft.getCompletedAt()
        );
    }

    private OnboardingProfileStepDto toProfileStep(OnboardingDraftEntity draft) {
        return new OnboardingProfileStepDto(
                draft.getName(),
                draft.getAge(),
                draft.getBirthDate(),
                draft.getGender(),
                draft.getHeight(),
                draft.getWeight(),
                draft.getBodyFatPercentage()
        );
    }

    private OnboardingPreferencesStepDto toPreferencesStep(OnboardingDraftEntity draft) {
        return new OnboardingPreferencesStepDto(
                draft.getMarketRegion(),
                draft.getCountryCode(),
                draft.getPreferredLanguage(),
                draft.getTimeZone(),
                draft.getUnitPreference()
        );
    }

    private OnboardingGoalStepDto toGoalStep(OnboardingDraftEntity draft) {
        return new OnboardingGoalStepDto(
                draft.getTargetWeight(),
                draft.getWeeklyWeightChangeTargetKg(),
                draft.getGoalType(),
                draft.getActivityLevel()
        );
    }

    private OnboardingNutritionStepDto toNutritionStep(OnboardingDraftEntity draft) {
        return new OnboardingNutritionStepDto(
                draft.getPrimaryDietaryPreference(),
                new LinkedHashSet<>(draft.getAllergens()),
                draft.isAllergenSelectionConfirmed()
        );
    }

    private OnboardingFitnessPreferenceStepDto toFitnessPreferenceStep(OnboardingDraftEntity draft) {
        return new OnboardingFitnessPreferenceStepDto(
                draft.getWeeklyWorkoutFrequency(),
                draft.isFitnessPreferenceCompleted()
        );
    }
    private UserProfileDto toProfileDto(OnboardingDraftEntity draft) {
        return UserProfileDto.builder()
                .name(draft.getName())
                .age(draft.getAge())
                .birthDate(draft.getBirthDate())
                .gender(draft.getGender())
                .height(draft.getHeight())
                .weight(draft.getWeight())
                .bodyFat(draft.getBodyFatPercentage())
                .marketRegion(draft.getMarketRegion())
                .countryCode(draft.getCountryCode())
                .preferredLanguage(draft.getPreferredLanguage())
                .timeZone(draft.getTimeZone())
                .unitPreference(draft.getUnitPreference())
                .build();
    }

    private CountryCode resolveCountryCode(CountryCode countryCode, MarketRegion marketRegion) {
        if (countryCode != null) {
            return countryCode;
        }
        if (marketRegion == MarketRegion.TR) {
            return CountryCode.TR;
        }
        return null;
    }
    private boolean supportedGender(String value) {
        return "MALE".equalsIgnoreCase(value) || "FEMALE".equalsIgnoreCase(value);
    }
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
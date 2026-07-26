package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.OnboardingGoalStepDto;
import com.grun.calorietracker.dto.OnboardingFitnessPreferenceStepDto;
import com.grun.calorietracker.dto.OnboardingNutritionStepDto;
import com.grun.calorietracker.dto.OnboardingPreferencesStepDto;
import com.grun.calorietracker.dto.OnboardingProfileStepDto;
import com.grun.calorietracker.dto.OnboardingStepUpdateRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.OnboardingDraftEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFitnessPreferenceEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.DietaryPreference;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.WeeklyWorkoutFrequency;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.repository.OnboardingDraftRepository;
import com.grun.calorietracker.repository.UserFitnessPreferenceRepository;
import com.grun.calorietracker.repository.UserNutritionPreferenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.OnboardingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    @Mock private UserService userService;
    @Mock private UserGoalService userGoalService;
    @Mock private UserRepository userRepository;
    @Mock private OnboardingDraftRepository onboardingDraftRepository;
    @Mock private UserNutritionPreferenceRepository userNutritionPreferenceRepository;
    @Mock private UserFitnessPreferenceRepository userFitnessPreferenceRepository;

    private OnboardingServiceImpl onboardingService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        onboardingService = new OnboardingServiceImpl(
                userService,
                userGoalService,
                userRepository,
                onboardingDraftRepository,
                userNutritionPreferenceRepository,
                userFitnessPreferenceRepository,
                new com.grun.calorietracker.service.support.UserTimeZoneSupport(),
                new com.grun.calorietracker.service.support.UserAgeSupport()
        );
        user = user();
        org.mockito.Mockito.lenient().when(onboardingDraftRepository.save(any(OnboardingDraftEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getState_whenNoDraft_returnsNonPersistentSnapshotAtFirstMissingStep() {
        user.setAge(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUser(user)).thenReturn(Optional.empty());
        when(userGoalService.getCurrentUserGoal("user@example.com")).thenReturn(null);

        var state = onboardingService.getState("user@example.com");

        assertEquals(OnboardingStep.PROFILE, state.getCurrentStep());
        assertEquals(OnboardingStatus.IN_PROGRESS, state.getStatus());
        verify(onboardingDraftRepository, never()).save(any());
    }

    @Test
    void getState_hydratesPersistedDietAllergensAndWorkoutFrequency() {
        UserNutritionPreferenceEntity nutrition = new UserNutritionPreferenceEntity();
        nutrition.setUser(user);
        nutrition.setDietaryPreferences(List.of("VEGAN"));
        nutrition.setAllergens(Set.of(RecipeAllergen.PEANUTS));
        UserFitnessPreferenceEntity fitness = new UserFitnessPreferenceEntity();
        fitness.setUser(user);
        fitness.setWeeklyWorkoutFrequency(WeeklyWorkoutFrequency.FIVE_PLUS);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUser(user)).thenReturn(Optional.empty());
        when(userGoalService.getCurrentUserGoal("user@example.com")).thenReturn(null);
        when(userNutritionPreferenceRepository.findByUser(user)).thenReturn(Optional.of(nutrition));
        when(userFitnessPreferenceRepository.findByUser(user)).thenReturn(Optional.of(fitness));

        var state = onboardingService.getState("user@example.com");

        assertEquals(DietaryPreference.VEGAN,
                state.getNutrition().getPrimaryDietaryPreference());
        assertEquals(Set.of(RecipeAllergen.PEANUTS), state.getNutrition().getAllergens());
        assertTrue(state.getNutrition().isAllergenSelectionConfirmed());
        assertEquals(WeeklyWorkoutFrequency.FIVE_PLUS,
                state.getFitnessPreference().getWeeklyWorkoutFrequency());
        assertTrue(state.getFitnessPreference().isSelectionConfirmed());
        verify(onboardingDraftRepository, never()).save(any());
    }
    @Test
    void updateProfile_withPartialPayload_mergesConfirmedFieldsAndRemainsResumable() {
        OnboardingDraftEntity draft = new OnboardingDraftEntity();
        draft.setUser(user);
        draft.setStatus(OnboardingStatus.IN_PROGRESS);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));

        OnboardingStepUpdateRequestDto nameRequest = new OnboardingStepUpdateRequestDto();
        nameRequest.setProfile(new OnboardingProfileStepDto(
                "Emrah", null, null, null, null, null, null
        ));
        var afterName = onboardingService.updateStep(
                OnboardingStep.PROFILE, nameRequest, "user@example.com"
        );

        OnboardingStepUpdateRequestDto genderRequest = new OnboardingStepUpdateRequestDto();
        genderRequest.setProfile(new OnboardingProfileStepDto(
                null, null, null, "MALE", null, null, null
        ));
        var afterGender = onboardingService.updateStep(
                OnboardingStep.PROFILE, genderRequest, "user@example.com"
        );

        assertEquals("Emrah", afterName.getProfile().getName());
        assertEquals("Emrah", afterGender.getProfile().getName());
        assertEquals("MALE", afterGender.getProfile().getGender());
        assertEquals(OnboardingStep.PROFILE, afterGender.getCurrentStep());
        assertTrue(afterGender.getCompletedSteps().isEmpty());
        verify(onboardingDraftRepository, times(2)).save(draft);
    }

    @Test
    void updateGoal_whenOtherStepsComplete_movesDraftToNutrition() {
        OnboardingDraftEntity draft = draftWithoutGoal();
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));

        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setGoal(new OnboardingGoalStepDto(
                78.0,
                0.5,
                GoalType.LOSE_WEIGHT,
                ActivityLevel.MODERATE
        ));

        var state = onboardingService.updateStep(OnboardingStep.GOAL, request, "user@example.com");

        assertEquals(OnboardingStep.NUTRITION, state.getCurrentStep());
        assertEquals(3, state.getCompletedSteps().size());
        org.junit.jupiter.api.Assertions.assertFalse(state.isCanComplete());
        assertEquals(GoalType.LOSE_WEIGHT, draft.getGoalType());
    }

    @Test
    void preview_calculatesFromDraftWithoutChangingLiveProfileOrGoal() {
        OnboardingDraftEntity draft = completeDraft();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUser(user)).thenReturn(Optional.of(draft));
        when(userGoalService.calculateGoalPreview(any(), any()))
                .thenReturn(new GoalCalculationResponse(2209, 138, 74, 248));

        var preview = onboardingService.preview("user@example.com");

        assertEquals(2209, preview.getCalculation().getCalculatedCalorieNeed());
        assertEquals(OnboardingStep.REVIEW, preview.getState().getCurrentStep());
        verify(userService, never()).updateCurrentUser(any(), any());
        verify(userGoalService, never()).saveUserGoal(any(), any());
        verify(userRepository, never()).findByEmailForUpdate(any());
        verify(onboardingDraftRepository, never()).findByUserForUpdate(any());
        verify(onboardingDraftRepository, never()).save(any());
    }

    @Test
    void completeOnboarding_isIdempotentAfterFirstSuccessfulCompletion() {
        OnboardingDraftEntity draft = completeDraft();
        UserProfileDto profile = UserProfileDto.builder().name("Emrah").build();
        UserGoalDto goal = savedGoal();

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));
        when(userService.updateCurrentUser(any(UserProfileDto.class), eq("user@example.com"))).thenReturn(profile);
        when(userService.getMyProfile("user@example.com")).thenReturn(myProfile());
        when(userGoalService.saveUserGoal(any(), eq("user@example.com"))).thenReturn(goal);
        GoalCalculationResponse detailedCalculation =
                new GoalCalculationResponse(2209, 138, 74, 248);
        detailedCalculation.setFormula("MIFFLIN_ST_JEOR");
        detailedCalculation.setEstimatedDurationWeeks(8);
        when(userGoalService.calculateGoalPreview(any(), any()))
                .thenReturn(detailedCalculation);

        var first = onboardingService.completeOnboarding("user@example.com");

        assertTrue(first.isOnboardingCompleted());
        assertEquals(OnboardingStatus.COMPLETED, draft.getStatus());

        when(userService.getMyProfile("user@example.com")).thenReturn(myProfile());
        when(userGoalService.getCurrentUserGoal("user@example.com")).thenReturn(goal);

        var retry = onboardingService.completeOnboarding("user@example.com");

        assertEquals(2209, retry.getCalculation().getCalculatedCalorieNeed());
        assertEquals("MIFFLIN_ST_JEOR", retry.getCalculation().getFormula());
        assertEquals(8, retry.getCalculation().getEstimatedDurationWeeks());
        verify(userService, times(1)).updateCurrentUser(any(), eq("user@example.com"));
        verify(userGoalService, times(1)).saveUserGoal(any(), eq("user@example.com"));
        org.mockito.ArgumentCaptor<UserNutritionPreferenceEntity> nutritionCaptor =
                org.mockito.ArgumentCaptor.forClass(UserNutritionPreferenceEntity.class);
        verify(userNutritionPreferenceRepository, times(1)).save(nutritionCaptor.capture());
        assertEquals(List.of("BALANCED"), nutritionCaptor.getValue().getDietaryPreferences());
        assertEquals(Set.of(), nutritionCaptor.getValue().getAllergens());

        org.mockito.ArgumentCaptor<UserFitnessPreferenceEntity> fitnessCaptor =
                org.mockito.ArgumentCaptor.forClass(UserFitnessPreferenceEntity.class);
        verify(userFitnessPreferenceRepository, times(1)).save(fitnessCaptor.capture());
        assertEquals(WeeklyWorkoutFrequency.THREE_TO_FOUR,
                fitnessCaptor.getValue().getWeeklyWorkoutFrequency());
    }

    @Test
    void completeOnboarding_whenGoalSaveFails_doesNotMarkDraftCompleted() {
        OnboardingDraftEntity draft = completeDraft();
        when(userRepository.findByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user))
                .thenReturn(Optional.of(draft));
        when(userGoalService.calculateGoalPreview(any(), any()))
                .thenReturn(new GoalCalculationResponse(2209, 138, 74, 248));
        when(userGoalService.saveUserGoal(any(), eq("user@example.com")))
                .thenThrow(new IllegalStateException("goal save failed"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> onboardingService.completeOnboarding("user@example.com")
        );

        assertEquals(OnboardingStatus.IN_PROGRESS, draft.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(draft.getCompletedAt());
        verify(onboardingDraftRepository, never()).save(draft);
    }
    @Test
    void updateNutrition_persistsCanonicalDietAndExplicitAllergenSelection() {
        OnboardingDraftEntity draft = draftWithoutGoal();
        draft.setTargetWeight(78.0);
        draft.setGoalType(GoalType.LOSE_WEIGHT);
        draft.setActivityLevel(ActivityLevel.MODERATE);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));

        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setNutrition(new OnboardingNutritionStepDto(
                DietaryPreference.VEGAN,
                Set.of(RecipeAllergen.PEANUTS),
                true
        ));

        var state = onboardingService.updateStep(OnboardingStep.NUTRITION, request, "user@example.com");

        assertEquals(DietaryPreference.VEGAN, draft.getPrimaryDietaryPreference());
        assertEquals(Set.of(RecipeAllergen.PEANUTS), draft.getAllergens());
        assertEquals(OnboardingStep.FITNESS_PREFERENCE, state.getCurrentStep());
        assertTrue(state.isCanComplete());
    }

    @Test
    void updateFitnessPreference_canExplicitlySkipOptionalQuestion() {
        OnboardingDraftEntity draft = completeDraft();
        draft.setFitnessPreferenceCompleted(false);
        draft.setWeeklyWorkoutFrequency(null);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));

        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setFitnessPreference(new OnboardingFitnessPreferenceStepDto(null, true));

        var state = onboardingService.updateStep(
                OnboardingStep.FITNESS_PREFERENCE,
                request,
                "user@example.com"
        );

        assertTrue(draft.isFitnessPreferenceCompleted());
        assertEquals(ActivityLevel.MODERATE, draft.getActivityLevel());
        assertEquals(OnboardingStep.REVIEW, state.getCurrentStep());
        assertTrue(state.isCanComplete());
    }
    @Test
    void updateProfile_withBirthDate_persistsBirthDateAndDerivesAge() {
        OnboardingDraftEntity draft = draftWithoutGoal();
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(onboardingDraftRepository.findByUserForUpdate(user)).thenReturn(Optional.of(draft));

        java.time.LocalDate birthDate = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Dublin"))
                .minusYears(32);
        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setProfile(new OnboardingProfileStepDto(
                "Emrah",
                null,
                birthDate,
                "MALE",
                180.0,
                82.0,
                19.2
        ));

        onboardingService.updateStep(OnboardingStep.PROFILE, request, "user@example.com");

        assertEquals(birthDate, draft.getBirthDate());
        assertEquals(32, draft.getAge());
    }
    private com.grun.calorietracker.dto.MyProfileDto myProfile() {
        return com.grun.calorietracker.dto.MyProfileDto.builder()
                .email("user@example.com")
                .name("Emrah")
                .body(com.grun.calorietracker.dto.ProfileBodyDto.builder().build())
                .preferences(com.grun.calorietracker.dto.ProfilePreferencesDto.builder().build())
                .security(com.grun.calorietracker.dto.ProfileSecurityDto.builder().build())
                .goalRecalculationRecommended(false)
                .build();
    }
    private UserEntity user() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setEmail("user@example.com");
        entity.setName("Emrah");
        entity.setAge(32);
        entity.setGender("MALE");
        entity.setHeight(180.0);
        entity.setWeight(82.0);
        entity.setBodyFatPercentage(19.2);
        entity.setMarketRegion(MarketRegion.UK_IE);
        entity.setPreferredLanguage(PreferredLanguage.EN);
        entity.setTimeZone("Europe/Dublin");
        entity.setUnitPreference(UnitPreference.METRIC);
        return entity;
    }

    private OnboardingDraftEntity draftWithoutGoal() {
        OnboardingDraftEntity draft = new OnboardingDraftEntity();
        draft.setUser(user);
        draft.setName("Emrah");
        draft.setAge(32);
        draft.setGender("MALE");
        draft.setHeight(180.0);
        draft.setWeight(82.0);
        draft.setBodyFatPercentage(19.2);
        draft.setMarketRegion(MarketRegion.UK_IE);
        draft.setPreferredLanguage(PreferredLanguage.EN);
        draft.setTimeZone("Europe/Dublin");
        draft.setUnitPreference(UnitPreference.METRIC);
        draft.setStatus(OnboardingStatus.IN_PROGRESS);
        return draft;
    }

    private OnboardingDraftEntity completeDraft() {
        OnboardingDraftEntity draft = draftWithoutGoal();
        draft.setTargetWeight(78.0);
        draft.setWeeklyWeightChangeTargetKg(0.5);
        draft.setGoalType(GoalType.LOSE_WEIGHT);
        draft.setActivityLevel(ActivityLevel.MODERATE);
        draft.setPrimaryDietaryPreference(DietaryPreference.BALANCED);
        draft.setAllergenSelectionConfirmed(true);
        draft.setFitnessPreferenceCompleted(true);
        draft.setWeeklyWorkoutFrequency(WeeklyWorkoutFrequency.THREE_TO_FOUR);
        return draft;
    }

    private UserGoalDto savedGoal() {
        UserGoalDto goal = new UserGoalDto();
        goal.setDailyCalorieGoal(2209);
        goal.setDailyProteinGoal(138.0);
        goal.setDailyFatGoal(74.0);
        goal.setDailyCarbGoal(248.0);
        return goal;
    }
}
package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdvancedUserGoalServiceImpl;
import com.grun.calorietracker.service.support.AdvancedMacroTargetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedUserGoalServiceImplTest {
    @Mock GoalRepository goalRepository;
    @Mock GoalTargetAcknowledgementRepository acknowledgementRepository;
    @Mock AdvancedGoalPreviewRepository previewRepository;
    @Mock AdvancedGoalSaveRequestRepository saveRequestRepository;
    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock UserGoalService userGoalService;
    @Mock SubscriptionService subscriptionService;
    @Mock UserAnalyticsCacheRevisionService cacheRevisionService;
    @Mock ProductAnalyticsService productAnalyticsService;

    private AdvancedUserGoalServiceImpl service;
    private UserEntity user;
    private UserGoalEntity activeGoal;
    private final AtomicReference<AdvancedGoalPreviewEntity> storedPreview = new AtomicReference<>();
    private final AtomicReference<AdvancedGoalSaveRequestEntity> storedSave = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new AdvancedUserGoalServiceImpl(
                goalRepository, acknowledgementRepository, previewRepository, saveRequestRepository,
                userRepository, userService, userGoalService, subscriptionService,
                new AdvancedMacroTargetPolicy(), cacheRevisionService, productAnalyticsService);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("advanced@grun.app");
        user.setAge(30);
        user.setWeight(80.0);
        user.setHeight(180.0);
        user.setGender("MALE");
        user.setTimeZone("Europe/London");
        activeGoal = new UserGoalEntity();
        activeGoal.setId(10L);
        activeGoal.setUser(user);
        activeGoal.setVersion(3L);

        lenient().when(userService.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(userGoalService.calculateGoal(any(), eq(user.getEmail()))).thenReturn(automatic());
        lenient().when(goalRepository.findByUser(user)).thenReturn(Optional.of(activeGoal));
        lenient().when(previewRepository.save(any())).thenAnswer(invocation -> {
            AdvancedGoalPreviewEntity value = invocation.getArgument(0);
            storedPreview.set(value);
            return value;
        });
        lenient().when(previewRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedPreview.get()).filter(value -> value.getToken().equals(invocation.getArgument(0))));
        lenient().when(saveRequestRepository.findByUserIdAndIdempotencyKey(eq(user.getId()), any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedSave.get())
                        .filter(value -> value.getIdempotencyKey().equals(invocation.getArgument(1))));
        lenient().when(saveRequestRepository.save(any())).thenAnswer(invocation -> {
            AdvancedGoalSaveRequestEntity value = invocation.getArgument(0);
            storedSave.set(value);
            return value;
        });
        lenient().when(goalRepository.save(any())).thenAnswer(invocation -> {
            UserGoalEntity value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(11L);
                value.setVersion(0L);
            }
            return value;
        });
    }

    @Test
    void previewBindsTokenToProfileAndActiveGoalVersion() {
        AdvancedGoalPreviewDto preview = service.preview(request(), user.getEmail());

        assertNotNull(preview.getPreviewToken());
        assertEquals(64, preview.getProfileVersion().length());
        assertEquals(3L, preview.getGoalVersion());
        assertEquals(preview.getPreviewToken(), storedPreview.get().getToken());
    }

    @Test
    void repeatedSaveWithSameKeyReturnsOriginalGoalWithoutCreatingAnotherVersion() {
        AdvancedGoalPreviewDto preview = service.preview(request(), user.getEmail());
        AdvancedGoalRequestDto request = request();
        request.setPreviewToken(preview.getPreviewToken());
        request.setExpectedProfileVersion(preview.getProfileVersion());
        request.setExpectedGoalVersion(preview.getGoalVersion());
        request.setWarningsAcknowledged(true);

        UserGoalDto first = service.save(request, user.getEmail(), "advanced-goal:test-1234");
        UserGoalDto replay = service.save(request, user.getEmail(), "advanced-goal:test-1234");

        assertEquals(first.getId(), replay.getId());
        assertEquals(11L, replay.getId());
        verify(cacheRevisionService, times(1)).bump(user.getId(), AnalyticsMutationSource.GOAL);
        verify(saveRequestRepository, times(1)).save(any());
    }

    private static AdvancedGoalRequestDto request() {
        AdvancedGoalRequestDto request = new AdvancedGoalRequestDto();
        request.setTargetWeight(75.0);
        request.setWeeklyWeightChangeTargetKg(0.5);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        request.setActivityLevel(ActivityLevel.MODERATE);
        request.setMode(GoalCalculationMode.MANUAL);
        request.setProteinGrams(150.0);
        request.setCarbGrams(200.0);
        request.setFatGrams(60.0);
        return request;
    }

    private static GoalCalculationResponse automatic() {
        GoalCalculationResponse response = new GoalCalculationResponse(2000, 125, 67, 224);
        response.setMaintenanceCalories(2400);
        return response;
    }
}

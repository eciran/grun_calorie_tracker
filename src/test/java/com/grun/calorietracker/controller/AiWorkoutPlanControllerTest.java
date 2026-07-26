package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiWorkoutPlanDayDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiWorkoutPlanCreditEstimateDto;
import com.grun.calorietracker.dto.AiWorkoutPlanExerciseDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.service.AiWorkoutPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiWorkoutPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiWorkoutPlanService aiWorkoutPlanService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void estimateCreditCost_returnsBackendCalculatedWorkoutCost() throws Exception {
        when(aiWorkoutPlanService.estimateCreditCost("user@example.com", 6, 75))
                .thenReturn(new AiWorkoutPlanCreditEstimateDto(6, 75, 450, 1, 90, 120, 3, 4));

        mockMvc.perform(get("/api/v1/ai/workout-plans/credit-cost")
                        .param("daysPerWeek", "6")
                        .param("minutesPerSession", "75"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlannedMinutes").value(450))
                .andExpect(jsonPath("$.totalCreditCost").value(4));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void generateDraft_returnsWorkoutPlanDraft() throws Exception {
        when(aiWorkoutPlanService.createDraft(eq("user@example.com"), eq("workout-request-123"), any())).thenReturn(draft());

        mockMvc.perform(post("/api/v1/ai/workout-plans/generate")
                        .header("Idempotency-Key", "workout-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "Lose fat while keeping muscle",
                                  "level": "BEGINNER",
                                  "daysPerWeek": 4,
                                  "minutesPerSession": 45,
                                  "equipment": ["DUMBBELLS"],
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(81))
                .andExpect(jsonPath("$.requestType").value("AI_WORKOUT_PLAN"))
                .andExpect(jsonPath("$.days[0].exercises[0].name").value("Push-Up"))
                .andExpect(jsonPath("$.quotaConsumedAmount").value(4))
                .andExpect(jsonPath("$.aiBaseRemainingThisPeriod").value(9))
                .andExpect(jsonPath("$.aiAddonRemainingThisPeriod").value(2))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(11));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void confirmDraft_returnsActiveWorkoutPlan() throws Exception {
        WorkoutPlanDto plan = plan();
        when(aiWorkoutPlanService.confirmDraft(eq("user@example.com"), eq(81L), any())).thenReturn(plan);

        mockMvc.perform(post("/api/v1/ai/workout-plans/81/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": {
                                    "requestId": 81,
                                    "requestType": "AI_WORKOUT_PLAN",
                                    "name": "Starter strength plan",
                                    "days": [
                                      {
                                        "dayLabel": "Day 1",
                                        "exercises": [
                                          { "name": "Push-Up", "measurementType": "SETS_REPS", "setCount": 3, "reps": 10 }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void updateSchedule_acceptsUserConfirmedWorkoutCalendar() throws Exception {
        WorkoutPlanDto scheduled = plan();
        scheduled.setScheduleReady(true);
        scheduled.setScheduleVersion("workout_schedule_v1");
        when(aiWorkoutPlanService.updateSchedule(eq("user@example.com"), eq(99L), any()))
                .thenReturn(scheduled);

        mockMvc.perform(put("/api/v1/ai/workout-plans/99/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessions": [
                                    {
                                      "dayIndex": 0,
                                      "scheduledDate": "2026-07-20",
                                      "scheduledStartTime": "18:00:00",
                                      "intensity": "MODERATE"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleReady").value(true))
                .andExpect(jsonPath("$.scheduleVersion").value("workout_schedule_v1"));
        verify(aiWorkoutPlanService).updateSchedule(eq("user@example.com"), eq(99L), any());
    }
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void listAndArchivePlans_useUserOwnedPlanEndpoints() throws Exception {
        when(aiWorkoutPlanService.listActivePlans("user@example.com")).thenReturn(List.of(plan()));

        mockMvc.perform(get("/api/v1/ai/workout-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99));

        when(aiWorkoutPlanService.listAllPlans("user@example.com")).thenReturn(List.of(plan()));
        mockMvc.perform(get("/api/v1/ai/workout-plans").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99));
        verify(aiWorkoutPlanService).listAllPlans("user@example.com");

        mockMvc.perform(delete("/api/v1/ai/workout-plans/99"))
                .andExpect(status().isNoContent());
        verify(aiWorkoutPlanService).archivePlan("user@example.com", 99L);
    }

    private WorkoutPlanDto plan() {
        WorkoutPlanDto dto = new WorkoutPlanDto();
        dto.setId(99L);
        dto.setName("Starter strength plan");
        dto.setStatus(WorkoutPlanStatus.ACTIVE);
        dto.setActive(true);
        dto.setPlan(draft());
        return dto;
    }

    private AiWorkoutPlanDraftResponseDto draft() {
        AiWorkoutPlanExerciseDto exercise = new AiWorkoutPlanExerciseDto();
        exercise.setName("Push-Up");
        exercise.setMeasurementType(ExerciseLogMeasurementType.SETS_REPS);
        exercise.setSetCount(3);
        exercise.setReps(10);

        AiWorkoutPlanDayDto day = new AiWorkoutPlanDayDto();
        day.setDayLabel("Day 1");
        day.setFocus("Upper body");
        day.setExercises(List.of(exercise));

        AiWorkoutPlanDraftResponseDto response = new AiWorkoutPlanDraftResponseDto();
        response.setRequestId(81L);
        response.setRequestType(AiRequestType.AI_WORKOUT_PLAN);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setProvider(AiProvider.LOG);
        response.setModel("log-draft-v1");
        response.setName("Starter strength plan");
        response.setSummary("Review before activating.");
        response.setQuotaConsumedAmount(4);
        response.setAiBaseRemainingThisPeriod(9);
        response.setAiAddonRemainingThisPeriod(2);
        response.setAiRemainingThisPeriod(11);
        response.setDays(List.of(day));
        return response;
    }
}

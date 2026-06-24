package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grun.calorietracker.dto.FastingDailySummaryDto;
import com.grun.calorietracker.dto.FastingDailyTrendDto;
import com.grun.calorietracker.dto.FastingPlanDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.dto.FastingRangeSummaryDto;
import com.grun.calorietracker.dto.FastingSessionCancelRequestDto;
import com.grun.calorietracker.dto.FastingSessionDto;
import com.grun.calorietracker.dto.FastingSessionFinishRequestDto;
import com.grun.calorietracker.dto.FastingSessionPageDto;
import com.grun.calorietracker.dto.FastingSessionStartRequestDto;
import com.grun.calorietracker.enums.FastingPlanType;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.service.FastingTrackingService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FastingTrackingControllerTest {

    private static final User TEST_USER = (User) User.withUsername("user@grun.app")
            .password("password")
            .roles("USER")
            .build();

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private FastingTrackingService fastingTrackingService;

    @BeforeEach
    void setUp() {
        fastingTrackingService = mock(FastingTrackingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FastingTrackingController(
                        fastingTrackingService,
                        mock(UserService.class),
                        new UserTimeZoneSupport()
                ))
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getPlan_returnsCurrentPlan() throws Exception {
        when(fastingTrackingService.getPlan("user@grun.app")).thenReturn(planDto());

        mockMvc.perform(get("/api/v1/fasting/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("FASTING_16_8"))
                .andExpect(jsonPath("$.fastingHours").value(16));
    }

    @Test
    void updatePlan_returnsUpdatedPlan() throws Exception {
        FastingPlanRequestDto request = new FastingPlanRequestDto();
        request.setPlanType(FastingPlanType.FASTING_16_8);
        request.setFastingHours(16);
        request.setEatingWindowHours(8);
        request.setPreferredStartTime(LocalTime.of(20, 0));
        request.setActive(true);
        request.setReminderEnabled(true);

        when(fastingTrackingService.updatePlan(any(), any(FastingPlanRequestDto.class))).thenReturn(planDto());

        mockMvc.perform(put("/api/v1/fasting/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderEnabled").value(true));
    }

    @Test
    void startSession_returnsActiveSession() throws Exception {
        FastingSessionStartRequestDto request = new FastingSessionStartRequestDto();
        request.setStartedAt(LocalDateTime.of(2026, 6, 5, 20, 0));
        when(fastingTrackingService.startSession(any(), any(FastingSessionStartRequestDto.class)))
                .thenReturn(sessionDto(FastingSessionStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/fasting/sessions/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.targetMinutes").value(960));
    }

    @Test
    void finishSession_returnsCompletedSession() throws Exception {
        FastingSessionFinishRequestDto request = new FastingSessionFinishRequestDto();
        request.setEndedAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        when(fastingTrackingService.finishSession(any(), any(), any(FastingSessionFinishRequestDto.class)))
                .thenReturn(sessionDto(FastingSessionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/fasting/sessions/12/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void cancelSession_returnsCancelledSession() throws Exception {
        FastingSessionCancelRequestDto request = new FastingSessionCancelRequestDto();
        request.setCancelledAt(LocalDateTime.of(2026, 6, 5, 22, 0));
        when(fastingTrackingService.cancelSession(any(), any(), any(FastingSessionCancelRequestDto.class)))
                .thenReturn(sessionDto(FastingSessionStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/fasting/sessions/12/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void getSessions_returnsPagedCompletedHistory() throws Exception {
        FastingSessionPageDto page = new FastingSessionPageDto();
        page.setContent(List.of(sessionDto(FastingSessionStatus.COMPLETED)));
        page.setPage(0);
        page.setSize(20);
        page.setTotalElements(1);
        page.setTotalPages(1);

        when(fastingTrackingService.getSessions(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/fasting/sessions")
                        .param("status", "COMPLETED")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-24")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
    @Test
    void getDailySummary_returnsSummary() throws Exception {
        FastingDailySummaryDto summary = new FastingDailySummaryDto();
        summary.setDate(LocalDate.of(2026, 6, 5));
        summary.setPlan(planDto());
        summary.setSessions(List.of(sessionDto(FastingSessionStatus.COMPLETED)));
        summary.setTotalCompletedMinutes(960);
        summary.setCurrentStreakDays(2);

        when(fastingTrackingService.getDailySummary("user@grun.app", LocalDate.of(2026, 6, 5))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/fasting/summary")
                        .param("date", "2026-06-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedMinutes").value(960))
                .andExpect(jsonPath("$.currentStreakDays").value(2));
    }

    @Test
    void getRangeSummary_returnsHistoryMetrics() throws Exception {
        FastingRangeSummaryDto summary = new FastingRangeSummaryDto();
        summary.setStartDate(LocalDate.of(2026, 6, 1));
        summary.setEndDate(LocalDate.of(2026, 6, 7));
        summary.setDayCount(7);
        summary.setSessionCount(3);
        summary.setCompletedSessionCount(2);
        summary.setTargetReachedSessionCount(1);
        summary.setTotalCompletedMinutes(1800);
        summary.setAverageCompletedMinutes(900);
        summary.setBestSessionMinutes(960);
        summary.setTargetSuccessRate(0.5D);
        summary.setCurrentStreakDays(1);
        FastingDailyTrendDto trend = new FastingDailyTrendDto();
        trend.setDate(LocalDate.of(2026, 6, 1));
        trend.setSessionCount(1);
        trend.setCompletedSessionCount(1);
        trend.setTargetReachedSessionCount(1);
        trend.setTotalCompletedMinutes(960);
        summary.setDailyTrends(List.of(trend));

        when(fastingTrackingService.getRangeSummary("user@grun.app", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/fasting/summary/range")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayCount").value(7))
                .andExpect(jsonPath("$.targetSuccessRate").value(0.5))
                .andExpect(jsonPath("$.dailyTrends[0].totalCompletedMinutes").value(960));
    }

    private static class TestAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return TEST_USER;
        }
    }

    private FastingPlanDto planDto() {
        FastingPlanDto dto = new FastingPlanDto();
        dto.setId(1L);
        dto.setPlanType(FastingPlanType.FASTING_16_8);
        dto.setFastingHours(16);
        dto.setEatingWindowHours(8);
        dto.setPreferredStartTime(LocalTime.of(20, 0));
        dto.setActive(true);
        dto.setReminderEnabled(true);
        return dto;
    }

    private FastingSessionDto sessionDto(FastingSessionStatus status) {
        FastingSessionDto dto = new FastingSessionDto();
        dto.setId(12L);
        dto.setStatus(status);
        dto.setFastingDate(LocalDate.of(2026, 6, 5));
        dto.setStartedAt(LocalDateTime.of(2026, 6, 5, 20, 0));
        dto.setTargetEndAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        dto.setTargetMinutes(960);
        dto.setActualMinutes(status == FastingSessionStatus.COMPLETED ? 960 : null);
        dto.setTargetReached(status == FastingSessionStatus.COMPLETED);
        return dto;
    }
}

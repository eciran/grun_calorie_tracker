package com.grun.calorietracker.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.EmailVerificationMailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileFoodDiaryFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FoodItemRepository foodItemRepository;
    @MockBean
    private EmailVerificationMailSender emailVerificationMailSender;

    @Test
    void registerVerifyLoginOnboardAndReuseFoodDiaryMeal() throws Exception {
        String email = "flow-user-" + System.nanoTime() + "@grun.test";
        AuthRequest auth = new AuthRequest();
        auth.setEmail(email);
        auth.setPassword("FlowPass1!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auth)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(emailVerificationMailSender).sendEmailVerificationToken(
                org.mockito.ArgumentMatchers.eq(email),
                rawToken.capture(),
                org.mockito.ArgumentMatchers.anyString()
        );
        EmailVerificationConfirmRequestDto confirm = new EmailVerificationConfirmRequestDto();
        confirm.setToken(rawToken.getValue());
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk());

        AuthTokens authTokens = login(auth);
        String token = authTokens.token();

        mockMvc.perform(get("/api/v1/app/startup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.nextStep").value("COMPLETE_ONBOARDING"));

        String refreshedToken = refresh(authTokens.refreshToken());
        token = refreshedToken;

        completeOnboarding(token);

        mockMvc.perform(get("/api/v1/app/startup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.dashboardReady").value(true))
                .andExpect(jsonPath("$.nextStep").value("OPEN_DASHBOARD"))
                .andExpect(jsonPath("$.subscription.plan").value("FREE"))
                .andExpect(jsonPath("$.subscription.planType").value("FREE"));

        long customFoodId = createCustomFood(token);
        addMicronutrients(customFoodId);
        logBreakfast(token, customFoodId);

        mockMvc.perform(get("/api/v1/food-logs/meals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("date", "2026-05-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$[0].logs[0].foodItemId").value(customFoodId))
                .andExpect(jsonPath("$[0].logs[0].snapshotFiber").value(8.0))
                .andExpect(jsonPath("$[0].logs[0].snapshotSodium").value(96.0));

        mockMvc.perform(get("/api/v1/food-logs/recent-meals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceDate").value("2026-05-22"));

        long templateId = saveTemplate(token);
        logTemplateForNextDay(token, templateId);

        mockMvc.perform(get("/api/v1/dashboard/daily-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("date", "2026-05-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodLogs[0].foodItemId").value(customFoodId))
                .andExpect(jsonPath("$.foodLogs[0].snapshotFiber").value(8.0))
                .andExpect(jsonPath("$.consumedMicros.fiber").value(8.0))
                .andExpect(jsonPath("$.consumedMicros.sodium").value(96.0));

        mockMvc.perform(get("/api/v1/food-logs/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("start", "2026-05-23")
                        .param("end", "2026-05-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalFiber").value(8.0))
                .andExpect(jsonPath("$[0].totalSodium").value(96.0));

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmail(email).orElseThrow().getEmailVerified());
    }

    private AuthTokens login(AuthRequest auth) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new AuthTokens(body.get("token").asText(), body.get("refreshToken").asText());
    }

    private String refresh(String refreshToken) throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken(refreshToken);
        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private void completeOnboarding(String token) throws Exception {
        OnboardingCompleteRequestDto request = new OnboardingCompleteRequestDto();
        request.setName("Flow User");
        request.setAge(31);
        request.setGender("MALE");
        request.setHeight(180.0);
        request.setWeight(82.0);
        request.setMarketRegion(MarketRegion.UK_IE);
        request.setPreferredLanguage(PreferredLanguage.EN);
        request.setTargetWeight(78.0);
        request.setWeeklyWeightChangeTargetKg(0.5);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        request.setActivityLevel(ActivityLevel.MODERATE);
        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    private long createCustomFood(String token) throws Exception {
        CustomFoodRequestDto request = new CustomFoodRequestDto();
        request.setName("Flow oats");
        request.setCalories(390.0);
        request.setProtein(13.0);
        request.setCarbs(66.0);
        request.setFat(7.0);
        String response = mockMvc.perform(post("/api/v1/products/custom")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addMicronutrients(long foodId) {
        var food = foodItemRepository.findById(foodId).orElseThrow();
        food.setFiber(10.0);
        food.setSugar(12.0);
        food.setSaturatedFat(1.5);
        food.setSodium(120.0);
        food.setPotassium(240.0);
        food.setCalcium(80.0);
        food.setIron(2.0);
        foodItemRepository.save(food);
    }

    private void logBreakfast(String token, long foodId) throws Exception {
        FoodLogsDto log = new FoodLogsDto();
        log.setFoodItemId(foodId);
        log.setPortionSize(80.0);
        log.setMealType("BREAKFAST");
        log.setLogDate(LocalDateTime.of(2026, 5, 22, 8, 0));
        mockMvc.perform(post("/api/v1/food-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(log)))
                .andExpect(status().isOk());
    }

    private long saveTemplate(String token) throws Exception {
        MealTemplateCreateRequestDto request = new MealTemplateCreateRequestDto();
        request.setName("Flow breakfast");
        request.setMealType("BREAKFAST");
        request.setSourceDate(LocalDate.of(2026, 5, 22));
        String response = mockMvc.perform(post("/api/v1/meal-templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void logTemplateForNextDay(String token, long templateId) throws Exception {
        MealTemplateApplyRequestDto request = new MealTemplateApplyRequestDto();
        request.setTargetDate(LocalDate.of(2026, 5, 23));
        mockMvc.perform(post("/api/v1/meal-templates/" + templateId + "/apply")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logDate").value("2026-05-23T08:00:00"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthTokens(String token, String refreshToken) {
    }
}

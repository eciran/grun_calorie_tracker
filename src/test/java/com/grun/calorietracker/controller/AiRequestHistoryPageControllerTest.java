package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiRequestHistoryPageDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.service.AiRequestHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiRequestHistoryPageControllerTest {
    private final AiRequestHistoryService service = mock(AiRequestHistoryService.class);
    private MockMvc mvc;

    @Test
    void recoveryBindsHeaderAndOwnerAndDisablesCaching() throws Exception {
        var item = new AiRequestHistoryDto(); item.setId(14L); item.setStatus(AiRequestStatus.PROCESSING);
        when(service.recoverByKey("owner@example.com", AiRequestType.PHOTO_MEAL_LOG, "photo:original"))
                .thenReturn(new com.grun.calorietracker.dto.AiRequestRecoveryDto(true, item));
        mvc.perform(get("/api/v1/ai/requests/recovery").param("requestType", "PHOTO_MEAL_LOG")
                        .param("email", "other@example.com").header("Idempotency-Key", "photo:original"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.found").value(true)).andExpect(jsonPath("$.request.id").value(14))
                .andExpect(jsonPath("$.request.outputPayload").doesNotExist());
        verify(service).recoverByKey("owner@example.com", AiRequestType.PHOTO_MEAL_LOG, "photo:original");
        org.junit.jupiter.api.Assertions.assertNull(com.grun.calorietracker.security.SubscriptionFeatureAccessFilter
                .resolveFeature("GET", "/api/v1/ai/requests/recovery"));
    }

    @Test
    void recoveryRequiresKeyAndKnownType() throws Exception {
        mvc.perform(get("/api/v1/ai/requests/recovery").param("requestType", "PHOTO_MEAL_LOG"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ai/requests/recovery").param("requestType", "UNKNOWN").header("Idempotency-Key", "photo:original"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @BeforeEach
    void setup() {
        var user = User.withUsername("owner@example.com").password("unused").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(new AiRequestHistoryController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void pageRouteBindsFiltersAndUsesAuthenticatedOwner() throws Exception {
        var item = new AiRequestHistoryDto(); item.setId(14L); item.setStatus(AiRequestStatus.DRAFT_CREATED);
        when(service.listHistoryPage(eq("owner@example.com"), anyList(), anyList(), eq(30L), eq(1)))
                .thenReturn(new AiRequestHistoryPageDto(List.of(item), 14L));
        mvc.perform(get("/api/v1/ai/requests/history/page")
                        .param("requestTypes", "PHOTO_MEAL_LOG", "VOICE_FOOD_LOG")
                        .param("statuses", "DRAFT_CREATED", "PROCESSING").param("beforeId", "30").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(14))
                .andExpect(jsonPath("$.nextBeforeId").value(14))
                .andExpect(jsonPath("$.items[0].outputPayload").doesNotExist());
        verify(service).listHistoryPage("owner@example.com", List.of(AiRequestType.PHOTO_MEAL_LOG, AiRequestType.VOICE_FOOD_LOG),
                List.of(AiRequestStatus.DRAFT_CREATED, AiRequestStatus.PROCESSING), 30L, 1);
    }

    @Test
    void legacyListStillReturnsAnArray() throws Exception {
        when(service.listHistory("owner@example.com", null, null, 20)).thenReturn(List.of());
        mvc.perform(get("/api/v1/ai/requests/history")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void invalidTypeIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(get("/api/v1/ai/requests/history/page").param("requestTypes", "OTHER_USER"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}

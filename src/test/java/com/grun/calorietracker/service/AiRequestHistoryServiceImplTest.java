package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AiRequestHistoryDetailDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AiRequestHistoryServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRequestHistoryServiceImplTest {

    private final AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiRequestHistoryServiceImpl service = new AiRequestHistoryServiceImpl(
            historyRepository,
            userRepository,
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void getHistoryItem_whenFailedHistoryHasNoOutputPayload_returnsSafeFallbackPayload() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(7L);
        history.setUser(user);
        history.setRequestType(AiRequestType.PHOTO_MEAL_LOG);
        history.setProvider(AiProvider.OPENAI);
        history.setModel("gpt-4.1-mini");
        history.setPromptVersion("ai-prompt-v3");
        history.setStatus(AiRequestStatus.FAILED);
        history.setQuotaConsumed(false);
        history.setQuotaConsumedAmount(0);
        history.setQuotaRefundedAmount(1);
        history.setCreatedAt(LocalDateTime.now());
        history.setErrorMessage("OpenAI provider request failed: HTTP 400 - technical details");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(history));

        AiRequestHistoryDetailDto result = service.getHistoryItem("user@example.com", 7L);

        assertEquals("ai-prompt-v3", result.getPromptVersion());
        assertEquals("AI analysis could not be completed. Please try again with a different input.", result.getUserMessage());
        assertTrue(result.getHasSafeOutputPayload());
        assertNotNull(result.getSafeOutputPayload());
        assertEquals("ai_error_v1", result.getSafeOutputPayload().get("schemaVersion").asText());
        assertEquals("AI_ANALYSIS_FAILED", result.getSafeOutputPayload().get("errorCode").asText());
        assertEquals("PHOTO_MEAL_LOG", result.getSafeOutputPayload().get("requestType").asText());
    }
}
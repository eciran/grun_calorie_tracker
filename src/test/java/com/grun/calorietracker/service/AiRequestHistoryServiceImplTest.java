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
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.domain.Pageable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRequestHistoryServiceImplTest {

    @Test
    void recoveryReturnsEveryStateWithoutPayloadOrMutation() throws Exception {
        var owner = new UserEntity(); owner.setId(1L);
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(owner));
        for (var state : AiRequestStatus.values()) {
            var row = new AiRequestHistoryEntity(); row.setId(12L); row.setStatus(state);
            row.setRequestType(AiRequestType.PHOTO_MEAL_LOG);
            row.setInputPayload("private photo"); row.setOutputPayload("private result");
            row.setErrorMessage("private provider error"); row.setIdempotencyKey("photo:original");
            when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(owner, AiRequestType.PHOTO_MEAL_LOG, "photo:original"))
                    .thenReturn(Optional.of(row));
            var result = service.recoverByKey("owner", AiRequestType.PHOTO_MEAL_LOG, " photo:original ");
            assertTrue(result.found()); assertEquals(12L, result.request().getId());
            assertEquals(state, result.request().getStatus());
            String json = new ObjectMapper().writeValueAsString(result);
            assertFalse(json.contains("private")); assertFalse(json.contains("Payload"));
            assertFalse(json.contains("photo:original"));
            assertNull(row.getCompletionNotifiedAt());
        }
        verify(historyRepository, never()).save(any());
    }

    @Test
    void recoveryScopesSameKeyToOwnerAndRequestType() {
        var owner = new UserEntity(); owner.setId(1L);
        var other = new UserEntity(); other.setId(2L);
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("other")).thenReturn(Optional.of(other));
        var row = new AiRequestHistoryEntity(); row.setId(12L);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(owner, AiRequestType.PHOTO_MEAL_LOG, "photo:original"))
                .thenReturn(Optional.of(row));
        assertTrue(service.recoverByKey("owner", AiRequestType.PHOTO_MEAL_LOG, "photo:original").found());
        var otherResult = service.recoverByKey("other", AiRequestType.PHOTO_MEAL_LOG, "photo:original");
        assertFalse(otherResult.found()); assertNull(otherResult.request());
        assertFalse(service.recoverByKey("owner", AiRequestType.VOICE_FOOD_LOG, "photo:original").found());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void recoveryRejectsMalformedKeysTypesAndUnknownAccount() {
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(new UserEntity()));
        for (String key : new String[]{null, "", "short", "invalid key", "x".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> service.recoverByKey("owner", AiRequestType.PHOTO_MEAL_LOG, key));
        }
        assertThrows(IllegalArgumentException.class, () -> service.recoverByKey("owner", null, "photo:original"));
        assertThrows(com.grun.calorietracker.exception.InvalidCredentialsException.class,
                () -> service.recoverByKey("unknown", AiRequestType.PHOTO_MEAL_LOG, "photo:original"));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void page_filtersBeforeLimitingAndTraversesOlderRecordsWithoutDuplicates() {
        UserEntity owner = new UserEntity(); owner.setId(1L);
        UserEntity other = new UserEntity(); other.setId(2L);
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(owner));
        List<AiRequestHistoryEntity> fixtures = new ArrayList<>();
        // More than 25 matching records; interleaved users/types share identical timestamps.
        for (long id = 180; id > 0; id--) {
            var row = new AiRequestHistoryEntity();
            row.setId(id); row.setUser(id % 3 == 0 ? other : owner);
            row.setRequestType(id % 2 == 0 ? AiRequestType.AI_WORKOUT_PLAN : AiRequestType.PHOTO_MEAL_LOG);
            row.setStatus(id % 5 == 0 ? AiRequestStatus.FAILED : AiRequestStatus.DRAFT_CREATED);
            row.setCreatedAt(LocalDateTime.of(2026, 8, 28, 12, 0));
            row.setInputPayload("private input"); row.setOutputPayload("private output");
            fixtures.add(row);
        }
        when(historyRepository.findUserHistoryPage(eq(owner), anyList(), anyList(), anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    List<AiRequestType> types = invocation.getArgument(1);
                    List<AiRequestStatus> states = invocation.getArgument(2);
                    long before = invocation.getArgument(3);
                    Pageable pageable = invocation.getArgument(4);
                    return fixtures.stream().filter(row -> row.getUser() == owner && row.getId() < before
                            && types.contains(row.getRequestType()) && states.contains(row.getStatus()))
                            .limit(pageable.getPageSize()).toList();
                });
        var ids = new ArrayList<Long>();
        Long before = null;
        do {
            var page = service.listHistoryPage("owner", List.of(AiRequestType.PHOTO_MEAL_LOG),
                    List.of(AiRequestStatus.DRAFT_CREATED), before, 25);
            assertTrue(page.items().size() <= 25);
            page.items().forEach(item -> ids.add(item.getId()));
            before = page.nextBeforeId();
            assertTrue(ids.size() <= fixtures.size(), "Cursor must progress");
        } while (before != null);
        var expected = fixtures.stream().filter(row -> row.getUser() == owner
                && row.getRequestType() == AiRequestType.PHOTO_MEAL_LOG && row.getStatus() == AiRequestStatus.DRAFT_CREATED)
                .map(AiRequestHistoryEntity::getId).toList();
        assertTrue(expected.size() > 25);
        assertEquals(expected, ids);
        assertEquals(ids.size(), ids.stream().distinct().count());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void page_returnsMetadataOnlyAndStableLookaheadCursor() throws Exception {
        UserEntity owner = new UserEntity(); owner.setId(1L);
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(owner));
        var first = new AiRequestHistoryEntity(); first.setId(20L);
        first.setStatus(AiRequestStatus.FAILED); first.setQuotaRefundedAmount(2);
        first.setInputPayload("secret prompt"); first.setOutputPayload("secret output");
        first.setErrorMessage("private provider error");
        var second = new AiRequestHistoryEntity(); second.setId(19L);
        when(historyRepository.findUserHistoryPage(eq(owner), anyList(), anyList(), eq(Long.MAX_VALUE), any()))
                .thenReturn(List.of(first, second));
        var page = service.listHistoryPage("owner", null, null, null, 1);
        assertEquals(20L, page.nextBeforeId());
        assertEquals(1, page.items().size());
        assertEquals(2, page.items().get(0).getQuotaRefundedAmount());
        String json = new ObjectMapper().writeValueAsString(page);
        assertFalse(json.contains("Payload"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("provider error"));
        verify(historyRepository).findUserHistoryPage(owner, List.of(AiRequestType.values()),
                List.of(AiRequestStatus.values()), Long.MAX_VALUE, org.springframework.data.domain.PageRequest.of(0, 2));
    }

    @Test
    void page_rejectsInvalidBoundsAndUnknownUser() {
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(new UserEntity()));
        for (int limit : List.of(0, -1, 101)) {
            assertThrows(IllegalArgumentException.class, () -> service.listHistoryPage("owner", null, null, null, limit));
        }
        assertThrows(IllegalArgumentException.class, () -> service.listHistoryPage("owner", null, null, 0L, 25));
        assertThrows(com.grun.calorietracker.exception.InvalidCredentialsException.class,
                () -> service.listHistoryPage("unknown", null, null, null, 25));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void page_emptyResultHasNoCursorAndForeignDetailIsNotReadable() {
        var owner = new UserEntity(); owner.setId(1L);
        when(userRepository.findByEmail("owner")).thenReturn(Optional.of(owner));
        when(historyRepository.findUserHistoryPage(eq(owner), anyList(), anyList(), anyLong(), any())).thenReturn(List.of());
        var page = service.listHistoryPage("owner", null, null, 50L, 25);
        assertTrue(page.items().isEmpty()); assertNull(page.nextBeforeId());
        assertThrows(IllegalArgumentException.class, () -> service.getHistoryItem("owner", 77L));
        verify(historyRepository).findByIdAndUser(77L, owner);
        verify(historyRepository, never()).save(any());
    }

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

    @Test
    void acknowledgeCompletion_whenDraftIsReady_marksItSeen() {
        UserEntity user = new UserEntity();
        user.setId(2L);
        user.setEmail("foreground@example.com");

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(8L);
        history.setUser(user);
        history.setStatus(AiRequestStatus.DRAFT_CREATED);

        when(userRepository.findByEmail("foreground@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(8L, user)).thenReturn(Optional.of(history));

        service.acknowledgeCompletion("foreground@example.com", 8L);

        assertNotNull(history.getCompletionNotifiedAt());
        org.mockito.Mockito.verify(historyRepository).save(history);
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.impl.AiCompletionNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCompletionNotificationServiceTest {

    @Test
    void publishPendingNotifications_createsRoutedNotificationAndMarksHistory() {
        AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        AiCompletionNotificationService service = new AiCompletionNotificationService(historyRepository, notificationRepository, pushDeliveryService);
        UserEntity user = new UserEntity(); user.setId(7L);
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(42L); history.setUser(user); history.setRequestType(AiRequestType.AI_WORKOUT_PLAN);
        history.setStatus(AiRequestStatus.DRAFT_CREATED); history.setCreatedAt(LocalDateTime.now());
        when(historyRepository.findPendingCompletionNotifications(anyList(), any(Pageable.class))).thenReturn(List.of(history));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(call -> { NotificationEntity value = call.getArgument(0); value.setId(9L); return value; });

        assertEquals(1, service.publishPendingNotifications());

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        NotificationEntity notification = captor.getValue();
        assertEquals("ai-workout-planner", notification.getTargetRoute());
        assertEquals("42", notification.getTargetId());
        assertEquals("VIEW_AI_RESULT", notification.getPrimaryAction());
        assertEquals("ai_request_ready", notification.getType());
        assertNotNull(history.getCompletionNotifiedAt());
        verify(pushDeliveryService).deliver(notification);
        verify(historyRepository).saveAll(List.of(history));
    }

    @Test
    void publishPendingNotifications_whenNothingFinished_doesNothing() {
        AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        when(historyRepository.findPendingCompletionNotifications(anyList(), any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, new AiCompletionNotificationService(historyRepository, notificationRepository, pushDeliveryService).publishPendingNotifications());
        verifyNoInteractions(notificationRepository, pushDeliveryService);
    }
}
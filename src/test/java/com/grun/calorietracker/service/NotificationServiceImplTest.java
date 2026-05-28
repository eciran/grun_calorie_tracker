package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new NotificationServiceImpl(notificationRepository, userRepository);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void listNotifications_whenUnreadAndTypeFilter_returnsPage() {
        NotificationEntity notification = notification(10L, false, "subscription");
        when(notificationRepository.findByUserAndTypeAndIsRead(eq(user), eq("subscription"), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        var result = service.listNotifications("user@example.com", true, "subscription", 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(10L, result.getContent().get(0).getId());
        assertEquals(false, result.getContent().get(0).getRead());
        assertEquals("subscription", result.getContent().get(0).getType());
    }

    @Test
    void markAsRead_whenOwnedNotification_marksRead() {
        NotificationEntity notification = notification(10L, false, "subscription");
        when(notificationRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.markAsRead("user@example.com", 10L);

        assertEquals(true, result.getRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_whenNotificationDoesNotBelongToUser_throwsNotFound() {
        when(notificationRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markAsRead("user@example.com", 99L));
    }

    @Test
    void markAllAsRead_marksOnlyUnreadUserNotifications() {
        NotificationEntity first = notification(1L, false, "subscription");
        NotificationEntity second = notification(2L, false, "system");
        when(notificationRepository.findByUserAndIsRead(user, false)).thenReturn(List.of(first, second));

        var result = service.markAllAsRead("user@example.com");

        assertEquals(2, result.getUpdatedCount());
        assertEquals(true, first.getIsRead());
        assertEquals(true, second.getIsRead());
        verify(notificationRepository).saveAll(List.of(first, second));
    }

    private NotificationEntity notification(Long id, boolean read, String type) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(id);
        notification.setUser(user);
        notification.setMessage("Feature changed");
        notification.setType(type);
        notification.setIsRead(read);
        notification.setCreatedAt(LocalDateTime.of(2026, 5, 27, 14, 0));
        return notification;
    }
}

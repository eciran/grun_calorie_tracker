package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.NotificationDto;
import com.grun.calorietracker.dto.NotificationPageDto;
import com.grun.calorietracker.dto.NotificationReadAllResponseDto;
import com.grun.calorietracker.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(username = "user@example.com")
    void listNotifications_returnsAuthenticatedUserNotifications() throws Exception {
        NotificationDto notification = notification();
        NotificationPageDto page = new NotificationPageDto();
        page.setContent(List.of(notification));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(notificationService.listNotifications("user@example.com", true, "subscription", 0, 25))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("unreadOnly", "true")
                        .param("type", "subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].type").value("subscription"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void markAsRead_returnsUpdatedNotification() throws Exception {
        NotificationDto notification = notification();
        notification.setRead(true);
        when(notificationService.markAsRead("user@example.com", 10L)).thenReturn(notification);

        mockMvc.perform(patch("/api/v1/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void markAllAsRead_returnsUpdatedCount() throws Exception {
        when(notificationService.markAllAsRead("user@example.com"))
                .thenReturn(new NotificationReadAllResponseDto(3));

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(3));
    }

    private NotificationDto notification() {
        NotificationDto dto = new NotificationDto();
        dto.setId(10L);
        dto.setMessage("Feature changed");
        dto.setType("subscription");
        dto.setRead(false);
        dto.setCreatedAt(LocalDateTime.of(2026, 5, 27, 14, 0));
        return dto;
    }
}

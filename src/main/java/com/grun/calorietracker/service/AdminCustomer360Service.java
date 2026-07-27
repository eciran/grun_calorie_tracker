package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminCustomer360Dto;
import com.grun.calorietracker.dto.AdminUserSupportNoteDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteRequestDto;

public interface AdminCustomer360Service {
    AdminCustomer360Dto getCustomer(Long userId);

    AdminUserSupportNoteDto addSupportNote(Long userId,
                                           AdminUserSupportNoteRequestDto request,
                                           String adminEmail);

    int revokeActiveSessions(Long userId);
}

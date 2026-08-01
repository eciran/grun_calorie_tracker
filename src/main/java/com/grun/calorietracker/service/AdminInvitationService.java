package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

public interface AdminInvitationService {
    AdminInvitationDto create(String ownerEmail, AdminInvitationCreateRequestDto request, String correlationId);
    AdminInvitationPageDto list(int page, int size);
    AdminInvitationDto resend(String ownerEmail, Long id, String correlationId);
    void revoke(String ownerEmail, Long id, String correlationId);
    AdminInvitationDto inspect(String rawToken);
    void accept(AdminInvitationAcceptRequestDto request);
}

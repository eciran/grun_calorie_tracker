package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminSessionPageDto;
import com.grun.calorietracker.dto.OwnerAdminSessionPageDto;
import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.entity.UserEntity;

public interface AdminSessionService {
    AdminSessionLogin create(UserEntity user);
    AdminSessionLogin create(UserEntity user, String userAgent, String remoteAddress);
    AuthResponse refresh(String rawSessionToken);
    boolean validateAndTouch(String sessionId, String email);
    AdminSessionPageDto list(String email, String currentSessionId, int page, int size);
    OwnerAdminSessionPageDto listAllForOwner(String actorEmail, String currentSessionId, int page, int size);
    void revokeAnyForOwner(String actorEmail, String sessionId, String currentSessionId, String reason, String correlationId);
    void revokeSession(String actorEmail, String sessionId, String currentSessionId, String reason, String correlationId);
    void revokeOtherSessions(String actorEmail, String currentSessionId, String reason, String correlationId);
    void revoke(String rawSessionToken);
    void revokeAllForUser(UserEntity user);
    record AdminSessionLogin(AuthResponse response, String rawSessionToken) {}
}

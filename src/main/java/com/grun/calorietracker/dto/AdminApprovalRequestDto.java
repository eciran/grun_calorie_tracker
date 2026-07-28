package com.grun.calorietracker.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.enums.*;
import java.time.Instant;
public record AdminApprovalRequestDto(Long id, AdminApprovalActionType actionType, AdminApprovalStatus status,
 String makerEmail, String checkerEmail, String targetKey, JsonNode payload, String requestReason,
 String decisionReason, Instant createdAt, Instant expiresAt, Instant decidedAt) {}
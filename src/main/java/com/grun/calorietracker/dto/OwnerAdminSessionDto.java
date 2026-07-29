package com.grun.calorietracker.dto;
import java.time.Instant;
public record OwnerAdminSessionDto(String id,String adminEmail,String adminRole,String device,String maskedIp,Instant createdAt,Instant lastActivityAt,Instant idleExpiresAt,Instant absoluteExpiresAt,boolean current) {}

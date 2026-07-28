package com.grun.calorietracker.dto;

public record AdminReauthenticationDto(String token, long expiresInSeconds) {}
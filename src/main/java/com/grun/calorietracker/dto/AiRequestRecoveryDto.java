package com.grun.calorietracker.dto;

/** A missing record is not proof that an interrupted request cannot still arrive. */
public record AiRequestRecoveryDto(boolean found, AiRequestHistoryDto request) {}

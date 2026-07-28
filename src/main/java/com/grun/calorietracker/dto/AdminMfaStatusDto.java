package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminMfaStatusDto(boolean enabled, boolean enrollmentPending, long recoveryCodesRemaining,
                                Instant verifiedAt) {}
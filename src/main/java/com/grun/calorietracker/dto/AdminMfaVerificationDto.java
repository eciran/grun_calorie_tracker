package com.grun.calorietracker.dto;

import java.util.List;

public record AdminMfaVerificationDto(boolean enabled, List<String> recoveryCodes) {}
package com.grun.calorietracker.dto;
import java.time.Instant;
import java.util.Map;
public record TestFeedbackScreenshotUploadDto(String uploadUrl, String method, Map<String,String> requiredHeaders, Instant expiresAt) { }
package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;

public record OwnerErrorGroupStateRequestDto(
        @NotBlank @Pattern(regexp="INVESTIGATING|RESOLVED|REOPENED") String lifecycleStatus,
        @NotBlank @Size(min=8,max=500) String reason,
        @NotBlank @Pattern(regexp="BACKEND|PROXY|ADMIN_WEB|MOBILE") String source,
        @Min(400) @Max(599) Integer status,
        @NotBlank @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|OTHER") String method,
        @NotBlank @Size(max=300) String route,
        @Size(max=80) String errorCode) {}

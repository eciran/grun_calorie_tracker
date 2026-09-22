package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;

public record AdminMailboxAccountRequestDto(
        @NotBlank @Email @Size(max=320) String emailAddress,
        @Size(max=160) String displayName,
        @NotBlank @Size(max=320) String username,
        @Size(max=512) String password,
        @NotBlank @Size(max=255) String imapHost,
        @Min(1) @Max(65535) int imapPort,
        boolean imapSsl,
        @NotBlank @Size(max=255) String smtpHost,
        @Min(1) @Max(65535) int smtpPort,
        boolean smtpSsl,
        boolean enabled) {}

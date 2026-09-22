package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminMailboxAccountDto(Long id,String emailAddress,String displayName,String username,
        String imapHost,int imapPort,boolean imapSsl,String smtpHost,int smtpPort,boolean smtpSsl,
        boolean enabled,boolean passwordConfigured,String connectionStatus,String connectionError,
        Instant lastTestedAt,Instant createdAt,Instant updatedAt) {}

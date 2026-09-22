package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminMailboxMessageDto(
        long uid, long mailboxId, String mailboxAddress, String folder,
        String subject, String sender, Instant receivedAt, boolean seen,
        boolean hasAttachments, String preview) {}

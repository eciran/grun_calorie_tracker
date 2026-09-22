package com.grun.calorietracker.dto;

import java.time.Instant;
import java.util.List;

public record AdminMailboxMessageDetailDto(
        long uid, long mailboxId, String mailboxAddress, String folder,
        String subject, String sender, List<String> recipients, Instant receivedAt,
        boolean seen, boolean hasAttachments, List<String> attachments, String bodyText) {}

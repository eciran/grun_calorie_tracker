package com.grun.calorietracker.dto;

import java.util.List;

/** Metadata-only cursor page. Fetch an owned detail separately to read its output. */
public record AiRequestHistoryPageDto(List<AiRequestHistoryDto> items, Long nextBeforeId) {
}

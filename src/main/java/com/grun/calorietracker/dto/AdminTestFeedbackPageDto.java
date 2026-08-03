package com.grun.calorietracker.dto;

import java.util.List;

public record AdminTestFeedbackPageDto(
        List<AdminTestFeedbackDto> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last
) { }

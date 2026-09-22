package com.grun.calorietracker.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record OwnerOperationalAlertPageDto(List<OwnerOperationalAlertDto> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {
    public static OwnerOperationalAlertPageDto from(Page<OwnerOperationalAlertDto> value) {
        return new OwnerOperationalAlertPageDto(value.getContent(), value.getNumber(), value.getSize(),
                value.getTotalElements(), value.getTotalPages(), value.isFirst(), value.isLast());
    }
}

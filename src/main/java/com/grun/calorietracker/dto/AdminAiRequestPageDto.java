package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Schema(description = "Paginated admin AI request response with a stable JSON contract.")
public class AdminAiRequestPageDto {
    private List<AdminAiRequestReviewDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static AdminAiRequestPageDto from(Page<AdminAiRequestReviewDto> source) {
        AdminAiRequestPageDto response = new AdminAiRequestPageDto();
        response.setContent(source.getContent());
        response.setPage(source.getNumber());
        response.setSize(source.getSize());
        response.setTotalElements(source.getTotalElements());
        response.setTotalPages(source.getTotalPages());
        response.setFirst(source.isFirst());
        response.setLast(source.isLast());
        return response;
    }
}
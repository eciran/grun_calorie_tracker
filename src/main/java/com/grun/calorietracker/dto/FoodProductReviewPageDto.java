package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated admin product review response.")
public class FoodProductReviewPageDto {

    @Schema(description = "Products in the current page.")
    private List<FoodProductDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private Integer page;

    @Schema(description = "Requested page size.", example = "25")
    private Integer size;

    @Schema(description = "Total number of matching products.", example = "248")
    private Long totalElements;

    @Schema(description = "Total number of matching pages.", example = "10")
    private Integer totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private Boolean first;

    @Schema(description = "Whether this is the last page.", example = "false")
    private Boolean last;

    public List<FoodProductDto> getContent() {
        return content;
    }

    public void setContent(List<FoodProductDto> content) {
        this.content = content;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Boolean getFirst() {
        return first;
    }

    public void setFirst(Boolean first) {
        this.first = first;
    }

    public Boolean getLast() {
        return last;
    }

    public void setLast(Boolean last) {
        this.last = last;
    }
}

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.ArrayList;

@Schema(description = "Paginated product search response.")
public class FoodProductSearchPageDto {
    @JsonIgnore
    private boolean cacheable = true;

    @JsonIgnore
    public boolean isCacheable() { return cacheable; }
    public void setCacheable(boolean cacheable) { this.cacheable = cacheable; }

    @Schema(description = "Anonymous identifier used to report which search result was selected.")
    private String searchRequestId;

    @Schema(description = "Products in the current search page.")
    private List<FoodProductDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private Integer page;

    @Schema(description = "Requested page size.", example = "25")
    private Integer size;

    @Schema(description = "Total number of matching products.", example = "152")
    private Long totalElements;

    @Schema(description = "Total number of matching pages.", example = "7")
    private Integer totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private Boolean first;

    @Schema(description = "Whether this is the last page.", example = "false")
    private Boolean last;

    public FoodProductSearchPageDto copyWithSearchRequestId(String requestId) {
        FoodProductSearchPageDto response = new FoodProductSearchPageDto();
        response.searchRequestId = requestId;
        response.content = content == null ? null : new ArrayList<>(content);
        response.page = page;
        response.size = size;
        response.totalElements = totalElements;
        response.totalPages = totalPages;
        response.first = first;
        response.last = last;
        response.cacheable = cacheable;
        return response;
    }

    public String getSearchRequestId() {
        return searchRequestId;
    }

    public void setSearchRequestId(String searchRequestId) {
        this.searchRequestId = searchRequestId;
    }

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

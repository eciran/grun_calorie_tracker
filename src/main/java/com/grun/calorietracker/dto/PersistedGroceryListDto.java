package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.GroceryListStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PersistedGroceryListDto {
    private Long id;
    private Long sourceMealPlanId;
    private String sourceMealPlanName;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime currentSourceUpdatedAt;
    private Boolean sourceOutdated;
    private GroceryListStatus status;
    private Integer totalItems;
    private Integer purchasedItems;
    private Integer visibleItems;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<GroceryListItemResponseDto> items;
}

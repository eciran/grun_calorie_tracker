package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.*;
import java.time.LocalDateTime;
public record MyProductIntakeDto(Long id,String barcode,MarketRegion marketRegion,FoodProductReviewCaseStatus status,FoodProductResolutionMode resolutionMode,String reviewNote,LocalDateTime createdAt,LocalDateTime updatedAt) {}

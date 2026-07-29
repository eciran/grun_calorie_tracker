package com.grun.calorietracker.dto;
import java.util.List;
public record OwnerAdminSessionPageDto(List<OwnerAdminSessionDto> content,int page,int size,long totalElements,int totalPages,boolean first,boolean last) {}

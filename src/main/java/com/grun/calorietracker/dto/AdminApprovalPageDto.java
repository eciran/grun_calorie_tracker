package com.grun.calorietracker.dto;
import java.util.List;
public record AdminApprovalPageDto(List<AdminApprovalRequestDto> content,int page,int size,long totalElements,int totalPages,boolean first,boolean last) {}
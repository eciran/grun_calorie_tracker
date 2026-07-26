package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminNotificationCampaignPageDto {
    private List<AdminNotificationCampaignDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
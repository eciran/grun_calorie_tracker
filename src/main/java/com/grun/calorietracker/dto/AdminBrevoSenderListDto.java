package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminBrevoSenderListDto {
    private boolean providerReachable;
    private String statusMessage;
    private List<AdminBrevoSenderDto> senders;
}

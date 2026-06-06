package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdminBrevoSenderDto {
    private Long id;
    private String name;
    private String email;
    private Boolean active;
    private Boolean dkimError;
    private Boolean spfError;
    private List<Map<String, Object>> ips;
}

package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PushDeliveryResultDto {
    private int attempted;
    private int sent;
    private int skipped;
    private int failed;
}

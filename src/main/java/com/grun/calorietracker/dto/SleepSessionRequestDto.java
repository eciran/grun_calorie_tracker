package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SleepSessionRequestDto {
    @NotNull
    private OffsetDateTime startAt;
    @NotNull
    private OffsetDateTime endAt;
    @Size(max = 255)
    private String externalId;
    @Size(max = 500)
    private String note;
    @Valid
    @Size(max = 100)
    private List<SleepStageRequestDto> stages = new ArrayList<>();
}

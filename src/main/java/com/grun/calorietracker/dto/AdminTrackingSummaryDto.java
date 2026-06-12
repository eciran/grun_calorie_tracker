package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin-only aggregate tracking metrics. Does not expose user-level tracking records.")
public class AdminTrackingSummaryDto {

    @Schema(description = "UTC/local backend time when the summary was generated.")
    private LocalDateTime generatedAt;

    @Schema(description = "Requested summary range in days.", example = "30")
    private int rangeDays;

    @Schema(description = "Inclusive start date for trend data.")
    private LocalDate startDate;

    @Schema(description = "Inclusive end date for trend data.")
    private LocalDate endDate;

    private TrackingModuleSummaryDto water;
    private TrackingModuleSummaryDto fasting;
    private TrackingModuleSummaryDto steps;

    private List<TrackingTrendPointDto> trends;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Aggregate metrics for one tracking module.")
    public static class TrackingModuleSummaryDto {
        private String module;
        private long recordsLastRange;
        private long activeUsersLastRange;
        private long totalValueLastRange;
        private String totalValueUnit;
        private long configuredUsers;
        private long reminderEnabledUsers;
        private long activeNow;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Daily aggregate tracking trend point.")
    public static class TrackingTrendPointDto {
        private LocalDate date;
        private long waterMl;
        private long waterLogs;
        private long waterUsers;
        private long fastingMinutes;
        private long fastingSessions;
        private long fastingUsers;
        private long steps;
        private long stepRecords;
        private long stepUsers;
    }
}

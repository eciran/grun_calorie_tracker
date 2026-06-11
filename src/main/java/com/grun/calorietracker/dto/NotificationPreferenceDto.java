package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user's notification preference settings.")
public class NotificationPreferenceDto {

    @Schema(description = "Whether push notifications are generally allowed for this account.", example = "true")
    private Boolean pushNotificationsEnabled;

    @Schema(description = "Whether meal reminder notifications are enabled.", example = "true")
    private Boolean mealRemindersEnabled;

    @Schema(description = "Whether hydration reminder notifications are enabled. Detailed water reminder schedule remains under water reminder settings.", example = "true")
    private Boolean hydrationRemindersEnabled;
}

package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/** Only user correction facts may cross the export boundary, not internal AI retry diagnostics. */
public final class PersonalDataExportProjection {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> COUNTS = Set.of(
            "suggestedItemCount", "confirmedItemCount", "createdLogCount");
    private static final Set<String> CHANGES = Set.of(
            "itemCountChanged", "foodItemsChanged", "portionsChanged", "mealTypeChanged", "logDateChanged");

    private PersonalDataExportProjection() {}

    public static String correctionSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode input = MAPPER.readTree(raw);
            if (!input.isObject()) {
                return null;
            }
            var result = MAPPER.createObjectNode();
            input.fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if ((COUNTS.contains(field.getKey()) && value.isIntegralNumber())
                        || (CHANGES.contains(field.getKey()) && value.isBoolean())) {
                    result.set(field.getKey(), value);
                }
            });
            return result.isEmpty() ? null : result.toString();
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }
}

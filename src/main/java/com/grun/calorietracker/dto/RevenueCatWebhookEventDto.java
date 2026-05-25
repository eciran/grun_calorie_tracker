package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RevenueCatWebhookEventDto {

    private Event event;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private String id;
        private String type;

        @JsonProperty("app_user_id")
        private String appUserId;

        @JsonProperty("original_app_user_id")
        private String originalAppUserId;

        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("entitlement_id")
        private String entitlementId;

        @JsonProperty("entitlement_ids")
        private List<String> entitlementIds;

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("original_transaction_id")
        private String originalTransactionId;

        @JsonProperty("event_timestamp_ms")
        private Long eventTimestampMs;

        @JsonProperty("purchased_at_ms")
        private Long purchasedAtMs;

        @JsonProperty("expiration_at_ms")
        private Long expirationAtMs;

        @JsonProperty("period_type")
        private String periodType;

        private String store;
        private String environment;

        @JsonProperty("cancel_reason")
        private String cancelReason;
    }
}

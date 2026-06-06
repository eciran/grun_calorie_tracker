package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "grun.revenuecat")
public class RevenueCatProperties {
    private String webhookAuthorization;
    private boolean strictProductMapping = true;
    private Api api = new Api();
    private Entitlements entitlements = new Entitlements();
    private Products products = new Products();

    @Data
    public static class Api {
        private boolean enabled = false;
        private String baseUrl = "https://api.revenuecat.com/v2";
        private String secretKey;
        private String projectId;
        private String currency = "EUR";
    }

    @Data
    public static class Entitlements {
        private List<String> plus = new ArrayList<>(List.of("plus", "plus_access"));
        private List<String> pro = new ArrayList<>(List.of("pro", "pro_access"));
    }

    @Data
    public static class Products {
        private List<String> plus = new ArrayList<>();
        private List<String> pro = new ArrayList<>();
        private Map<String, Integer> aiAddonQuotas = new HashMap<>();
        private Map<String, Integer> aiAddonValidityDays = new HashMap<>();
        private int defaultAiAddonValidityDays = 30;
    }
}

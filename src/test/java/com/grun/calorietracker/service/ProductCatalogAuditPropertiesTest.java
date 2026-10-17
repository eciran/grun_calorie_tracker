package com.grun.calorietracker.service;

import com.grun.calorietracker.config.ProductCatalogAuditProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogAuditPropertiesTest {
    @Test
    void defaultsAreDisabledAndBounded() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new ProductCatalogAuditProperties();
            assertFalse(properties.isEnabled());
            assertTrue(factory.getValidator().validate(properties).isEmpty());
        }
    }

    @Test
    void invalidBudgetsAndTimezoneFailValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new ProductCatalogAuditProperties();
            properties.setBatchSize(1);
            properties.setMaxAttempts(6);
            properties.setTimeBudget(Duration.ofSeconds(31));
            properties.setLease(Duration.ofSeconds(59));
            properties.setZone("invalid-zone");
            var errors = factory.getValidator().validate(properties);
            assertTrue(errors.stream().anyMatch(e -> e.getPropertyPath().toString().equals("batchSize")));
            assertTrue(errors.stream().anyMatch(e -> e.getPropertyPath().toString().equals("maxAttempts")));
            assertTrue(errors.stream().anyMatch(e -> e.getPropertyPath().toString().equals("durationConfigurationValid")));
            assertTrue(errors.stream().anyMatch(e -> e.getPropertyPath().toString().equals("scheduleConfigurationValid")));
        }
    }
}

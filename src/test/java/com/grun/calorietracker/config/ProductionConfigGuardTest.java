package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigGuardTest {

    @Test
    void validateProductionConfiguration_whenJwtSecretUsesLocalFallback_failsFast() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("jwt.secret", "Q0hBTkdFX01FX0xPQ0FMX0RFVkVMT1BNRU5UX1NFQ1JFVF9LRVlfMTIzNDU2Nzg5MA==");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenRevenueCatWebhookAuthMissing_failsFast() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("grun.revenuecat.webhook-authorization", "");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenBrevoProviderWithoutApiKey_failsFast() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("grun.mail.brevo.api-key", "");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenRequiredSecretsConfigured_passes() {
        ProductionConfigGuard guard = new ProductionConfigGuard(baseEnvironment());

        assertDoesNotThrow(guard::validateProductionConfiguration);
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("jwt.secret", "U1VQRVJfU0VDVVJFX1BST0RfSldUX1NFQ1JFVF9LRVlfMTIzNDU2")
                .withProperty("grun.revenuecat.webhook-authorization", "Bearer production-webhook-secret")
                .withProperty("grun.mail.provider", "BREVO")
                .withProperty("grun.mail.brevo.api-key", "xkeysib-production");
    }
}

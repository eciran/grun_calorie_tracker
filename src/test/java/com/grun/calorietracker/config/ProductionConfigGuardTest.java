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
    void validateProductionConfiguration_whenBrevoTemplateIdMissing_failsFast() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("grun.mail.brevo.templates.password-reset-en", "0");
        ProductionConfigGuard guard = new ProductionConfigGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenRequiredSecretsConfigured_passes() {
        ProductionConfigGuard guard = new ProductionConfigGuard(baseEnvironment());

        assertDoesNotThrow(guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenPushDisabled_failsFast() {
        ProductionConfigGuard guard = new ProductionConfigGuard(
                baseEnvironment().withProperty("grun.push.enabled", "false"));

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_whenPushProviderDoesNotMatchMobileTokens_failsFast() {
        ProductionConfigGuard guard = new ProductionConfigGuard(
                baseEnvironment().withProperty("grun.push.provider", "FCM"));

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("jwt.secret", "U1VQRVJfU0VDVVJFX1BST0RfSldUX1NFQ1JFVF9LRVlfMTIzNDU2")
                .withProperty("grun.revenuecat.webhook-authorization", "Bearer production-webhook-secret")
                .withProperty("grun.mail.provider", "BREVO")
                .withProperty("grun.mail.brevo.api-key", "xkeysib-production")
                .withProperty("grun.mail.brevo.templates.email-verification-en", "1")
                .withProperty("grun.mail.brevo.templates.email-verification-tr", "2")
                .withProperty("grun.mail.brevo.templates.password-reset-en", "3")
                .withProperty("grun.mail.brevo.templates.password-reset-tr", "4")
                .withProperty("grun.mail.brevo.templates.subscription-feature-change-en", "5")
                .withProperty("grun.mail.brevo.templates.subscription-feature-change-tr", "6")
                .withProperty("grun.mail.brevo.templates.admin-invitation-en", "7")
                .withProperty("grun.mail.brevo.templates.admin-invitation-tr", "8")
                .withProperty("grun.mail.brevo.templates.admin-password-changed-en", "9")
                .withProperty("grun.mail.brevo.templates.admin-password-changed-tr", "10")
                .withProperty("grun.push.enabled", "true")
                .withProperty("grun.push.provider", "EXPO")
                .withProperty("grun.push.expo.url", "https://exp.host/--/api/v2/push/send");
    }
}

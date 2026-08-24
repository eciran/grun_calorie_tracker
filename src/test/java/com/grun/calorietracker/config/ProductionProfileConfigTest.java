package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductionProfileConfigTest {

    @Test
    void applicationProd_keepsProductionSafetyDefaults() {
        Map<String, Object> config = loadYaml("application-prod.yml");

        assertEquals(false, valueAt(config, "spring.jpa.show-sql"));
        assertEquals(false, valueAt(config, "spring.flyway.baseline-on-migrate"));
        assertEquals("never", valueAt(config, "server.error.include-message"));
        assertEquals("never", valueAt(config, "server.error.include-binding-errors"));
        assertEquals("never", valueAt(config, "server.error.include-stacktrace"));
        assertEquals(false, valueAt(config, "server.error.include-exception"));
        assertEquals(false, valueAt(config, "grun.errors.include-internal-details"));
        assertEquals(true, valueAt(config, "grun.rate-limit.enabled"));
        assertEquals(true, valueAt(config, "grun.rate-limit.redis.enabled"));
        assertEquals("${SPRING_DATA_REDIS_SSL_ENABLED:false}", valueAt(config, "spring.data.redis.ssl.enabled"));
        assertEquals("${SPRING_DATA_REDIS_CLIENT_NAME:grun-api}", valueAt(config, "spring.data.redis.client-name"));
        assertEquals(true, valueAt(config, "grun.revenuecat.strict-product-mapping"));
        assertEquals("${GRUN_OWNER_BOOTSTRAP_ENABLED:false}", valueAt(config, "grun.security.owner-bootstrap.enabled"));
        assertEquals(false, valueAt(config, "grun.local.admin.bootstrap-enabled"));
        assertEquals(false, valueAt(config, "grun.local.demo-seed.enabled"));
    }

    @Test
    void applicationExample_mapsEveryBrevoTemplateEnvironmentVariable() {
        Map<String, Object> config = loadYaml("application-example.yml");

        assertEquals("${GRUN_BREVO_TEMPLATE_EMAIL_VERIFICATION_EN:0}",
                valueAt(config, "grun.mail.brevo.templates.email-verification-en"));
        assertEquals("${GRUN_BREVO_TEMPLATE_EMAIL_VERIFICATION_TR:0}",
                valueAt(config, "grun.mail.brevo.templates.email-verification-tr"));
        assertEquals("${GRUN_BREVO_TEMPLATE_PASSWORD_RESET_EN:0}",
                valueAt(config, "grun.mail.brevo.templates.password-reset-en"));
        assertEquals("${GRUN_BREVO_TEMPLATE_PASSWORD_RESET_TR:0}",
                valueAt(config, "grun.mail.brevo.templates.password-reset-tr"));
        assertEquals("${GRUN_BREVO_TEMPLATE_SUBSCRIPTION_FEATURE_CHANGE_EN:0}",
                valueAt(config, "grun.mail.brevo.templates.subscription-feature-change-en"));
        assertEquals("${GRUN_BREVO_TEMPLATE_SUBSCRIPTION_FEATURE_CHANGE_TR:0}",
                valueAt(config, "grun.mail.brevo.templates.subscription-feature-change-tr"));
        assertEquals("${GRUN_BREVO_TEMPLATE_ADMIN_INVITATION_EN:0}",
                valueAt(config, "grun.mail.brevo.templates.admin-invitation-en"));
        assertEquals("${GRUN_BREVO_TEMPLATE_ADMIN_INVITATION_TR:0}",
                valueAt(config, "grun.mail.brevo.templates.admin-invitation-tr"));
        assertEquals("${GRUN_BREVO_TEMPLATE_ADMIN_PASSWORD_CHANGED_EN:0}",
                valueAt(config, "grun.mail.brevo.templates.admin-password-changed-en"));
        assertEquals("${GRUN_BREVO_TEMPLATE_ADMIN_PASSWORD_CHANGED_TR:0}",
                valueAt(config, "grun.mail.brevo.templates.admin-password-changed-tr"));
    }

    private Map<String, Object> loadYaml(String resourceName) {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        assertNotNull(stream, () -> "Missing resource: " + resourceName);
        return new Yaml().load(stream);
    }

    @SuppressWarnings("unchecked")
    private Object valueAt(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }
}

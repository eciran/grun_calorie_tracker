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
        assertEquals(true, valueAt(config, "grun.revenuecat.strict-product-mapping"));
        assertEquals(false, valueAt(config, "grun.local.admin.bootstrap-enabled"));
        assertEquals(false, valueAt(config, "grun.local.demo-seed.enabled"));
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

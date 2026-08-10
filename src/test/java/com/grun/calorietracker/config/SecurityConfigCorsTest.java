package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void allowsControlledProductionAndStagingOrigins() {
        assertThat(SecurityConfig.allowedCorsOriginPatterns())
                .contains(
                        "https://gruncalorietracker.com",
                        "https://www.gruncalorietracker.com",
                        "https://api.gruncalorietracker.com",
                        "https://api-staging.gruncalorietracker.com"
                )
                .doesNotContain("*");
    }
}

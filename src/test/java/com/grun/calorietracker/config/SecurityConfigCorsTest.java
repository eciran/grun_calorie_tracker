package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void deployedProfilesRejectLocalAndUntrustedOriginsEvenWithLocalProfilePresent() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local");
        CorsConfigurationSource source = new SecurityConfig(null, null, null, false, null)
                .corsConfigurationSource(environment);
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));
        assertThat(configuration.checkOrigin("http://localhost:8081")).isNull();
        assertThat(configuration.checkOrigin("http://192.168.1.1:8081")).isNull();
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
        assertThat(configuration.checkOrigin("https://gruncalorietracker.com.evil.example")).isNull();
        assertThat(configuration.checkOrigin("https://api-staging.gruncalorietracker.com"))
                .isEqualTo("https://api-staging.gruncalorietracker.com");
        environment.setActiveProfiles("staging");
        assertThat(new SecurityConfig(null, null, null, false, null)
                .corsConfigurationSource(environment)
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"))
                .checkOrigin("http://localhost:8081")).isNull();
    }

    @Test
    void localDevelopmentRetainsExpoAndBrowserOrigins() {
        MockEnvironment environment = new MockEnvironment();
        var configuration = new SecurityConfig(null, null, null, false, null)
                .corsConfigurationSource(environment)
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));
        assertThat(configuration.checkOrigin("http://localhost:8081")).isEqualTo("http://localhost:8081");
        assertThat(configuration.checkOrigin("http://192.168.1.2:8081")).isEqualTo("http://192.168.1.2:8081");
    }

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

    @Test
    void allowsFreshMfaProofHeaderForCrossOriginAdminClients() {
        assertThat(SecurityConfig.allowedCorsHeaders())
                .contains("Authorization", "Content-Type", "X-Admin-Reauth-Token")
                .doesNotContain("*");
    }
}

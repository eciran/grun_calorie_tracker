package com.grun.calorietracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetLandingControllerTest {

    private final PasswordResetLandingController controller = new PasswordResetLandingController();

    @Test
    void landingPageIsHtmlAndPreventsTokenCachingOrReferrerLeakage() throws Exception {
        ResponseEntity<Resource> response = controller.resetPasswordLandingPage();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/html");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exists()).isTrue();
        assertThat(response.getBody().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("window.location.replace('grun://reset-password?token='")
                .doesNotContain("Open GRUN", "GRUN’da aç");
    }

    @Test
    void emailLogoUsesHighResolutionBundledAsset() throws Exception {
        ResponseEntity<Resource> response = controller.emailLogo();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isGreaterThan(40_000);
    }
}

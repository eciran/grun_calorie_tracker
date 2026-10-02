package com.grun.calorietracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetLandingControllerTest {

    private final AppLinkController controller = new AppLinkController(
            "TEAM123.com.grun.calorietracker",
            "com.grun.calorietracker",
            "AA:BB:CC,DD:EE:FF");

    @Test
    void landingPageIsHtmlAndPreventsTokenCachingOrReferrerLeakage() throws Exception {
        ResponseEntity<Resource> response = controller.tokenActionLandingPage();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/html");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exists()).isTrue();
        assertThat(response.getBody().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("'/verify-email':'verify-email'", "'/reset-password':'reset-password'")
                .contains("'/open/plans':'pro-upgrade'")
                .contains("/api/v1/auth/email-verification/confirm")
                .contains("/api/v1/auth/password-reset/confirm")
                .contains("window.history.replaceState")
                .doesNotContain("localStorage", "sessionStorage");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("connect-src 'self'");
    }

    @Test
    void associationDocumentsContainOnlyConfiguredApplicationIdentity() {
        assertThat(controller.appleAppSiteAssociation().getBody().toString())
                .contains("TEAM123.com.grun.calorietracker", "/verify-email*", "/open/*");
        assertThat(controller.androidAssetLinks().getBody().toString())
                .contains("com.grun.calorietracker", "AA:BB:CC", "DD:EE:FF");
    }

    @Test
    void unknownPublicDestinationIsRejected() {
        assertThat(controller.appDestinationLandingPage("arbitrary").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void emailLogoUsesHighResolutionBundledAsset() throws Exception {
        ResponseEntity<Resource> response = new PasswordResetLandingController().emailLogo();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isGreaterThan(40_000);
    }
}

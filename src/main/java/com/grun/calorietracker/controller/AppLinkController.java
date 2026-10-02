package com.grun.calorietracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
public class AppLinkController {

    private static final List<String> PUBLIC_DESTINATIONS = List.of("plans", "notifications", "profile");

    private final String iosAppId;
    private final String androidPackageName;
    private final List<String> androidCertificateFingerprints;

    public AppLinkController(
            @Value("${grun.app-links.ios-app-id:}") String iosAppId,
            @Value("${grun.app-links.android-package-name:com.grun.calorietracker}") String androidPackageName,
            @Value("${grun.app-links.android-sha256-cert-fingerprints:}") String androidCertificateFingerprints) {
        this.iosAppId = iosAppId.trim();
        this.androidPackageName = androidPackageName.trim();
        this.androidCertificateFingerprints = Arrays.stream(androidCertificateFingerprints.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @GetMapping(value = {"/reset-password", "/verify-email"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> tokenActionLandingPage() {
        return landingPage();
    }

    @GetMapping(value = "/open/{destination}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> appDestinationLandingPage(@PathVariable String destination) {
        if (!PUBLIC_DESTINATIONS.contains(destination)) {
            return ResponseEntity.notFound().build();
        }
        return landingPage();
    }

    @ResponseBody
    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        List<Map<String, Object>> details = iosAppId.isBlank()
                ? List.of()
                : List.of(Map.of("appID", iosAppId,
                        "paths", List.of("/verify-email*", "/reset-password*", "/open/*")));
        return associationResponse(Map.of("applinks", Map.of("apps", List.of(), "details", details)));
    }

    @ResponseBody
    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> androidAssetLinks() {
        if (androidPackageName.isBlank() || androidCertificateFingerprints.isEmpty()) {
            return associationResponse(List.of());
        }
        return associationResponse(List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of("namespace", "android_app", "package_name", androidPackageName,
                        "sha256_cert_fingerprints", androidCertificateFingerprints))));
    }

    private ResponseEntity<Resource> landingPage() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                                + "connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/app-action.html"));
    }

    private <T> ResponseEntity<T> associationResponse(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}

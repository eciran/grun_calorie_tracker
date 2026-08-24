package com.grun.calorietracker.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PasswordResetLandingController {

    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> resetPasswordLandingPage() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                                + "img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/reset-password.html"));
    }

    @GetMapping(value = "/email-assets/grun-logo.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> emailLogo() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic().immutable())
                .contentType(MediaType.IMAGE_PNG)
                .body(new ClassPathResource("static/email-assets/grun-logo-192.png"));
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.CatalogMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media/catalog")
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class CatalogMediaController {
    private final CatalogMediaService catalogMediaService;

    @GetMapping("/{token}")
    public ResponseEntity<Resource> get(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(mediaType(token))
                .body(catalogMediaService.load(token));
    }

    private MediaType mediaType(String token) {
        String lower = token == null ? "" : token.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}

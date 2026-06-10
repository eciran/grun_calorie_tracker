package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.service.RecipeImageModerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.util.Locale;

@Service
public class RecipeImageModerationServiceImpl implements RecipeImageModerationService {

    private static final int MAX_IMAGE_URL_LENGTH = 2048;

    private final AiProperties properties;
    private final RestOperations restOperations;

    @Autowired
    public RecipeImageModerationServiceImpl(AiProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(properties.getRecipeImageModeration().getTimeout())
                .setReadTimeout(properties.getRecipeImageModeration().getTimeout())
                .build());
    }

    public RecipeImageModerationServiceImpl(AiProperties properties, RestOperations restOperations) {
        this.properties = properties;
        this.restOperations = restOperations;
    }

    @Override
    public Result moderate(String imageUrl, ImageSource source) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return new Result(null, null);
        }
        if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            return rejected("Image URL is too long.");
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException ex) {
            return rejected("Image URL is not valid.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return rejected("Recipe images must use HTTPS.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".jpg") && !path.endsWith(".jpeg") && !path.endsWith(".png") && !path.endsWith(".webp")) {
            return rejected("Recipe image must be jpg, png, or webp.");
        }
        if (source == ImageSource.BRAND_OFFICIAL || source == ImageSource.ADMIN_UPLOAD) {
            return new Result(ImageStatus.APPROVED, "Trusted image source passed automatic URL moderation.");
        }
        if (providerEnabled()) {
            return moderateWithProvider(imageUrl, source);
        }
        return new Result(ImageStatus.NEEDS_REVIEW, "Image passed automatic URL moderation and requires visual review.");
    }

    private Result rejected(String note) {
        return new Result(ImageStatus.REJECTED, note);
    }

    private boolean providerEnabled() {
        AiProperties.RecipeImageModeration moderation = properties.getRecipeImageModeration();
        return moderation != null
                && moderation.isEnabled()
                && moderation.getEndpoint() != null
                && !moderation.getEndpoint().isBlank()
                && moderation.getApiKey() != null
                && !moderation.getApiKey().isBlank();
    }

    private Result moderateWithProvider(String imageUrl, ImageSource source) {
        AiProperties.RecipeImageModeration moderation = properties.getRecipeImageModeration();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(moderation.getApiKey());
        headers.set("X-GRun-AI-Use-Case", "RECIPE_IMAGE_MODERATION");
        try {
            ProviderResponse response = restOperations.postForObject(
                    moderation.getEndpoint(),
                    new HttpEntity<>(new ProviderRequest(imageUrl, source), headers),
                    ProviderResponse.class
            );
            if (response == null) {
                return new Result(ImageStatus.NEEDS_REVIEW, "Image moderation provider returned an empty response.");
            }
            return toResult(response, moderation);
        } catch (RestClientException ex) {
            return new Result(ImageStatus.NEEDS_REVIEW, "Image moderation provider request failed.");
        }
    }

    private Result toResult(ProviderResponse response, AiProperties.RecipeImageModeration moderation) {
        double confidence = response.confidence() == null ? 0.0 : response.confidence();
        String reason = response.reason() == null || response.reason().isBlank()
                ? "Provider visual moderation completed."
                : response.reason().trim();
        if (Boolean.TRUE.equals(response.rejected()) && confidence >= moderation.getRejectThreshold()) {
            return new Result(ImageStatus.REJECTED, reason);
        }
        if (Boolean.TRUE.equals(response.approved()) && confidence >= moderation.getApproveThreshold()) {
            return new Result(ImageStatus.APPROVED, reason);
        }
        return new Result(ImageStatus.NEEDS_REVIEW, reason);
    }

    private record ProviderRequest(String imageUrl, ImageSource source) {
    }

    public record ProviderResponse(Boolean approved, Boolean rejected, Double confidence, String reason, String category) {
    }
}

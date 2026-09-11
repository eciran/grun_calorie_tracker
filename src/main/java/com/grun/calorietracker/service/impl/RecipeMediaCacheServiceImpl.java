package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.MediaStorageProperties;
import com.grun.calorietracker.config.RecipeMediaProperties;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.service.RecipeMediaCacheService;
import com.grun.calorietracker.service.media.MediaNamespace;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RecipeMediaCacheServiceImpl implements RecipeMediaCacheService {
    private static final String PUBLIC_MARKER = "/api/v1/media/recipes/";
    private static final String UPLOAD_MARKER = "/api/v1/recipes/images/";
    private static final String CACHE_USER_AGENT = "Mozilla/5.0 (compatible; GRUNRecipeImageCache/1.0)";
    private static final int MAX_REDIRECTS = 3;

    private final MediaObjectStorage mediaStorage;
    private final MediaStorageKeyPolicy keyPolicy;
    private final MediaStorageProperties mediaProperties;
    private final RecipeMediaProperties recipeMediaProperties;

    @Override
    public boolean cacheApprovedImage(RecipeEntity recipe) {
        String sourceUrl = recipe == null ? null : recipe.getImageUrl();
        if (sourceUrl == null || sourceUrl.isBlank() || sourceUrl.contains(PUBLIC_MARKER) || sourceUrl.contains(UPLOAD_MARKER)) {
            return false;
        }
        URI uri = requirePublicHttpsUrl(sourceUrl);
        DownloadedImage image = download(uri);
        String owner = "r" + recipe.getId();
        String storageKey = keyPolicy.create(MediaNamespace.RECIPE_MEDIA, owner, extension(image.contentType()));
        mediaStorage.store(storageKey, image.content(), image.contentType(), sha256(image.content()));
        recipe.setImageUrl(publicUrl(publicToken(storageKey, owner)));
        return true;
    }

    @Override
    public Resource load(String publicToken) {
        String storageKey = storageKeyFromToken(publicToken);
        mediaStorage.inspect(storageKey);
        return new ByteArrayResource(mediaStorage.readBounded(storageKey, maximumBytes()));
    }

    private DownloadedImage download(URI initialUri) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            URI uri = initialUri;
            for (int redirects = 0; ; redirects++) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "image/jpeg,image/png,image/webp")
                        .header("User-Agent", CACHE_USER_AGENT)
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (isRedirect(response.statusCode())) {
                    try (InputStream ignored = response.body()) {
                        if (redirects >= MAX_REDIRECTS) {
                            throw new IllegalArgumentException("Recipe image redirect limit was exceeded.");
                        }
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new IllegalArgumentException("Recipe image redirect has no location."));
                        uri = requirePublicHttpsUrl(uri.resolve(location).toString());
                        continue;
                    }
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    try (InputStream ignored = response.body()) {
                        throw new IllegalArgumentException("Recipe image could not be cached (HTTP " + response.statusCode() + ").");
                    }
                }
                String contentType = response.headers().firstValue("content-type").orElse("").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                extension(contentType);
                try (InputStream input = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > maximumBytes()) throw new IllegalArgumentException("Recipe image exceeds the configured cache limit.");
                        output.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IllegalArgumentException("Recipe image response was empty.");
                    return new DownloadedImage(output.toByteArray(), contentType);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Recipe image could not be cached.", exception);
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private URI requirePublicHttpsUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Recipe cache source must be a public HTTPS URL.");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Recipe cache source host is not public.");
                }
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Recipe cache source URL is invalid.", exception);
        }
    }

    private String publicUrl(String token) {
        return mediaProperties.getPublicBaseUrl().replaceAll("/+$", "") + PUBLIC_MARKER + token;
    }

    private String publicToken(String storageKey, String owner) {
        String prefix = keyPolicy.prefix(MediaNamespace.RECIPE_MEDIA) + "/" + owner + "/";
        if (!storageKey.startsWith(prefix)) throw new IllegalArgumentException("Recipe media storage key is invalid.");
        return owner + "~" + storageKey.substring(prefix.length());
    }

    private String storageKeyFromToken(String token) {
        if (token == null || !token.matches("r\\d+~[a-fA-F0-9-]{36}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Recipe media token is invalid.");
        }
        int separator = token.indexOf('~');
        String owner = token.substring(0, separator);
        return keyPolicy.requireManaged(keyPolicy.prefix(MediaNamespace.RECIPE_MEDIA) + "/" + owner + "/" + token.substring(separator + 1));
    }

    private long maximumBytes() { return recipeMediaProperties.getImage().getMaxUploadBytes(); }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException("Recipe cache image content type is not allowed.");
        };
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("Recipe image checksum could not be calculated.", exception); }
    }

    private record DownloadedImage(byte[] content, String contentType) {}
}

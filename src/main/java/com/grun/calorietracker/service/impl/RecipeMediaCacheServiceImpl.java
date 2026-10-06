package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.MediaStorageProperties;
import com.grun.calorietracker.config.RecipeMediaProperties;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.service.RecipeMediaCacheService;
import com.grun.calorietracker.service.media.MediaNamespace;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import com.grun.calorietracker.service.media.PublicImageDnsResolver;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
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
    private final FoodProductEvidenceImageInspector imageInspector;

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
            var connections = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(new PublicImageDnsResolver(SystemDefaultDnsResolver.INSTANCE)).build();
            try (var client = HttpClients.custom().setConnectionManager(connections)
                    .disableRedirectHandling().disableAutomaticRetries().disableContentCompression()
                    .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(8))
                            .setConnectionRequestTimeout(Timeout.ofSeconds(8)).setResponseTimeout(Timeout.ofSeconds(15)).build())
                    .build()) {
                URI uri = initialUri;
                for (int redirects = 0; ; redirects++) {
                    HttpGet request = new HttpGet(uri);
                    request.setHeader("Accept", "image/jpeg,image/png,image/webp");
                    request.setHeader("User-Agent", CACHE_USER_AGENT);
                    var response = client.execute(request);
                    try {
                        if (isRedirect(response.getCode())) {
                            if (redirects >= MAX_REDIRECTS) {
                                throw new IllegalArgumentException("Recipe image redirect limit was exceeded.");
                            }
                            if (response.getFirstHeader("location") == null) {
                                throw new IllegalArgumentException("Recipe image redirect has no location.");
                            }
                            String location = response.getFirstHeader("location").getValue();
                            uri = requirePublicHttpsUrl(uri.resolve(location).toString());
                            continue;
                        }
                        if (response.getCode() < 200 || response.getCode() >= 300 || response.getEntity() == null) {
                            throw new IllegalArgumentException("Recipe image could not be cached (HTTP " + response.getCode() + ").");
                        }
                        String contentType = response.getEntity().getContentType();
                        contentType = (contentType == null ? "" : contentType).split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                        extension(contentType);
                        // Abort the response below instead of draining an oversized or hostile stream on close.
                        InputStream input = response.getEntity().getContent();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (System.nanoTime() > deadline) throw new IllegalArgumentException("Recipe image download timed out.");
                            total += count;
                            if (total > maximumBytes()) throw new IllegalArgumentException("Recipe image exceeds the configured cache limit.");
                            output.write(buffer, 0, count);
                        }
                        if (total == 0) throw new IllegalArgumentException("Recipe image response was empty.");
                        byte[] content = output.toByteArray();
                        imageInspector.inspectImage(content, contentType, maximumBytes(), recipeMediaProperties.getImage().getMaxDecodedPixels());
                        return new DownloadedImage(content, contentType);
                    } finally {
                        response.close(CloseMode.IMMEDIATE);
                    }
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
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) {
                throw new IllegalArgumentException("Recipe cache source must be a public HTTPS URL.");
            }
            new PublicImageDnsResolver(SystemDefaultDnsResolver.INSTANCE).resolve(uri.getHost());
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

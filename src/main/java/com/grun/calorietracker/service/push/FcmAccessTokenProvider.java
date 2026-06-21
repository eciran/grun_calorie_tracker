package com.grun.calorietracker.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.PushProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class FcmAccessTokenProvider {

    private static final String FIREBASE_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final long TOKEN_REFRESH_SKEW_SECONDS = 60;

    private final PushProperties pushProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private String cachedAccessToken;
    private Instant cachedAccessTokenExpiresAt;

    public FcmAccessTokenProvider(PushProperties pushProperties,
                                  ObjectMapper objectMapper,
                                  RestTemplateBuilder restTemplateBuilder) {
        this.pushProperties = pushProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    public synchronized String getAccessToken() {
        String credentialsJson = pushProperties.getFcm().getCredentialsJson();
        if (isBlank(credentialsJson)) {
            return pushProperties.getFcm().getAccessToken();
        }
        if (!isBlank(cachedAccessToken)
                && cachedAccessTokenExpiresAt != null
                && Instant.now().isBefore(cachedAccessTokenExpiresAt.minusSeconds(TOKEN_REFRESH_SKEW_SECONDS))) {
            return cachedAccessToken;
        }

        try {
            JsonNode credentials = objectMapper.readTree(normalizeCredentialsJson(credentialsJson));
            String clientEmail = requiredText(credentials, "client_email");
            String privateKeyPem = requiredText(credentials, "private_key");
            String tokenUri = textOrDefault(credentials, "token_uri", DEFAULT_TOKEN_URI);

            Instant now = Instant.now();
            String assertion = Jwts.builder()
                    .setIssuer(clientEmail)
                    .setSubject(clientEmail)
                    .setAudience(tokenUri)
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(now.plusSeconds(3600)))
                    .claim("scope", FIREBASE_MESSAGING_SCOPE)
                    .signWith(parsePrivateKey(privateKeyPem), SignatureAlgorithm.RS256)
                    .compact();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", JWT_BEARER_GRANT_TYPE);
            body.add("assertion", assertion);

            JsonNode response = restTemplate.postForObject(tokenUri, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null || response.path("access_token").asText().isBlank()) {
                throw new IllegalStateException("Google OAuth token response did not include access_token.");
            }
            long expiresIn = response.path("expires_in").asLong(3600);
            cachedAccessToken = response.path("access_token").asText();
            cachedAccessTokenExpiresAt = now.plusSeconds(expiresIn);
            return cachedAccessToken;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create FCM access token from service account credentials.", ex);
        }
    }

    private String normalizeCredentialsJson(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        byte[] decoded = Base64.getDecoder().decode(trimmed);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String normalized = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (isBlank(value)) {
            throw new IllegalStateException("FCM service account credentials missing required field: " + fieldName);
        }
        return value;
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = node.path(fieldName).asText();
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

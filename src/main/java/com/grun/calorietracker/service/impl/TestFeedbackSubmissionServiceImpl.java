package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.TestFeedbackProperties;
import com.grun.calorietracker.dto.TestFeedbackCreateRequestDto;
import com.grun.calorietracker.dto.TestFeedbackSubmissionDto;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.RequestRateLimiter;
import com.grun.calorietracker.service.TestFeedbackSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TestFeedbackSubmissionServiceImpl implements TestFeedbackSubmissionService {

    private static final long ONE_MINUTE_MILLIS = 60_000L;
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-z0-9._~-]+(?:\\.[a-z0-9._~-]+){0,2}");
    private static final Pattern JWT_TOKEN = Pattern.compile("\\beyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private final TestFeedbackProperties properties;
    private final TestFeedbackSubmissionRepository repository;
    private final UserRepository userRepository;
    private final RequestRateLimiter rateLimiter;

    @Override
    @Transactional
    public TestFeedbackSubmissionDto submit(String userEmail, String environment, String idempotencyKey,
                                            TestFeedbackCreateRequestDto request) {
        if (!properties.isEnabledFor(environment)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        return repository.findByUserIdAndIdempotencyKey(user.getId(), normalizedKey)
                .map(existing -> toDto(existing, true))
                .orElseGet(() -> create(user, normalizedKey, request));
    }

    private TestFeedbackSubmissionDto create(UserEntity user, String idempotencyKey,
                                             TestFeedbackCreateRequestDto request) {
        String rateLimitKey = "test-feedback:user:" + user.getId();
        if (!rateLimiter.isAllowed(rateLimitKey, properties.getMaxSubmissionsPerMinute(), ONE_MINUTE_MILLIS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Feedback submission limit exceeded");
        }

        TestFeedbackSubmissionEntity entity = new TestFeedbackSubmissionEntity();
        entity.setUser(user);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setFeedbackType(request.feedbackType());
        entity.setPlatform(request.platform());
        entity.setRoute(sanitize(request.route(), 240));
        entity.setPreviousRoute(sanitize(request.previousRoute(), 240));
        entity.setDescription(sanitize(request.description(), properties.getMaxDescriptionLength()));
        entity.setAppVersion(sanitize(request.appVersion(), 40));
        entity.setBuildNumber(sanitize(request.buildNumber(), 40));
        entity.setEasBuildId(sanitize(request.easBuildId(), 100));
        entity.setCommitSha(normalize(request.commitSha()));
        entity.setOsVersion(sanitize(request.osVersion(), 80));
        entity.setDeviceModel(sanitize(request.deviceModel(), 120));
        entity.setLanguageTag(normalize(request.languageTag()));
        entity.setMarketRegion(normalize(request.marketRegion()));
        entity.setLastHttpStatus(request.lastHttpStatus());
        entity.setLastHttpDurationMs(request.lastHttpDurationMs());
        entity.setLastCorrelationId(sanitize(request.lastCorrelationId(), 100));
        entity.setNetworkState(normalize(request.networkState()));
        return toDto(repository.save(entity), false);
    }

    private String requireIdempotencyKey(String value) {
        String sanitized = sanitize(value, 100);
        if (sanitized == null || sanitized.length() < 8) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 8 and 100 characters.");
        }
        return sanitized;
    }

    private String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = CONTROL_CHARACTERS.matcher(value.trim()).replaceAll("");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        sanitized = JWT_TOKEN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        return sanitized.substring(0, Math.min(sanitized.length(), maxLength));
    }

    private String normalize(String value) {
        String sanitized = sanitize(value, 100);
        return sanitized == null ? null : sanitized.toUpperCase(Locale.ROOT);
    }

    private TestFeedbackSubmissionDto toDto(TestFeedbackSubmissionEntity entity, boolean duplicate) {
        return new TestFeedbackSubmissionDto(entity.getId(), entity.getFeedbackType(), entity.getStatus(),
                entity.getPlatform(), entity.getRoute(), entity.getCreatedAt(), duplicate);
    }
}

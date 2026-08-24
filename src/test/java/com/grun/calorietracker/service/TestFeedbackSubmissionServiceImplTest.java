package com.grun.calorietracker.service;

import com.grun.calorietracker.config.TestFeedbackProperties;
import com.grun.calorietracker.dto.TestFeedbackCreateRequestDto;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackType;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.RequestRateLimiter;
import com.grun.calorietracker.service.impl.TestFeedbackSubmissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestFeedbackSubmissionServiceImplTest {
    @Mock TestFeedbackSubmissionRepository repository;
    @Mock UserRepository userRepository;
    @Mock RequestRateLimiter rateLimiter;
    private TestFeedbackProperties properties;
    private TestFeedbackSubmissionServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new TestFeedbackProperties();
        properties.setEnabled(true);
        service = new TestFeedbackSubmissionServiceImpl(properties, repository, userRepository, rateLimiter);
        user = new UserEntity();
        user.setId(42L);
        user.setEmail("tester@example.com");
    }

    @Test
    void rejectsProductionEnvironmentWithoutDatabaseAccess() {
        assertThatThrownBy(() -> service.submit(user.getEmail(), "production", "feedback-123", request("fine")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verifyNoInteractions(repository, userRepository, rateLimiter);
    }

    @Test
    void returnsExistingSubmissionBeforeConsumingRateLimit() {
        TestFeedbackSubmissionEntity existing = new TestFeedbackSubmissionEntity();
        existing.setId(7L);
        existing.setUser(user);
        existing.setFeedbackType(TestFeedbackType.WORKS_WELL);
        existing.setPlatform(TestFeedbackPlatform.ANDROID);
        existing.setRoute("/home");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.findByUserIdAndIdempotencyKey(42L, "feedback-123")).thenReturn(Optional.of(existing));

        assertThat(service.submit(user.getEmail(), "preview", "feedback-123", request("fine")).duplicate()).isTrue();
        verifyNoInteractions(rateLimiter);
        verify(repository, never()).save(any());
    }

    @Test
    void redactsTokensAndPersistsOnlySafeContext() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.findByUserIdAndIdempotencyKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(rateLimiter.isAllowed(anyString(), eq(6), eq(60_000L))).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> {
            TestFeedbackSubmissionEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return entity;
        });

        service.submit(user.getEmail(), "internal", "feedback-123", request("Bearer abc.def.ghi"));

        ArgumentCaptor<TestFeedbackSubmissionEntity> captor = ArgumentCaptor.forClass(TestFeedbackSubmissionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("[REDACTED_TOKEN]");
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    private TestFeedbackCreateRequestDto request(String description) {
        return new TestFeedbackCreateRequestDto(TestFeedbackType.WORKS_WELL, TestFeedbackPlatform.ANDROID,
                "/home", "/login", description, "1.0.0", "12", null, "abcdef1", "14", "Pixel",
                "en-IE", "UK_IE", 200, 120L, "cid-1", "WIFI");
    }
}

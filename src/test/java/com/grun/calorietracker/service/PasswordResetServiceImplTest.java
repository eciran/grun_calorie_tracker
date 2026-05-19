package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetMailSender passwordResetMailSender;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordResetServiceImpl passwordResetService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetServiceImpl(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                passwordResetMailSender,
                refreshTokenService
        );
        ReflectionTestUtils.setField(passwordResetService, "expirationMinutes", 30L);
        ReflectionTestUtils.setField(passwordResetService, "resetBaseUrl", "http://localhost:8080/reset-password");

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("old-password");
    }

    @Test
    void requestPasswordReset_whenUserExists_createsHashedTokenAndSendsMail() {
        PasswordResetRequestDto request = new PasswordResetRequestDto();
        request.setEmail("user@example.com");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        PasswordResetResponseDto response = passwordResetService.requestPasswordReset(request);

        ArgumentCaptor<PasswordResetTokenEntity> tokenCaptor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetTokenEntity savedToken = tokenCaptor.getValue();

        assertThat(response.getMessage()).isEqualTo("If the email exists, a password reset link has been sent.");
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getTokenHash()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(passwordResetMailSender).sendPasswordResetToken(anyString(), anyString(), anyString());
    }

    @Test
    void requestPasswordReset_whenUserDoesNotExist_returnsGenericResponseWithoutMail() {
        PasswordResetRequestDto request = new PasswordResetRequestDto();
        request.setEmail("missing@example.com");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        PasswordResetResponseDto response = passwordResetService.requestPasswordReset(request);

        assertThat(response.getMessage()).isEqualTo("If the email exists, a password reset link has been sent.");
        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetMailSender, never()).sendPasswordResetToken(anyString(), anyString(), anyString());
    }

    @Test
    void confirmPasswordReset_whenTokenIsValid_updatesPasswordAndMarksTokenUsed() {
        PasswordResetConfirmRequestDto request = new PasswordResetConfirmRequestDto();
        request.setToken("raw-token");
        request.setNewPassword("NewStrongPass1!");

        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("encoded-new-password");

        PasswordResetResponseDto response = passwordResetService.confirmPasswordReset(request);

        assertThat(response.getMessage()).isEqualTo("Password has been reset successfully.");
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void confirmPasswordReset_whenTokenExpired_marksTokenUsedAndThrows() {
        PasswordResetConfirmRequestDto request = new PasswordResetConfirmRequestDto();
        request.setToken("raw-token");
        request.setNewPassword("NewStrongPass1!");

        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password reset token is invalid or expired");

        assertThat(token.getUsedAt()).isNotNull();
        verify(passwordResetTokenRepository).save(token);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }
}

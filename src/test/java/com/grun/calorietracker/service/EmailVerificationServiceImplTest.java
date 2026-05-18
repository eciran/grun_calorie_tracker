package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.entity.EmailVerificationTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.EmailVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class EmailVerificationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private EmailVerificationMailSender emailVerificationMailSender;

    private EmailVerificationServiceImpl emailVerificationService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationServiceImpl(
                userRepository,
                emailVerificationTokenRepository,
                emailVerificationMailSender
        );
        ReflectionTestUtils.setField(emailVerificationService, "expirationMinutes", 1440L);
        ReflectionTestUtils.setField(emailVerificationService, "verificationBaseUrl", "http://localhost:8080/verify-email");

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setEmailVerified(false);
    }

    @Test
    void createVerificationTokenForUser_whenUserIsUnverified_savesTokenAndSendsMail() {
        when(emailVerificationTokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        emailVerificationService.createVerificationTokenForUser(user);

        ArgumentCaptor<EmailVerificationTokenEntity> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUser()).isEqualTo(user);
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotBlank();
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailVerificationMailSender).sendEmailVerificationToken(anyString(), anyString(), anyString());
    }

    @Test
    void createVerificationTokenForUser_whenUserAlreadyVerified_doesNothing() {
        user.setEmailVerified(true);

        emailVerificationService.createVerificationTokenForUser(user);

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailVerificationMailSender, never()).sendEmailVerificationToken(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerification_whenUserMissing_returnsGenericResponseWithoutMail() {
        EmailVerificationRequestDto request = new EmailVerificationRequestDto();
        request.setEmail("missing@example.com");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        EmailVerificationResponseDto response = emailVerificationService.resendVerification(request);

        assertThat(response.getMessage()).isEqualTo("If the email exists, a verification link has been sent.");
        verify(emailVerificationMailSender, never()).sendEmailVerificationToken(anyString(), anyString(), anyString());
    }

    @Test
    void confirmVerification_whenTokenIsValid_marksEmailVerifiedAndTokenUsed() {
        EmailVerificationConfirmRequestDto request = new EmailVerificationConfirmRequestDto();
        request.setToken("raw-token");

        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(anyString())).thenReturn(Optional.of(token));

        EmailVerificationResponseDto response = emailVerificationService.confirmVerification(request);

        assertThat(response.getMessage()).isEqualTo("Email has been verified successfully.");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    void confirmVerification_whenTokenExpired_marksTokenUsedAndThrows() {
        EmailVerificationConfirmRequestDto request = new EmailVerificationConfirmRequestDto();
        request.setToken("raw-token");

        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.confirmVerification(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email verification token is invalid or expired");

        assertThat(token.getUsedAt()).isNotNull();
        verify(emailVerificationTokenRepository).save(token);
        verify(userRepository, never()).save(any());
    }
}

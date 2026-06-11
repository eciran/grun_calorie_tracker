package com.grun.calorietracker.service;

import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.service.impl.TokenCleanupServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenCleanupServiceImplTest {

    @Test
    void cleanupExpiredTokens_deletesExpiredTokensFromAllTokenTables() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        EmailVerificationTokenRepository emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        TokenCleanupServiceImpl service = new TokenCleanupServiceImpl(
                refreshTokenRepository,
                passwordResetTokenRepository,
                emailVerificationTokenRepository
        );

        when(refreshTokenRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(3L);
        when(passwordResetTokenRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(2L);
        when(emailVerificationTokenRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(4L);

        TokenCleanupServiceImpl.TokenCleanupResult result = service.cleanupExpiredTokens();

        assertThat(result.refreshTokens()).isEqualTo(3L);
        assertThat(result.passwordResetTokens()).isEqualTo(2L);
        assertThat(result.emailVerificationTokens()).isEqualTo(4L);
        assertThat(result.totalDeleted()).isEqualTo(9L);
        verify(refreshTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(emailVerificationTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}

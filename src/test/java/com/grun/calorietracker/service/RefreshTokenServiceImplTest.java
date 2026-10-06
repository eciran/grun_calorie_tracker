package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.impl.RefreshTokenServiceImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserActivityService userActivityService;

    private RefreshTokenServiceImpl refreshTokenService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, userRepository, jwtUtil, userActivityService);
        ReflectionTestUtils.setField(refreshTokenService, "expirationDays", 30L);
        org.mockito.Mockito.lenient().when(refreshTokenRepository.consumeIfAvailable(anyString(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setEmailVerified(true);
    }

    @Test
    void createRefreshToken_savesHashAndReturnsRawToken() {
        String rawToken = refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshTokenEntity saved = captor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void refreshAccessToken_whenTokenValid_rotatesRefreshTokenAndReturnsAccessToken() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(0L));
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));
        when(jwtUtil.generateUserToken(user)).thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(900L);

        AuthResponse response = refreshTokenService.refreshAccessToken("raw-refresh-token");

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(existing.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).consumeIfAvailable(anyString(), any(), org.mockito.ArgumentMatchers.eq(0L));
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void refreshAccessToken_whenEmailUnverified_keepsSessionActive() {
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(0L));
        user.setEmailVerified(false);
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateUserToken(user)).thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(900L);

        AuthResponse response = refreshTokenService.refreshAccessToken("raw-refresh-token");

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(existing.getUsedAt()).isNotNull();
    }
    @Test
    void refreshAccessToken_whenExpired_marksUsedAndThrows() {
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");

        assertThat(existing.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void refreshAccessToken_whenAccountDisabled_revokesTokenAndThrows() {
        user.setAccountEnabled(false);
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void refreshAccessToken_whenAccountLocked_revokesTokenAndThrows() {
        user.setAccountLocked(true);
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void refreshAccessToken_whenUsedTokenReplayed_revokesActiveTokensForUser() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.incrementAuthTokenVersion(1L)).thenReturn(1);
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(0L), Optional.of(1L));
        RefreshTokenEntity reused = new RefreshTokenEntity();
        reused.setUser(user);
        reused.setUsedAt(LocalDateTime.now().minusMinutes(1));
        reused.setExpiresAt(LocalDateTime.now().plusDays(1));
        RefreshTokenEntity active = new RefreshTokenEntity();
        active.setUser(user);
        active.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user))
                .thenReturn(List.of(active));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");

        assertThat(reused.getRevokedAt()).isNotNull();
        assertThat(active.getRevokedAt()).isNotNull();
        assertThat(user.getAuthTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).save(reused);
    }

    @Test
    void revokeAllForUser_marksAllActiveTokensRevoked() {
        when(userRepository.incrementAuthTokenVersion(1L)).thenReturn(1);
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        RefreshTokenEntity first = new RefreshTokenEntity();
        RefreshTokenEntity second = new RefreshTokenEntity();

        when(refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user))
                .thenReturn(List.of(first, second));

        refreshTokenService.revokeAllForUser(user);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
        assertThat(user.getAuthTokenVersion()).isEqualTo(1);
        verify(userRepository).incrementAuthTokenVersion(1L);
    }

    @Test
    void refreshAfterGlobalRevocationCannotMintNewVersionFromStaleToken() {
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(1L));
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        user.setAuthTokenVersion(1);
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("stale"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(token.getRevokedAt()).isNotNull();
        org.mockito.Mockito.verifyNoInteractions(jwtUtil);
    }

    @Test
    void refreshTokenCapturesIssuingVersion() {
        user.setAuthTokenVersion(7);
        refreshTokenService.createRefreshToken(user);
        verify(refreshTokenRepository).save(org.mockito.ArgumentMatchers.argThat(token -> token.getAuthTokenVersion() == 7));
    }

    @Test
    void singleDeviceLogoutDoesNotInvalidateOtherAccessTokens() {
        when(refreshTokenRepository.revokeIfAvailable(anyString(), any())).thenReturn(1);
        refreshTokenService.revokeRefreshToken("logout");
        assertThat(user.getAuthTokenVersion()).isZero();
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void concurrentConsumptionLoserCannotMintTokens() {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(0L));
        when(refreshTokenRepository.consumeIfAvailable(anyString(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(0);
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("race"))
                .isInstanceOf(com.grun.calorietracker.exception.RequestConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtUtil);
        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void recentReplayRejectsWithoutRevokingTheWinningSession() {
        RefreshTokenEntity used = new RefreshTokenEntity();
        used.setUser(user);
        used.setUsedAt(LocalDateTime.now());
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(0L));
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("recent"))
                .isInstanceOf(com.grun.calorietracker.exception.RequestConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtUtil);
        verify(userRepository, org.mockito.Mockito.never()).incrementAuthTokenVersion(any());
        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void replayFromRetiredGenerationCannotRevokeFreshSessions() {
        RefreshTokenEntity retired = new RefreshTokenEntity();
        retired.setUser(user);
        retired.setUsedAt(LocalDateTime.now().minusMinutes(1));
        user.setAuthTokenVersion(1);
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(retired));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.findAuthTokenVersionById(1L)).thenReturn(Optional.of(1L));
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("retired"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, org.mockito.Mockito.never()).incrementAuthTokenVersion(any());
        verify(refreshTokenRepository, org.mockito.Mockito.never()).findByUserAndRevokedAtIsNullAndUsedAtIsNull(any());
        assertThat(retired.getRevokedAt()).isNotNull();
    }
}

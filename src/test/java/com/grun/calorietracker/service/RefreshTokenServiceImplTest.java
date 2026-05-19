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

    private RefreshTokenServiceImpl refreshTokenService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, userRepository, jwtUtil);
        ReflectionTestUtils.setField(refreshTokenService, "expirationDays", 30L);

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
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("user@example.com")).thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(900L);

        AuthResponse response = refreshTokenService.refreshAccessToken("raw-refresh-token");

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(existing.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
        verify(refreshTokenRepository, times(2)).save(any(RefreshTokenEntity.class));
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
    void revokeAllForUser_marksAllActiveTokensRevoked() {
        RefreshTokenEntity first = new RefreshTokenEntity();
        RefreshTokenEntity second = new RefreshTokenEntity();

        when(refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user))
                .thenReturn(List.of(first, second));

        refreshTokenService.revokeAllForUser(user);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
    }
}

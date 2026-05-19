package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${grun.refresh-token.expiration-days:30}")
    private long expirationDays;

    @Override
    @Transactional
    public String createRefreshToken(UserEntity user) {
        String rawToken = generateRawToken();

        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        refreshTokenRepository.save(token);

        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthResponse refreshAccessToken(String rawRefreshToken) {
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(hashToken(rawRefreshToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }

        UserEntity user = token.getUser();
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            throw new IllegalArgumentException("Email address is not verified");
        }

        token.setUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        UserEntity managedUser = userRepository.findById(user.getId()).orElse(user);
        String newRefreshToken = createRefreshToken(managedUser);
        String accessToken = jwtUtil.generateToken(managedUser.getEmail());

        return new AuthResponse(accessToken, newRefreshToken, "Bearer", jwtUtil.getExpirationSeconds(), "Token refreshed successfully");
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void revokeRefreshToken(String rawRefreshToken) {
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(hashToken(rawRefreshToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));

        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void revokeAllForUser(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)
                .forEach(token -> token.setRevokedAt(now));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 hashing is not available", e);
        }
    }
}

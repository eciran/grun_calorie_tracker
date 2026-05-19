package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.PasswordResetMailSender;
import com.grun.calorietracker.service.PasswordResetService;
import com.grun.calorietracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String REQUEST_MESSAGE = "If the email exists, a password reset link has been sent.";
    private static final String CONFIRM_MESSAGE = "Password has been reset successfully.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender passwordResetMailSender;
    private final RefreshTokenService refreshTokenService;

    @Value("${grun.password-reset.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${grun.password-reset.base-url:http://localhost:8080/reset-password}")
    private String resetBaseUrl;

    @Override
    @Transactional
    public PasswordResetResponseDto requestPasswordReset(PasswordResetRequestDto request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            invalidateExistingTokens(user);
            String rawToken = generateRawToken();

            PasswordResetTokenEntity token = new PasswordResetTokenEntity();
            token.setUser(user);
            token.setTokenHash(hashToken(rawToken));
            token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
            passwordResetTokenRepository.save(token);

            passwordResetMailSender.sendPasswordResetToken(user.getEmail(), rawToken, buildResetLink(rawToken));
        });

        return new PasswordResetResponseDto(REQUEST_MESSAGE);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public PasswordResetResponseDto confirmPasswordReset(PasswordResetConfirmRequestDto request) {
        PasswordResetTokenEntity token = passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(hashToken(request.getToken()))
                .orElseThrow(() -> new IllegalArgumentException("Password reset token is invalid or expired"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsedAt(LocalDateTime.now());
            passwordResetTokenRepository.save(token);
            throw new IllegalArgumentException("Password reset token is invalid or expired");
        }

        UserEntity user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        return new PasswordResetResponseDto(CONFIRM_MESSAGE);
    }

    private void invalidateExistingTokens(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> token.setUsedAt(now));
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

    private String buildResetLink(String rawToken) {
        return resetBaseUrl + "?token=" + rawToken;
    }
}

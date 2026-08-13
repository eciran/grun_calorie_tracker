package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminSessionService;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.service.PasswordResetMailSender;
import com.grun.calorietracker.service.PasswordResetService;
import com.grun.calorietracker.service.RefreshTokenService;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.enums.PreferredLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

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
    private final AdminSessionService adminSessionService;
    private final AdminAuditService adminAuditService;
    private final MailDeliveryService mailDeliveryService;
    @Autowired(required = false) private MailProperties mailProperties;

    @Value("${grun.password-reset.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${grun.password-reset.base-url:http://localhost:8082/reset-password}")
    private String resetBaseUrl;

    @Value("${grun.admin-password-reset.base-url:http://localhost:8080/admin-ui/index.html}")
    private String adminResetBaseUrl;

    @Value("${grun.password-reset.request-cooldown-seconds:60}")
    private long requestCooldownSeconds;

    @Override
    @Transactional
    public PasswordResetResponseDto requestPasswordReset(PasswordResetRequestDto request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (isWithinRequestCooldown(user)) {
                return;
            }
            invalidateExistingTokens(user);
            String rawToken = generateRawToken();

            PasswordResetTokenEntity token = new PasswordResetTokenEntity();
            token.setUser(user);
            token.setTokenHash(hashToken(rawToken));
            token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
            passwordResetTokenRepository.save(token);

            passwordResetMailSender.sendPasswordResetToken(user.getEmail(), rawToken, buildResetLink(user, rawToken));
        });

        return new PasswordResetResponseDto(REQUEST_MESSAGE, Math.max(requestCooldownSeconds, 0L));
    }

    @Override
    @Transactional
    public PasswordResetResponseDto requestAdminPasswordReset(String ownerEmail, Long userId, String correlationId) {
        UserEntity owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Owner account was not found."));
        if (owner.getRole() == null || !owner.getRole().isOwner()
                || !Boolean.TRUE.equals(owner.getAccountEnabled()) || Boolean.TRUE.equals(owner.getAccountLocked())) {
            throw new IllegalArgumentException("Only an active owner can request an admin password reset.");
        }
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found."));
        if (target.getRole() == null || !target.getRole().isAdminRole() || target.getRole().isOwner()) {
            throw new IllegalArgumentException("Password reset links can only be sent to delegated admin accounts.");
        }
        issueToken(target);
        adminAuditService.record(ownerEmail, AdminAuditActionType.ADMIN_PASSWORD_RESET_REQUEST,
                AdminAuditTargetType.ADMIN_ACCOUNT, target.getId().toString(), null,
                Map.of("email", target.getEmail(), "delivery", "PASSWORD_RESET_LINK"), correlationId);
        return new PasswordResetResponseDto("Password reset link has been sent to the admin account.",
                Math.max(requestCooldownSeconds, 0L));
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
        user.setPasswordSet(true);
        user.setEmailVerified(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        if (user.getRole() != null && user.getRole().isAdminRole()) {
            adminSessionService.revokeAllForUser(user);
            adminAuditService.record(user.getEmail(), AdminAuditActionType.ADMIN_PASSWORD_RESET_CONFIRM,
                    AdminAuditTargetType.ADMIN_ACCOUNT, user.getId().toString(), null,
                    Map.of("sessionsRevoked", true), null);
            if (mailProperties == null) {
                mailDeliveryService.sendTransactionalEmail(user.getEmail(), "Your GRun admin password was changed",
                        "Your GRun admin password was changed. If you did not perform this action, contact the account owner immediately.",
                        "<p>Your GRun admin password was changed.</p><p>If you did not perform this action, contact the account owner immediately.</p>");
            } else {
                boolean turkish = user.getPreferredLanguage() == PreferredLanguage.TR;
                long templateId = turkish ? mailProperties.getBrevo().getTemplates().getAdminPasswordChangedTr()
                        : mailProperties.getBrevo().getTemplates().getAdminPasswordChangedEn();
                mailDeliveryService.sendTransactionalTemplate(user.getEmail(), templateId, Map.of(), "Your GRun admin password was changed",
                        "Your GRun admin password was changed. If you did not perform this action, contact the account owner immediately.",
                        "<p>Your GRun admin password was changed.</p><p>If you did not perform this action, contact the account owner immediately.</p>");
            }
        }

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        return new PasswordResetResponseDto(CONFIRM_MESSAGE);
    }

    private void invalidateExistingTokens(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> token.setUsedAt(now));
    }

    private boolean isWithinRequestCooldown(UserEntity user) {
        if (requestCooldownSeconds <= 0) {
            return false;
        }
        LocalDateTime cooldownThreshold = LocalDateTime.now().minusSeconds(requestCooldownSeconds);
        return passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(user)
                .filter(token -> token.getCreatedAt() != null)
                .map(token -> token.getCreatedAt().isAfter(cooldownThreshold))
                .orElse(false);
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

    private void issueToken(UserEntity user) {
        invalidateExistingTokens(user);
        String rawToken = generateRawToken();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        passwordResetTokenRepository.save(token);
        passwordResetMailSender.sendPasswordResetToken(user.getEmail(), rawToken, buildResetLink(user, rawToken));
    }

    private String buildResetLink(UserEntity user, String rawToken) {
        if (user.getRole() != null && user.getRole().isAdminRole()) {
            return adminResetBaseUrl + (adminResetBaseUrl.contains("?") ? "&" : "?") + "passwordResetToken=" + rawToken;
        }
        return resetBaseUrl + (resetBaseUrl.contains("?") ? "&" : "?") + "token=" + rawToken;
    }
}

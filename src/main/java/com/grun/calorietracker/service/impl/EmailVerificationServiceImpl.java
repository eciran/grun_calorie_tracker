package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.entity.EmailVerificationTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.EmailVerificationMailSender;
import com.grun.calorietracker.service.EmailVerificationService;
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
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final String RESEND_MESSAGE = "If the email exists, a verification link has been sent.";
    private static final String CONFIRM_MESSAGE = "Email has been verified successfully.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailVerificationMailSender emailVerificationMailSender;

    @Value("${grun.email-verification.expiration-minutes:1440}")
    private long expirationMinutes;

    @Value("${grun.email-verification.base-url:http://localhost:8080/verify-email}")
    private String verificationBaseUrl;

    @Override
    @Transactional
    public void createVerificationTokenForUser(UserEntity user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        invalidateExistingTokens(user);
        String rawToken = generateRawToken();

        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        emailVerificationTokenRepository.save(token);

        emailVerificationMailSender.sendEmailVerificationToken(user.getEmail(), rawToken, buildVerificationLink(rawToken));
    }

    @Override
    @Transactional
    public EmailVerificationResponseDto resendVerification(EmailVerificationRequestDto request) {
        userRepository.findByEmail(request.getEmail())
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .ifPresent(this::createVerificationTokenForUser);
        return new EmailVerificationResponseDto(RESEND_MESSAGE);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public EmailVerificationResponseDto confirmVerification(EmailVerificationConfirmRequestDto request) {
        EmailVerificationTokenEntity token = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hashToken(request.getToken()))
                .orElseThrow(() -> new IllegalArgumentException("Email verification token is invalid or expired"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsedAt(LocalDateTime.now());
            emailVerificationTokenRepository.save(token);
            throw new IllegalArgumentException("Email verification token is invalid or expired");
        }

        UserEntity user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(token);

        return new EmailVerificationResponseDto(CONFIRM_MESSAGE);
    }

    private void invalidateExistingTokens(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        emailVerificationTokenRepository.findByUserAndUsedAtIsNull(user)
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

    private String buildVerificationLink(String rawToken) {
        return verificationBaseUrl + "?token=" + rawToken;
    }
}

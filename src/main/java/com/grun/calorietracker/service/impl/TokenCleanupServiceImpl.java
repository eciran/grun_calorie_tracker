package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenCleanupServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupServiceImpl.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Scheduled(fixedDelayString = "${grun.tokens.cleanup-interval-ms:3600000}")
    @Transactional
    public TokenCleanupResult cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now();
        long refreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
        long passwordResetTokens = passwordResetTokenRepository.deleteByExpiresAtBefore(cutoff);
        long emailVerificationTokens = emailVerificationTokenRepository.deleteByExpiresAtBefore(cutoff);

        TokenCleanupResult result = new TokenCleanupResult(refreshTokens, passwordResetTokens, emailVerificationTokens);
        if (result.totalDeleted() > 0) {
            log.info(
                    "expired_token_cleanup refreshTokens={} passwordResetTokens={} emailVerificationTokens={}",
                    refreshTokens,
                    passwordResetTokens,
                    emailVerificationTokens
            );
        }
        return result;
    }

    public record TokenCleanupResult(long refreshTokens,
                                     long passwordResetTokens,
                                     long emailVerificationTokens) {

        public long totalDeleted() {
            return refreshTokens + passwordResetTokens + emailVerificationTokens;
        }
    }
}

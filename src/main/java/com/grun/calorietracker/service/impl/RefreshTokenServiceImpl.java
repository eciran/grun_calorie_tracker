package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserActivitySource;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.RefreshTokenService;
import com.grun.calorietracker.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserActivityService userActivityService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.grun.calorietracker.service.AdminSessionService adminSessionService;

    @Value("${grun.refresh-token.expiration-days:30}")
    private long expirationDays;

    @Override
    @Transactional
    public String createRefreshToken(UserEntity user) {
        String rawToken = generateRawToken();

        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setAuthTokenVersion(user.getAuthTokenVersion());
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        refreshTokenRepository.save(token);

        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refreshAccessToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(tokenHash)
                .orElseGet(() -> handlePotentialTokenReuse(tokenHash));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            throw new InvalidRefreshTokenException();
        }

        UserEntity user = token.getUser();
        if (!isSessionAllowed(user)) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            throw new InvalidRefreshTokenException();
        }

        UserEntity managedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new InvalidRefreshTokenException());
        long currentVersion = userRepository.findAuthTokenVersionById(user.getId())
                .orElseThrow(() -> new InvalidRefreshTokenException());
        if (token.getAuthTokenVersion() != currentVersion) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            throw new InvalidRefreshTokenException();
        }
        if (!isSessionAllowed(managedUser)) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(managedUser)
                    .forEach(activeToken -> activeToken.setRevokedAt(LocalDateTime.now()));
            throw new InvalidRefreshTokenException();
        }
        managedUser.setAuthTokenVersion(currentVersion);
        LocalDateTime consumedAt = LocalDateTime.now();
        if (refreshTokenRepository.consumeIfAvailable(tokenHash, consumedAt, currentVersion) != 1) {
            throw new com.grun.calorietracker.exception.RequestConflictException("Refresh token was already consumed. Use the latest session token.");
        }
        // The conditional update clears stale managed snapshots; never save the old token back over it.
        token.setUsedAt(consumedAt);
        String newRefreshToken = createRefreshToken(managedUser);
        String accessToken = jwtUtil.generateUserToken(managedUser);
        recordActivitySafely(managedUser.getEmail());

        return new AuthResponse(accessToken, newRefreshToken, "Bearer", jwtUtil.getExpirationSeconds(), "Token refreshed successfully");
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (refreshTokenRepository.revokeIfAvailable(hashToken(rawRefreshToken), LocalDateTime.now()) != 1) {
            throw new InvalidRefreshTokenException();
        }
    }

    @Override
    @Transactional
    public void revokeAllForUser(UserEntity user) {
        UserEntity managedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (userRepository.incrementAuthTokenVersion(user.getId()) != 1) {
            throw new IllegalStateException("Could not revoke account access tokens");
        }
        long currentVersion = userRepository.findAuthTokenVersionById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Could not read account security state"));
        managedUser.setAuthTokenVersion(currentVersion);
        user.setAuthTokenVersion(currentVersion);
        if (adminSessionService != null) adminSessionService.revokeAllForUser(user);
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)
                .forEach(token -> token.setRevokedAt(now));
    }

    private void recordActivitySafely(String email) {
        try {
            userActivityService.recordActivity(email, UserActivitySource.TOKEN_REFRESH);
        } catch (RuntimeException exception) {
            log.warn("Refresh token activity could not be recorded", exception);
        }
    }

    private RefreshTokenEntity handlePotentialTokenReuse(String tokenHash) {
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getUsedAt() != null && token.getRevokedAt() == null && token.getUser() != null)
                .ifPresent(token -> {
                    if (token.getUsedAt().isAfter(LocalDateTime.now().minusSeconds(30))
                            && userRepository.findAuthTokenVersionById(token.getUser().getId())
                            .filter(version -> version == token.getAuthTokenVersion()).isPresent()) {
                        throw new com.grun.calorietracker.exception.RequestConflictException("Refresh token was already consumed. Use the latest session token.");
                    }
                    revokeTokenFamilyAfterReuse(token);
                });
        throw new InvalidRefreshTokenException();
    }

    private boolean isSessionAllowed(UserEntity user) {
        return user != null
                && !Boolean.FALSE.equals(user.getAccountEnabled())
                && !Boolean.TRUE.equals(user.getAccountLocked())
                && (user.getLoginLockedUntil() == null || !user.getLoginLockedUntil().isAfter(LocalDateTime.now()));
    }

    private void revokeTokenFamilyAfterReuse(RefreshTokenEntity reusedToken) {
        UserEntity user = userRepository.findByIdForUpdate(reusedToken.getUser().getId())
                .orElseThrow(() -> new InvalidRefreshTokenException());
        long currentVersion = userRepository.findAuthTokenVersionById(user.getId())
                .orElseThrow(() -> new InvalidRefreshTokenException());
        // An already-retired credential generation must not log out a fresh session.
        if (reusedToken.getAuthTokenVersion() == currentVersion) revokeAllForUser(user);
        LocalDateTime now = LocalDateTime.now();
        if (reusedToken.getRevokedAt() == null) {
            reusedToken.setRevokedAt(now);
            refreshTokenRepository.save(reusedToken);
        }
    }

    private static final class InvalidRefreshTokenException extends IllegalArgumentException {
        private InvalidRefreshTokenException() { super("Refresh token is invalid or expired"); }
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

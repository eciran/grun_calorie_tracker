package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AdminMfaRecoveryCodeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.AdminMfaRecoveryCodeRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminMfaSecretCipher;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.security.TotpService;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSecurityAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminMfaServiceImpl implements AdminMfaService {
    private static final int RECOVERY_CODE_COUNT = 8;

    private final UserRepository userRepository;
    private final AdminMfaRecoveryCodeRepository recoveryCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AdminMfaSecretCipher secretCipher;
    private final JwtUtil jwtUtil;
    private final AdminAuditService auditService;
    @Autowired(required=false) private AdminSecurityAlertService securityAlerts;

    @Value("${grun.security.admin-mfa-required:false}")
    private boolean adminMfaRequired;

    @Override
    @Transactional(readOnly = true)
    public AdminMfaStatusDto status(String email) {
        UserEntity user = requireAdmin(email, false);
        return toStatus(user);
    }

    @Override
    @Transactional
    public AdminMfaEnrollmentDto beginEnrollment(String email, String currentPassword, String correlationId) {
        UserEntity user = requireAdmin(email, true);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is invalid.");
        }
        String secret = totpService.generateSecret();
        user.setAdminMfaSecretEncrypted(secretCipher.encrypt(secret));
        user.setAdminMfaEnrollmentStartedAt(Instant.now());
        user.setAdminMfaEnabled(false);
        user.setAdminMfaVerifiedAt(null);
        recoveryCodeRepository.deleteByUser(user);
        userRepository.save(user);
        String label = URLEncoder.encode("GRUN Admin:" + user.getEmail(), StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("GRUN Admin", StandardCharsets.UTF_8);
        return new AdminMfaEnrollmentDto(secret,
                "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=6&period=30");
    }

    @Override
    @Transactional
    public AdminMfaVerificationDto verifyEnrollment(String email, String code, String correlationId) {
        UserEntity user = requireAdmin(email, true);
        if (user.getAdminMfaSecretEncrypted() == null || user.getAdminMfaEnrollmentStartedAt() == null) {
            throw new IllegalArgumentException("Start MFA enrollment before verification.");
        }
        if (!totpService.verify(secretCipher.decrypt(user.getAdminMfaSecretEncrypted()), code)) {
            throw new IllegalArgumentException("Authenticator code is invalid.");
        }
        user.setAdminMfaEnabled(true);
        user.setAdminMfaVerifiedAt(Instant.now());
        user.setAdminMfaEnrollmentStartedAt(null);
        userRepository.save(user);
        List<String> recoveryCodes = createRecoveryCodes(user);
        auditService.record(email, AdminAuditActionType.ADMIN_MFA_ENROLL, AdminAuditTargetType.ADMIN_ACCOUNT,
                user.getId().toString(), Map.of("enabled", false), Map.of("enabled", true), correlationId);
        revokeRefreshTokens(user);
        return new AdminMfaVerificationDto(true, recoveryCodes);
    }

    @Override
    @Transactional
    public AdminMfaStatusDto disable(String email, String code, String correlationId) {
        UserEntity user = requireAdmin(email, true);
        requireValidCode(user, code, true);
        user.setAdminMfaEnabled(false);
        user.setAdminMfaSecretEncrypted(null);
        user.setAdminMfaEnrollmentStartedAt(null);
        user.setAdminMfaVerifiedAt(null);
        recoveryCodeRepository.deleteByUser(user);
        userRepository.save(user);
        revokeRefreshTokens(user);
        auditService.record(email, AdminAuditActionType.ADMIN_MFA_DISABLE, AdminAuditTargetType.ADMIN_ACCOUNT,
                user.getId().toString(), Map.of("enabled", true), Map.of("enabled", false), correlationId);
        return toStatus(user);
    }

    @Override
    @Transactional
    public AdminReauthenticationDto reauthenticate(String email, String code, com.grun.calorietracker.enums.AdminReauthenticationPurpose purpose, String correlationId) {
        UserEntity user = requireAdmin(email, true);
        if (user.getRole() != com.grun.calorietracker.enums.UserRole.OWNER) throw new IllegalArgumentException("Owner account required.");
        if (purpose == null) throw new IllegalArgumentException("Re-authentication purpose is required.");
        requireValidCode(user, code, true);
        auditService.record(email, AdminAuditActionType.ADMIN_MFA_REAUTHENTICATE,
                AdminAuditTargetType.ADMIN_ACCOUNT, user.getId().toString(), null, Map.of("verified", true, "purpose", purpose.name()), correlationId);
        return new AdminReauthenticationDto(jwtUtil.generateAdminReauthenticationToken(email, purpose), 300);
    }

    @Override
    @Transactional
    public void verifyLogin(UserEntity user, String code) {
        if (!user.getRole().isAdminRole()) return;
        if (!Boolean.TRUE.equals(user.getAdminMfaEnabled())) {
            if (user.getRole() == com.grun.calorietracker.enums.UserRole.OWNER) return;
            if (adminMfaRequired) {
                throw new IllegalArgumentException("Admin MFA enrollment is required before secure login.");
            }
            return;
        }
        requireValidCode(user, code, true);
    }

    private void requireValidCode(UserEntity user, String code, boolean allowRecovery) {
        if (!Boolean.TRUE.equals(user.getAdminMfaEnabled()) || user.getAdminMfaSecretEncrypted() == null) {
            throw new IllegalArgumentException("Admin MFA is not enrolled.");
        }
        if (totpService.verify(secretCipher.decrypt(user.getAdminMfaSecretEncrypted()), code)) return;
        if (allowRecovery && consumeRecoveryCode(user, code)) { if(securityAlerts!=null) securityAlerts.recoveryCodeUsed(user); return; }
        if(securityAlerts!=null) securityAlerts.mfaFailure(user);
        throw new IllegalArgumentException("Authenticator or recovery code is invalid.");
    }

    private boolean consumeRecoveryCode(UserEntity user, String rawCode) {
        if (rawCode == null) return false;
        for (AdminMfaRecoveryCodeEntity stored : recoveryCodeRepository.findByUserAndUsedAtIsNull(user)) {
            if (passwordEncoder.matches(normalizeRecovery(rawCode), stored.getCodeHash())) {
                stored.setUsedAt(Instant.now());
                recoveryCodeRepository.save(stored);
                return true;
            }
        }
        return false;
    }

    private List<String> createRecoveryCodes(UserEntity user) {
        recoveryCodeRepository.deleteByUser(user);
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
            String formatted = raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8);
            AdminMfaRecoveryCodeEntity entity = new AdminMfaRecoveryCodeEntity();
            entity.setUser(user);
            entity.setCodeHash(passwordEncoder.encode(normalizeRecovery(formatted)));
            recoveryCodeRepository.save(entity);
            rawCodes.add(formatted);
        }
        return rawCodes;
    }

    private String normalizeRecovery(String value) {
        return value == null ? "" : value.replace("-", "").trim().toUpperCase(Locale.ROOT);
    }

    private AdminMfaStatusDto toStatus(UserEntity user) {
        return new AdminMfaStatusDto(Boolean.TRUE.equals(user.getAdminMfaEnabled()),
                !Boolean.TRUE.equals(user.getAdminMfaEnabled()) && user.getAdminMfaSecretEncrypted() != null,
                recoveryCodeRepository.countByUserAndUsedAtIsNull(user), user.getAdminMfaVerifiedAt());
    }

    private UserEntity requireAdmin(String email, boolean lock) {
        UserEntity user = (lock ? userRepository.findByEmailForUpdate(email) : userRepository.findByEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found."));
        if (user.getRole() == null || !user.getRole().isAdminRole()) {
            throw new IllegalArgumentException("Account is not an admin team member.");
        }
        return user;
    }

    private void revokeRefreshTokens(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)
                .forEach(token -> token.setRevokedAt(now));
    }
}
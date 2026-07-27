package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminAccessProfileDto;
import com.grun.calorietracker.dto.AdminAccessGrantRequestDto;
import com.grun.calorietracker.dto.AdminTeamMemberDto;
import com.grun.calorietracker.dto.AdminTeamMemberUpdateRequestDto;
import com.grun.calorietracker.dto.AdminTeamPageDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminPermissionMatrix;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSecurityServiceImpl implements AdminSecurityService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminAuditService adminAuditService;

    @Value("${grun.security.admin-mfa-required:false}")
    private boolean adminMfaRequired;

    @Override
    @Transactional(readOnly = true)
    public AdminAccessProfileDto currentAccess(String email) {
        UserEntity user = requireAdmin(userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found.")));
        List<String> permissions = AdminPermissionMatrix.permissionsFor(user.getRole()).stream()
                .map(Enum::name)
                .sorted()
                .toList();
        return new AdminAccessProfileDto(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                permissions,
                adminMfaRequired,
                Boolean.TRUE.equals(user.getAdminMfaEnabled())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTeamPageDto listTeam(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<UserEntity> result = userRepository.findByRoleIn(
                adminRoles(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "email"))
        );
        return new AdminTeamPageDto(
                result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Override
    @Transactional
    public AdminTeamMemberDto grantAccess(
            String actorEmail,
            AdminAccessGrantRequestDto request,
            String correlationId
    ) {
        if (!request.role().isAdminRole()) {
            throw new IllegalArgumentException("Only admin team roles can be granted.");
        }
        UserEntity target = userRepository.findByEmailForUpdate(request.email().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Create and verify the standard user account before granting admin access."
                ));
        if (target.getRole() != null && target.getRole().isAdminRole()) {
            throw new IllegalArgumentException("This account already belongs to the admin team.");
        }
        Map<String, Object> before = snapshotPublicAccount(target);
        target.setRole(request.role());
        target.setAccountEnabled(true);
        target.setAdminMfaEnabled(request.mfaEnabled());
        target.setAdminRoleUpdatedAt(Instant.now());
        UserEntity saved = userRepository.save(target);
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(saved)
                .forEach(token -> token.setRevokedAt(now));
        Map<String, Object> after = new LinkedHashMap<>(snapshot(saved));
        after.put("reason", request.reason().trim());
        adminAuditService.record(actorEmail, AdminAuditActionType.ADMIN_ROLE_UPDATE,
                AdminAuditTargetType.ADMIN_ACCOUNT, saved.getId().toString(), before, after, correlationId);
        return toDto(saved);
    }
    @Override
    @Transactional
    public AdminTeamMemberDto updateMember(
            String actorEmail,
            Long userId,
            AdminTeamMemberUpdateRequestDto request,
            String correlationId
    ) {
        if (!request.role().isAdminRole()) {
            throw new IllegalArgumentException("Only admin team roles can be assigned here.");
        }
        UserEntity target = requireAdmin(userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found.")));
        boolean self = target.getEmail().equalsIgnoreCase(actorEmail);
        if (self && (!request.enabled() || request.role() != target.getRole())) {
            throw new IllegalArgumentException("You cannot deactivate or change your own admin role.");
        }
        if (target.getRole() == UserRole.ADMIN
                && request.role() != UserRole.ADMIN
                && userRepository.countByRoleAndAccountEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("The final active Super Admin cannot be downgraded.");
        }

        Map<String, Object> before = snapshot(target);
        UserRole oldRole = target.getRole();
        boolean oldEnabled = Boolean.TRUE.equals(target.getAccountEnabled());
        boolean oldMfa = Boolean.TRUE.equals(target.getAdminMfaEnabled());
        target.setRole(request.role());
        target.setAccountEnabled(request.enabled());
        target.setAdminMfaEnabled(request.mfaEnabled());
        if (oldRole != request.role()) {
            target.setAdminRoleUpdatedAt(Instant.now());
        }
        UserEntity saved = userRepository.save(target);

        boolean securityChanged = oldRole != request.role()
                || oldEnabled != request.enabled()
                || oldMfa != request.mfaEnabled();
        if (securityChanged) {
            LocalDateTime now = LocalDateTime.now();
            refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(saved)
                    .forEach(token -> token.setRevokedAt(now));
        }
        if (oldRole != request.role()) {
            audit(actorEmail, AdminAuditActionType.ADMIN_ROLE_UPDATE, saved, before, request, correlationId);
        }
        if (oldEnabled != request.enabled()) {
            audit(actorEmail, AdminAuditActionType.ADMIN_STATUS_UPDATE, saved, before, request, correlationId);
        }
        if (oldMfa != request.mfaEnabled()) {
            audit(actorEmail, AdminAuditActionType.ADMIN_MFA_STATUS_UPDATE, saved, before, request, correlationId);
        }
        return toDto(saved);
    }

    private void audit(
            String actorEmail,
            AdminAuditActionType action,
            UserEntity target,
            Map<String, Object> before,
            AdminTeamMemberUpdateRequestDto request,
            String correlationId
    ) {
        Map<String, Object> after = new LinkedHashMap<>(snapshot(target));
        after.put("reason", request.reason().trim());
        adminAuditService.record(actorEmail, action, AdminAuditTargetType.ADMIN_ACCOUNT,
                target.getId().toString(), before, after, correlationId);
    }

    private UserEntity requireAdmin(UserEntity user) {
        if (user.getRole() == null || !user.getRole().isAdminRole()) {
            throw new IllegalArgumentException("Account is not an admin team member.");
        }
        return user;
    }

    private AdminTeamMemberDto toDto(UserEntity user) {
        long activeSessions = refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user).size();
        return new AdminTeamMemberDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getAccountEnabled()),
                Boolean.TRUE.equals(user.getAccountLocked()),
                Boolean.TRUE.equals(user.getAdminMfaEnabled()),
                activeSessions,
                user.getLastActiveAt(),
                user.getAdminRoleUpdatedAt()
        );
    }

    private Map<String, Object> snapshot(UserEntity user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", user.getRole().name());
        value.put("enabled", Boolean.TRUE.equals(user.getAccountEnabled()));
        value.put("mfaEnabled", Boolean.TRUE.equals(user.getAdminMfaEnabled()));
        return value;
    }

    private Map<String, Object> snapshotPublicAccount(UserEntity user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", user.getRole() == null ? null : user.getRole().name());
        value.put("enabled", Boolean.TRUE.equals(user.getAccountEnabled()));
        return value;
    }
    private List<UserRole> adminRoles() {
        return Arrays.stream(UserRole.values()).filter(UserRole::isAdminRole).toList();
    }
}

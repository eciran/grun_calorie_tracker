package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminTeamMemberUpdateRequestDto;
import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSecurityServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AdminAuditService adminAuditService;

    private AdminSecurityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSecurityServiceImpl(userRepository, refreshTokenRepository, adminAuditService);
    }

    @Test
    void roleChangeRevokesRefreshSessionsAndWritesAudit() {
        UserEntity actor = admin(1L, "owner@grun.app", UserRole.OWNER);
        UserEntity target = admin(7L, "catalog@grun.app", UserRole.ADMIN_CATALOG);
        when(userRepository.findByEmail("owner@grun.app")).thenReturn(Optional.of(actor));
        RefreshTokenEntity token = new RefreshTokenEntity();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(target))
                .thenReturn(List.of(token), List.of());

        service.updateMember(
                "owner@grun.app",
                7L,
                new AdminTeamMemberUpdateRequestDto(UserRole.ADMIN_SUPPORT, true, true, "Support transfer"),
                "cid-1"
        );

        assertEquals(UserRole.ADMIN_SUPPORT, target.getRole());
        assertNotNull(token.getRevokedAt());
        verify(adminAuditService).record(
                eq("owner@grun.app"),
                eq(AdminAuditActionType.ADMIN_ROLE_UPDATE),
                eq(AdminAuditTargetType.ADMIN_ACCOUNT),
                eq("7"),
                any(),
                any(),
                eq("cid-1")
        );
    }

    @Test
    void nonOwnerCannotManageAdminRoles() {
        UserEntity actor = admin(3L, "legacy@grun.app", UserRole.ADMIN);
        when(userRepository.findByEmail("legacy@grun.app")).thenReturn(Optional.of(actor));
        assertThrows(IllegalArgumentException.class, () -> service.updateMember(
                "legacy@grun.app", 7L,
                new AdminTeamMemberUpdateRequestDto(UserRole.ADMIN_SUPPORT, true, true, "Forbidden"),
                "cid-2"));
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void legacyAdminRoleCannotBeAssigned() {
        UserEntity actor = admin(1L, "owner@grun.app", UserRole.OWNER);
        when(userRepository.findByEmail("owner@grun.app")).thenReturn(Optional.of(actor));
        assertThrows(IllegalArgumentException.class, () -> service.updateMember(
                "owner@grun.app", 7L,
                new AdminTeamMemberUpdateRequestDto(UserRole.ADMIN, true, true, "No super admins"),
                "cid-3"));
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void ownerCannotBeModifiedThroughAdminTeamEndpoint() {
        UserEntity target = admin(9L, "primary@gmail.com", UserRole.OWNER);
        when(userRepository.findByEmail("primary@gmail.com")).thenReturn(Optional.of(target));
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(target));
        assertThrows(IllegalArgumentException.class, () -> service.updateMember(
                "primary@gmail.com", 9L,
                new AdminTeamMemberUpdateRequestDto(UserRole.ADMIN_READ_ONLY, true, true, "No generic mutation"),
                "cid-owner"));
        verify(userRepository, never()).save(any());
    }

    private UserEntity admin(Long id, String email, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountEnabled(true);
        user.setAccountLocked(false);
        user.setAdminMfaEnabled(false);
        return user;
    }
}

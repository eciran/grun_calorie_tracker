package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.AdminMfaLoginException;
import com.grun.calorietracker.repository.AdminMfaRecoveryCodeRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminMfaSecretCipher;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.security.TotpService;
import com.grun.calorietracker.service.impl.AdminMfaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AdminMfaServiceImplLoginTest {

    @Test
    void verifyLogin_whenMfaEnabledAndCodeMissing_returnsExplicitChallenge() {
        AdminMfaServiceImpl service = new AdminMfaServiceImpl(
                mock(UserRepository.class),
                mock(AdminMfaRecoveryCodeRepository.class),
                mock(RefreshTokenRepository.class),
                mock(PasswordEncoder.class),
                mock(TotpService.class),
                mock(AdminMfaSecretCipher.class),
                mock(JwtUtil.class),
                mock(AdminAuditService.class)
        );
        UserEntity owner = new UserEntity();
        owner.setRole(UserRole.OWNER);
        owner.setAdminMfaEnabled(true);

        AdminMfaLoginException error = assertThrows(
                AdminMfaLoginException.class,
                () -> service.verifyLogin(owner, null)
        );

        assertEquals("ADMIN_MFA_REQUIRED", error.getCode());
    }
}

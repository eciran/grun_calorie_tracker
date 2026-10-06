package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AuthRequest;
import com.grun.calorietracker.dto.AdminMfaCodeRequestDto;
import com.grun.calorietracker.dto.AdminMfaEnrollmentRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.AdminAuthenticationRateLimitException;
import com.grun.calorietracker.exception.GlobalExceptionHandler;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminAuthenticationAttemptGuard;
import com.grun.calorietracker.security.InMemoryRateLimiter;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthenticationRateLimitContractTest {
    @Mock UserRepository users;
    @Mock AuthenticationManager authenticationManager;
    @Mock AdminAuthenticationAttemptGuard attemptGuard;
    @Mock AdminMfaService mfa;
    @InjectMocks AuthController legacy;

    @Test void dedicatedLoginBlocksBeforePasswordOrMfaVerification() {
        doThrow(new AdminAuthenticationRateLimitException(60)).when(attemptGuard).requireAllowed("owner@example.com");
        var sessions = mock(AdminSessionService.class);
        var controller = new AdminSessionAuthController(authenticationManager, users, mfa, sessions, attemptGuard);
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.login(request(), new MockHttpServletRequest(), new MockHttpServletResponse()));
        verifyNoInteractions(authenticationManager, users, mfa, sessions);
    }

    @Test void legacyAdminLoginCannotBypassAccountLimit() {
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(account(UserRole.OWNER)));
        doThrow(new AdminAuthenticationRateLimitException(60)).when(attemptGuard).requireAllowed("owner@example.com");
        assertThrows(AdminAuthenticationRateLimitException.class, () -> legacy.login(request()));
        verifyNoInteractions(authenticationManager, mfa);
    }

    @Test void standardUserLoginKeepsExistingPasswordValidation() {
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(account(UserRole.STANDARD)));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        assertThrows(BadCredentialsException.class, () -> legacy.login(request()));
        verifyNoInteractions(attemptGuard, mfa);
    }

    @Test void wrongPasswordAttemptsAcrossBothLoginRoutesConsumeSameBudget() {
        var realGuard = new AdminAuthenticationAttemptGuard(new InMemoryRateLimiter());
        org.springframework.test.util.ReflectionTestUtils.setField(legacy, "attemptGuard", realGuard);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(account(UserRole.OWNER)));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        var controller = new AdminSessionAuthController(authenticationManager, users, mfa, mock(AdminSessionService.class), realGuard);
        for (int i = 0; i < 3; i++) assertThrows(BadCredentialsException.class, () -> legacy.login(request()));
        for (int i = 0; i < 2; i++) assertThrows(BadCredentialsException.class,
                () -> controller.login(request(), new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertThrows(AdminAuthenticationRateLimitException.class, () -> legacy.login(request()));
        verify(authenticationManager, times(5)).authenticate(any());
        verifyNoInteractions(mfa);
    }

    @Test void allMfaMutationRoutesStopBeforeCallingMfaService() {
        var controller = new AdminSecurityController(null, mfa, null, null, null, attemptGuard);
        var principal = User.withUsername("owner@example.com").password("unused").roles("OWNER").build();
        doThrow(new AdminAuthenticationRateLimitException(60)).when(attemptGuard).requireAllowed("owner@example.com");
        var http = new MockHttpServletRequest();
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.beginMfaEnrollment(mock(AdminMfaEnrollmentRequestDto.class), principal, http));
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.verifyMfaEnrollment(mock(AdminMfaCodeRequestDto.class), principal, http));
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.disableMfa(mock(AdminMfaCodeRequestDto.class), principal, http));
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.reauthenticate(mock(AdminMfaCodeRequestDto.class), principal, http));
        verifyNoInteractions(mfa);
    }

    @Test void invalidMfaAttemptsCannotCreateSessionAndEventuallyThrottle() {
        var guard = new AdminAuthenticationAttemptGuard(new InMemoryRateLimiter());
        var sessions = mock(AdminSessionService.class);
        var owner = account(UserRole.OWNER);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        doThrow(com.grun.calorietracker.exception.AdminMfaLoginException.invalid())
                .when(mfa).verifyLogin(owner, null);
        var controller = new AdminSessionAuthController(authenticationManager, users, mfa, sessions, guard);
        for (int i = 0; i < 5; i++) {
            assertThrows(com.grun.calorietracker.exception.AdminMfaLoginException.class,
                    () -> controller.login(request(), new MockHttpServletRequest(), new MockHttpServletResponse()));
        }
        assertThrows(AdminAuthenticationRateLimitException.class,
                () -> controller.login(request(), new MockHttpServletRequest(), new MockHttpServletResponse()));
        verify(authenticationManager, times(5)).authenticate(any());
        verifyNoInteractions(sessions);
    }

    @Test void blockedHttpLoginReturnsStable429WithRetryAfter() throws Exception {
        doThrow(new AdminAuthenticationRateLimitException(900)).when(attemptGuard).requireAllowed("owner@example.com");
        var controller = new AdminSessionAuthController(authenticationManager, users, mfa, mock(AdminSessionService.class), attemptGuard);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new StaticMessageSource(), false)).build();
        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        verifyNoInteractions(authenticationManager, mfa);
    }

    private AuthRequest request() {
        var request = new AuthRequest();
        request.setEmail("owner@example.com");
        request.setPassword("StrongPass1!");
        return request;
    }

    private UserEntity account(UserRole role) {
        var user = new UserEntity();
        user.setEmail("owner@example.com");
        user.setRole(role);
        return user;
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdminSessionAuthControllerCookiePolicyTest {
    @Test void loginCookieIsSecureNonPersistentBrowserSessionCookie(){
        var controller=new AdminSessionAuthController(mock(AuthenticationManager.class),mock(UserRepository.class),mock(AdminMfaService.class),mock(AdminSessionService.class));
        ReflectionTestUtils.setField(controller,"secureCookie",true);
        ResponseCookie cookie=ReflectionTestUtils.invokeMethod(controller,"cookie","opaque",false);
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly()); assertTrue(cookie.isSecure());
        assertEquals("Strict",cookie.getSameSite()); assertEquals("/api/v1/auth/admin",cookie.getPath());
        assertEquals(Duration.ofSeconds(-1),cookie.getMaxAge());
    }
    @Test void logoutCookieExpiresImmediately(){
        var controller=new AdminSessionAuthController(mock(AuthenticationManager.class),mock(UserRepository.class),mock(AdminMfaService.class),mock(AdminSessionService.class));
        ResponseCookie cookie=ReflectionTestUtils.invokeMethod(controller,"cookie","",true);
        assertNotNull(cookie); assertEquals(Duration.ZERO,cookie.getMaxAge());
    }
}
package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminSessionAuthResponse;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminSessionDeadlineContractTest {
    @Test void restoreAndActivityUseSeparateCookieAuthenticatedRoutesAndNoStore() throws Exception {
        var sessions = mock(AdminSessionService.class);
        var controller = new AdminSessionAuthController(mock(AuthenticationManager.class), mock(UserRepository.class),
                mock(AdminMfaService.class), sessions);
        var now = Instant.now();
        var response = new AdminSessionAuthResponse("jwt", 60, "ok",
                new AdminSessionAuthResponse.SessionState("sid", now, now.plusSeconds(90), now.plusSeconds(3600), 300_000, now.plusSeconds(60)));
        when(sessions.restore("opaque")).thenReturn(response);
        when(sessions.refresh("opaque")).thenReturn(response);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var cookie = new Cookie("grun_admin_session", "opaque");
        mvc.perform(get("/api/v1/auth/admin/session").cookie(cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.adminSession.idleTimeoutMs").value(300_000))
                .andExpect(jsonPath("$.adminSession.sessionId").value("sid"))
                .andExpect(jsonPath("$.adminSession.serverTime").exists())
                .andExpect(jsonPath("$.adminSession.idleExpiresAt").exists())
                .andExpect(jsonPath("$.adminSession.absoluteExpiresAt").exists())
                .andExpect(jsonPath("$.adminSession.tokenExpiresAt").exists());
        verify(sessions, never()).refresh(anyString());
        mvc.perform(post("/api/v1/auth/admin/refresh").cookie(cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(sessions).refresh("opaque");
        // A JWT alone is not a replacement for the HttpOnly session cookie.
        assertThrows(IllegalArgumentException.class, () -> controller.session(new org.springframework.mock.web.MockHttpServletRequest()));
    }
}

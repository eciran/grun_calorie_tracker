package com.grun.calorietracker.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_whenUserDisabled_doesNotAuthenticateRequest() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(User.withUsername("user@example.com")
                        .password("encoded")
                        .disabled(true)
                        .roles("STANDARD")
                        .build());
        when(jwtUtil.isTokenValid("token", "user@example.com")).thenReturn(true);

        MockHttpServletRequest request = getWithBearerToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_whenUserLocked_doesNotAuthenticateRequest() throws Exception {
        when(jwtUtil.extractUsername("token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(User.withUsername("user@example.com")
                        .password("encoded")
                        .accountLocked(true)
                        .roles("STANDARD")
                        .build());
        when(jwtUtil.isTokenValid("token", "user@example.com")).thenReturn(true);

        MockHttpServletRequest request = getWithBearerToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest getWithBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        return request;
    }
}

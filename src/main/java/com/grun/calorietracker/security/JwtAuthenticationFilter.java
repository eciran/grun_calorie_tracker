package com.grun.calorietracker.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String REVENUECAT_WEBHOOK_PATH = "/api/v1/webhooks/revenuecat";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final com.grun.calorietracker.service.AdminSessionService adminSessionService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService,
                                   com.grun.calorietracker.service.AdminSessionService adminSessionService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.adminSessionService = adminSessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return REVENUECAT_WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        String username;
        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            boolean tokenValid;
            try {
                tokenValid = jwtUtil.isTokenValid(jwt, userDetails.getUsername());
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            boolean admin = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (tokenValid && admin) {
                String sessionId = jwtUtil.extractAdminSessionId(jwt);
                tokenValid = sessionId != null && adminSessionService.validateAndTouch(sessionId, username);
            }
            if (tokenValid && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}

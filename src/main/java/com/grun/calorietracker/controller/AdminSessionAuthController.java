package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AuthRequest;
import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.dto.LogoutResponseDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminSessionAuthController {
    static final String COOKIE_NAME="grun_admin_session";
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final AdminMfaService adminMfaService;
    private final AdminSessionService adminSessions;
    @Value("${grun.security.admin-session.secure-cookie:true}") private boolean secureCookie;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request,HttpServletRequest httpRequest,HttpServletResponse response) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(),request.getPassword()));
        UserEntity user=users.findByEmail(request.getEmail()).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if(user.getRole()==null || !user.getRole().isAdminRole()) throw new IllegalArgumentException("Admin account required.");
        adminMfaService.verifyLogin(user,request.getAdminMfaCode());
        AdminSessionService.AdminSessionLogin login=adminSessions.create(user, requestHeader(httpRequest, "User-Agent"), httpRequest.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(login.rawSessionToken(),false).toString());
        return ResponseEntity.ok(login.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) { return ResponseEntity.ok(adminSessions.refresh(requiredCookie(request))); }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDto> logout(HttpServletRequest request,HttpServletResponse response) {
        String raw=cookieValue(request); if(raw!=null) adminSessions.revoke(raw);
        response.addHeader(HttpHeaders.SET_COOKIE,cookie("",true).toString());
        return ResponseEntity.ok(new LogoutResponseDto("Logout successful"));
    }

    private String requestHeader(HttpServletRequest request,String name){String value=request.getHeader(name);return value==null?"":value;}
    private ResponseCookie cookie(String value,boolean clear){ ResponseCookie.ResponseCookieBuilder b=ResponseCookie.from(COOKIE_NAME,value).httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/v1/auth/admin"); if(clear)b.maxAge(0); return b.build(); }
    private String requiredCookie(HttpServletRequest r){String v=cookieValue(r);if(v==null)throw new IllegalArgumentException("Admin session is invalid or expired.");return v;}
    private String cookieValue(HttpServletRequest r){if(r.getCookies()==null)return null;for(Cookie c:r.getCookies())if(COOKIE_NAME.equals(c.getName()))return c.getValue();return null;}
}

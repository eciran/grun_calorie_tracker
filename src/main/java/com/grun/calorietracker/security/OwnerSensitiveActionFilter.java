package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.enums.AdminReauthenticationPurpose;
import com.grun.calorietracker.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OwnerSensitiveActionFilter extends OncePerRequestFilter {
    public static final String REAUTH_HEADER="X-Admin-Reauth-Token";
    private final JwtUtil jwtUtil;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_OWNER"))){chain.doFilter(req,res);return;}
        var owner=users.findByEmail(auth.getName()).orElse(null);
        String path=req.getRequestURI();
        if(owner==null){write(res,401,"OWNER_NOT_FOUND",null);return;}
        if(!Boolean.TRUE.equals(owner.getAdminMfaEnabled())){
            if(path.equals("/api/v1/admin/security/me") || path.startsWith("/api/v1/admin/security/mfa")){chain.doFilter(req,res);return;}
            write(res,403,"OWNER_MFA_ENROLLMENT_REQUIRED",null);return;
        }
        AdminReauthenticationPurpose purpose=requiredPurpose(req.getMethod(),path);
        if(purpose==null){chain.doFilter(req,res);return;}
        String proof=req.getHeader(REAUTH_HEADER);
        try {
            if(proof!=null && jwtUtil.isAdminReauthenticationTokenValid(proof,auth.getName(),purpose)){chain.doFilter(req,res);return;}
        } catch(JwtException|IllegalArgumentException ignored) {}
        write(res,428,"FRESH_OWNER_MFA_REQUIRED",purpose);
    }

    static AdminReauthenticationPurpose requiredPurpose(String method,String path){
        if("OPTIONS".equals(method))return null;
        if(("GET".equals(method)||"HEAD".equals(method))
                && path.contains("/export")
                && (path.contains("/legal")||path.contains("/gdpr")||path.contains("/audits"))){
            return AdminReauthenticationPurpose.COMPLIANCE;
        }
        if("GET".equals(method)||"HEAD".equals(method))return null;
        if(path.startsWith("/api/v1/admin/security/team"))return AdminReauthenticationPurpose.ADMIN_TEAM;
        if(path.startsWith("/api/v1/admin/security/mfa/disable")||path.startsWith("/api/v1/admin/security/sessions")||path.startsWith("/api/v1/admin/security/owner-sessions"))return AdminReauthenticationPurpose.OWNER_SECURITY;
        if(path.contains("feature")||path.contains("entitlement"))return AdminReauthenticationPurpose.FEATURE_ENTITLEMENT;
        if(path.contains("ai-credit-pricing")||path.contains("/ai/monitoring/policy")||path.contains("/ai/quota")||path.contains("/ai/credits"))return AdminReauthenticationPurpose.AI_POLICY;
        if(path.contains("subscription")||path.contains("revenuecat"))return AdminReauthenticationPurpose.SUBSCRIPTION_POLICY;
        if(path.contains("/system")||path.contains("/mail")||path.contains("/brevo")||path.contains("/push"))return AdminReauthenticationPurpose.RUNTIME_CONFIGURATION;
        if(path.contains("notification-campaign"))return AdminReauthenticationPurpose.NOTIFICATION_CAMPAIGN;
        if(path.contains("/legal")||path.contains("/gdpr")||path.contains("/audits"))return AdminReauthenticationPurpose.COMPLIANCE;
        if(path.contains("/approvals/"))return AdminReauthenticationPurpose.APPROVAL_DECISION;
        return null;
    }
    private void write(HttpServletResponse res,int status,String code,AdminReauthenticationPurpose purpose)throws IOException{res.setStatus(status);res.setContentType(MediaType.APPLICATION_JSON_VALUE);objectMapper.writeValue(res.getOutputStream(),Map.of("code",code,"message",code.equals("OWNER_MFA_ENROLLMENT_REQUIRED")?"Owner MFA enrollment is required.":"Fresh owner MFA re-authentication is required.","requiredPurpose",purpose==null?"":purpose.name()));}
}


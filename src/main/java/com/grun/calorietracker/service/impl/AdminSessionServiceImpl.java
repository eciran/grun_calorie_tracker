package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSessionDto;
import com.grun.calorietracker.dto.AdminSessionPageDto;
import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.dto.OwnerAdminSessionDto;
import com.grun.calorietracker.dto.OwnerAdminSessionPageDto;
import com.grun.calorietracker.entity.AdminSessionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.AdminSessionRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminSessionServiceImpl implements AdminSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AdminSessionRepository sessions;
    private final JwtUtil jwtUtil;
    @Autowired(required=false) private AdminAuditService auditService;
    @Autowired(required=false) private NotificationRepository notifications;
    @Autowired(required=false) private UserRepository users;
    @Value("${grun.security.owner-bootstrap.primary-email:}") private String primaryOwnerEmail;
    @Value("${grun.security.admin-session.idle-timeout:15m}") private Duration idleTimeout;
    @Value("${grun.security.admin-session.absolute-timeout:8h}") private Duration absoluteTimeout;

    @Override public AdminSessionLogin create(UserEntity user) { return create(user,"",""); }

    @Override @Transactional
    public AdminSessionLogin create(UserEntity user,String userAgent,String remoteAddress) {
        if (user.getRole() == null || !user.getRole().isAdminRole()) throw new IllegalArgumentException("Admin account required.");
        Instant now=Instant.now(); String raw=randomToken();
        AdminSessionEntity session=new AdminSessionEntity();
        session.setId(UUID.randomUUID().toString()); session.setUser(user); session.setSessionTokenHash(hash(raw));
        session.setCreatedAt(now); session.setLastActivityAt(now); session.setAbsoluteExpiresAt(now.plus(absoluteTimeout));
        session.setDeviceLabel(deviceLabel(userAgent)); session.setMaskedIp(maskIp(remoteAddress));
        var existingSessions=sessions.findByUserAndRevokedAtIsNull(user);
        boolean unfamiliar=existingSessions==null || existingSessions.stream().noneMatch(existing->safe(existing.getDeviceLabel(),"").equals(session.getDeviceLabel())&&safe(existing.getMaskedIp(),"").equals(session.getMaskedIp()));
        sessions.save(session);
        if(auditService!=null)auditService.record(user.getEmail(),AdminAuditActionType.ADMIN_SESSION_CREATE,AdminAuditTargetType.ADMIN_SESSION,session.getId(),null,Map.of("device",session.getDeviceLabel(),"maskedIp",session.getMaskedIp()),null);
        boolean recovery="root-owner@gruncalorietracker.com".equalsIgnoreCase(user.getEmail());
        if(recovery&&auditService!=null)auditService.record(user.getEmail(),AdminAuditActionType.RECOVERY_OWNER_LOGIN,AdminAuditTargetType.ADMIN_ACCOUNT,user.getEmail(),null,Map.of("device",session.getDeviceLabel(),"maskedIp",session.getMaskedIp()),null);
        if(recovery||unfamiliar)notifyPrimaryOwner(recovery?"Recovery owner account used":"Unfamiliar owner sign-in",recovery?"The break-glass recovery owner created a new admin session.":"A new device or masked network was observed for an admin session.",session);
        return new AdminSessionLogin(response(session,"Admin login successful"),raw);
    }

    @Override @Transactional
    public AuthResponse refresh(String raw) {
        AdminSessionEntity session=sessions.findBySessionTokenHashAndRevokedAtIsNull(hash(raw))
                .orElseThrow(() -> new IllegalArgumentException("Admin session is invalid or expired."));
        requireUsable(session, Instant.now()); session.setLastActivityAt(Instant.now());
        return response(session,"Admin session refreshed");
    }

    @Override @Transactional
    public boolean validateAndTouch(String id,String email) {
        return sessions.findById(id).filter(s -> s.getUser().getEmail().equalsIgnoreCase(email)).map(s -> {
            try { requireUsable(s,Instant.now()); s.setLastActivityAt(Instant.now()); return true; }
            catch (IllegalArgumentException ex) { return false; }
        }).orElse(false);
    }

    @Override @Transactional(readOnly=true)
    public AdminSessionPageDto list(String email,String currentSessionId,int page,int size) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid session page request.");
        AdminSessionEntity current=sessions.findById(currentSessionId)
                .filter(s->s.getUser().getEmail().equalsIgnoreCase(email))
                .orElseThrow(()->new IllegalArgumentException("Current admin session was not found."));
        var result=sessions.findByUserAndRevokedAtIsNullOrderByCreatedAtDesc(current.getUser(),PageRequest.of(page,size));
        var content=result.getContent().stream().map(s->new AdminSessionDto(s.getId(),safe(s.getDeviceLabel(),"Unknown browser"),safe(s.getMaskedIp(),"Unknown"),s.getCreatedAt(),s.getLastActivityAt(),s.getLastActivityAt().plus(idleTimeout),s.getAbsoluteExpiresAt(),s.getId().equals(currentSessionId))).toList();
        return new AdminSessionPageDto(content,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.isFirst(),result.isLast());
    }

    @Override @Transactional(readOnly=true)
    public OwnerAdminSessionPageDto listAllForOwner(String actorEmail,String currentSessionId,int page,int size) {
        if(page<0||size<1||size>100)throw new IllegalArgumentException("Invalid session page request.");
        AdminSessionEntity current=ownedSession(actorEmail,currentSessionId);
        if(current.getUser().getRole()!=com.grun.calorietracker.enums.UserRole.OWNER)throw new org.springframework.security.access.AccessDeniedException("Owner account required.");
        Instant now=Instant.now();
        var result=sessions.findActiveSessions(now,now.minus(idleTimeout),PageRequest.of(page,size));
        var content=result.getContent().stream().map(s->new OwnerAdminSessionDto(s.getId(),s.getUser().getEmail(),s.getUser().getRole().name(),safe(s.getDeviceLabel(),"Unknown browser"),safe(s.getMaskedIp(),"Unknown"),s.getCreatedAt(),s.getLastActivityAt(),s.getLastActivityAt().plus(idleTimeout),s.getAbsoluteExpiresAt(),s.getId().equals(currentSessionId))).toList();
        return new OwnerAdminSessionPageDto(content,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.isFirst(),result.isLast());
    }

    @Override @Transactional
    public void revokeAnyForOwner(String actorEmail,String sessionId,String currentSessionId,String reason,String correlationId) {
        AdminSessionEntity current=ownedSession(actorEmail,currentSessionId);
        if(current.getUser().getRole()!=com.grun.calorietracker.enums.UserRole.OWNER)throw new org.springframework.security.access.AccessDeniedException("Owner account required.");
        if(sessionId.equals(currentSessionId))throw new IllegalArgumentException("Use logout to revoke the current owner session.");
        AdminSessionEntity target=sessions.findById(sessionId).filter(s->s.getRevokedAt()==null).orElseThrow(()->new IllegalArgumentException("Admin session was not found."));
        target.setRevokedAt(Instant.now());
        if(auditService!=null)auditService.record(actorEmail,AdminAuditActionType.ADMIN_SESSION_REVOKE,AdminAuditTargetType.ADMIN_SESSION,sessionId,null,Map.of("reason",reason,"targetAdmin",target.getUser().getEmail(),"targetRole",target.getUser().getRole().name()),correlationId);
    }

    @Override @Transactional
    public void revokeSession(String actorEmail,String sessionId,String currentSessionId,String reason,String correlationId) {
        if(sessionId.equals(currentSessionId)) throw new IllegalArgumentException("Use logout to revoke the current admin session.");
        AdminSessionEntity target=ownedSession(actorEmail,sessionId);
        target.setRevokedAt(Instant.now());
        audit(actorEmail,AdminAuditActionType.ADMIN_SESSION_REVOKE,sessionId,reason,correlationId);
    }

    @Override @Transactional
    public void revokeOtherSessions(String actorEmail,String currentSessionId,String reason,String correlationId) {
        AdminSessionEntity current=ownedSession(actorEmail,currentSessionId); int revoked=0; Instant now=Instant.now();
        for(AdminSessionEntity session:sessions.findByUserAndRevokedAtIsNull(current.getUser())) if(!session.getId().equals(currentSessionId)){session.setRevokedAt(now);revoked++;}
        audit(actorEmail,AdminAuditActionType.ADMIN_OTHER_SESSIONS_REVOKE,currentSessionId,reason+" (revoked="+revoked+")",correlationId);
    }

    @Override @Transactional public void revoke(String raw) { sessions.findBySessionTokenHashAndRevokedAtIsNull(hash(raw)).ifPresent(s -> s.setRevokedAt(Instant.now())); }
    @Override @Transactional public void revokeAllForUser(UserEntity user) { sessions.findByUserAndRevokedAtIsNull(user).forEach(s -> s.setRevokedAt(Instant.now())); }

    private AdminSessionEntity ownedSession(String email,String id){return sessions.findById(id).filter(s->s.getRevokedAt()==null&&s.getUser().getEmail().equalsIgnoreCase(email)).orElseThrow(()->new IllegalArgumentException("Admin session was not found."));}
    private void notifyPrimaryOwner(String title,String message,AdminSessionEntity session){if(notifications==null||users==null||primaryOwnerEmail==null||primaryOwnerEmail.isBlank())return;users.findByEmail(primaryOwnerEmail).ifPresent(primary->{NotificationEntity n=new NotificationEntity();n.setUser(primary);n.setTitle(title);n.setMessage(message);n.setNote("Device: "+session.getDeviceLabel()+" | Network: "+session.getMaskedIp());n.setType("admin_security_alert");n.setSeverity("CRITICAL");n.setSource("ADMIN_SECURITY");n.setTargetType("ADMIN_SESSION");n.setTargetId(session.getId());n.setTargetRoute("admins");n.setVisibleInApp(true);n.setIsRead(false);n.setCreatedAt(LocalDateTime.now());notifications.save(n);});}
    private void audit(String email,AdminAuditActionType action,String id,String reason,String correlationId){if(auditService!=null)auditService.record(email,action,AdminAuditTargetType.ADMIN_SESSION,id,null,Map.of("reason",reason),correlationId);}
    private void requireUsable(AdminSessionEntity s,Instant now) { UserEntity u=s.getUser(); if(s.getRevokedAt()!=null || !s.getAbsoluteExpiresAt().isAfter(now) || !s.getLastActivityAt().plus(idleTimeout).isAfter(now) || !Boolean.TRUE.equals(u.getAccountEnabled()) || Boolean.TRUE.equals(u.getAccountLocked()) || u.getRole()==null || !u.getRole().isAdminRole()) { if(s.getRevokedAt()==null) s.setRevokedAt(now); throw new IllegalArgumentException("Admin session is invalid or expired."); } }
    private AuthResponse response(AdminSessionEntity s,String message) { String jwt=jwtUtil.generateAdminSessionToken(s.getUser().getEmail(),s.getId(),s.getAbsoluteExpiresAt()); return new AuthResponse(jwt,null,"Bearer",jwtUtil.getExpirationSeconds(),message); }
    private String deviceLabel(String ua){if(ua==null||ua.isBlank())return "Unknown browser";String browser=ua.contains("Edg/")?"Edge":ua.contains("Chrome/")?"Chrome":ua.contains("Firefox/")?"Firefox":ua.contains("Safari/")?"Safari":"Other browser";String os=ua.contains("Windows")?"Windows":ua.contains("Mac OS")?"macOS":ua.contains("Android")?"Android":ua.contains("iPhone")||ua.contains("iPad")?"iOS":ua.contains("Linux")?"Linux":"Unknown OS";return browser+" on "+os;}
    static String maskIp(String ip){if(ip==null||ip.isBlank())return "Unknown";String value=ip.split(",")[0].trim();if(value.contains(".")){String[] p=value.split("\\.");return p.length==4?p[0]+"."+p[1]+"."+p[2]+".x":"Masked";}if(value.contains(":")){String[] p=value.split(":");return (p.length>1?p[0]+":"+p[1]:p[0])+":…";}return "Masked";}
    private String safe(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private String hash(String raw){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
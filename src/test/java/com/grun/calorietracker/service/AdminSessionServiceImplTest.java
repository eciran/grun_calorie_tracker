package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.AdminSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.AdminSessionRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.impl.AdminSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSessionServiceImplTest {
    @Mock AdminSessionRepository sessions;
    @Mock JwtUtil jwtUtil;
    @Mock AdminAuditService auditService;
    AdminSessionServiceImpl service;

    @BeforeEach void setup(){ service=new AdminSessionServiceImpl(sessions,jwtUtil); ReflectionTestUtils.setField(service,"auditService",auditService); ReflectionTestUtils.setField(service,"idleTimeout",Duration.ofMinutes(15)); ReflectionTestUtils.setField(service,"absoluteTimeout",Duration.ofHours(8)); }

    @Test void createsHashedServerSideSession(){ UserEntity owner=user(); when(jwtUtil.getExpirationSeconds()).thenReturn(3600L); when(jwtUtil.generateAdminSessionToken(anyString(),anyString(),any())).thenReturn("jwt"); var login=service.create(owner); assertNull(login.response().getRefreshToken()); verify(sessions).save(argThat(s -> !s.getSessionTokenHash().equals(login.rawSessionToken()) && s.getAbsoluteExpiresAt().isAfter(s.getCreatedAt()))); }

    @Test void absoluteExpiredSessionIsRevokedImmediately(){ AdminSessionEntity session=session(Instant.now(),Instant.now().minusSeconds(1)); when(sessions.findById("sid")).thenReturn(Optional.of(session)); assertFalse(service.validateAndTouch("sid","owner@test.com")); assertNotNull(session.getRevokedAt()); }

    @Test void disabledOwnerSessionIsRevokedImmediately(){ AdminSessionEntity session=session(Instant.now(),Instant.now().plusSeconds(60)); session.getUser().setAccountEnabled(false); when(sessions.findById("sid")).thenReturn(Optional.of(session)); assertFalse(service.validateAndTouch("sid","owner@test.com")); assertNotNull(session.getRevokedAt()); }

    @Test void roleRemovalRevokesSessionImmediately(){ AdminSessionEntity session=session(Instant.now(),Instant.now().plusSeconds(60)); session.getUser().setRole(UserRole.STANDARD); when(sessions.findById("sid")).thenReturn(Optional.of(session)); assertFalse(service.validateAndTouch("sid","owner@test.com")); assertNotNull(session.getRevokedAt()); }

    @Test void idleSessionIsRevokedImmediately(){ AdminSessionEntity session=session(Instant.now().minus(Duration.ofMinutes(16)),Instant.now().plusSeconds(60)); when(sessions.findById("sid")).thenReturn(Optional.of(session)); assertFalse(service.validateAndTouch("sid","owner@test.com")); assertNotNull(session.getRevokedAt()); }

    @Test void revokedSessionCannotRefresh(){ AdminSessionEntity session=session(Instant.now(),Instant.now().plusSeconds(60)); session.setRevokedAt(Instant.now()); when(sessions.findBySessionTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.empty()); assertThrows(IllegalArgumentException.class,()->service.refresh("raw")); }

    @Test void cannotRevokeCurrentSessionThroughRemoteRevoke(){ AdminSessionEntity current=session(Instant.now(),Instant.now().plusSeconds(3600)); assertThrows(IllegalArgumentException.class,()->service.revokeSession("owner@test.com","sid","sid","reason","cid")); assertNull(current.getRevokedAt()); }

    @Test void revokedSessionFailsNextAuthenticatedRequest(){ AdminSessionEntity target=session(Instant.now(),Instant.now().plusSeconds(3600)); target.setId("other"); when(sessions.findById("other")).thenReturn(Optional.of(target)); service.revokeSession("owner@test.com","other","sid","incident","cid"); assertNotNull(target.getRevokedAt()); when(sessions.findById("other")).thenReturn(Optional.of(target)); assertFalse(service.validateAndTouch("other","owner@test.com")); }

    @Test void revokeOtherSessionsKeepsCurrentSession(){ AdminSessionEntity current=session(Instant.now(),Instant.now().plusSeconds(3600)); AdminSessionEntity other=session(Instant.now(),Instant.now().plusSeconds(3600)); other.setId("other"); when(sessions.findById("sid")).thenReturn(Optional.of(current)); when(sessions.findByUserAndRevokedAtIsNull(current.getUser())).thenReturn(List.of(current,other)); service.revokeOtherSessions("owner@test.com","sid","security review","cid"); assertNull(current.getRevokedAt()); assertNotNull(other.getRevokedAt()); }

    @Test void ownerCanRevokeAnotherAdminsSession(){
        AdminSessionEntity current=session(Instant.now(),Instant.now().plusSeconds(3600));
        AdminSessionEntity target=session(Instant.now(),Instant.now().plusSeconds(3600)); target.setId("target"); target.getUser().setEmail("admin@test.com"); target.getUser().setRole(UserRole.ADMIN_SUPPORT);
        when(sessions.findById("sid")).thenReturn(Optional.of(current)); when(sessions.findById("target")).thenReturn(Optional.of(target));
        service.revokeAnyForOwner("owner@test.com","target","sid","security incident","cid");
        assertNotNull(target.getRevokedAt());
        verify(auditService).record(eq("owner@test.com"),eq(com.grun.calorietracker.enums.AdminAuditActionType.ADMIN_SESSION_REVOKE),eq(com.grun.calorietracker.enums.AdminAuditTargetType.ADMIN_SESSION),eq("target"),isNull(),anyMap(),eq("cid"));
    }

    @Test void nonOwnerCannotUseCentralSessionRevocation(){
        AdminSessionEntity current=session(Instant.now(),Instant.now().plusSeconds(3600)); current.getUser().setRole(UserRole.ADMIN_SUPPORT);
        when(sessions.findById("sid")).thenReturn(Optional.of(current));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.revokeAnyForOwner("owner@test.com","target","sid","reason","cid"));
        verify(sessions,never()).findById("target");
    }

    private UserEntity user(){UserEntity u=new UserEntity();u.setId(1L);u.setEmail("owner@test.com");u.setRole(UserRole.OWNER);u.setAccountEnabled(true);u.setAccountLocked(false);return u;}
    private AdminSessionEntity session(Instant activity,Instant absolute){AdminSessionEntity s=new AdminSessionEntity();s.setId("sid");s.setUser(user());s.setCreatedAt(activity);s.setLastActivityAt(activity);s.setAbsoluteExpiresAt(absolute);return s;}
}

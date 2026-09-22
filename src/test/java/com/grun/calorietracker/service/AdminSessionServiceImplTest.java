package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.AdminSessionEntity;
import com.grun.calorietracker.dto.AdminSessionAuthResponse;
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

    @Test void newLoginRevokesEveryPreviousSessionForTheSameAdmin(){
        UserEntity owner=user();
        AdminSessionEntity first=session(Instant.now().minusSeconds(30),Instant.now().plusSeconds(3600));
        AdminSessionEntity second=session(Instant.now().minusSeconds(20),Instant.now().plusSeconds(3600)); second.setId("second");
        when(sessions.findByUserAndRevokedAtIsNull(owner)).thenReturn(List.of(first,second));
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(jwtUtil.generateAdminSessionToken(anyString(),anyString(),any())).thenReturn("jwt");

        var login=service.create(owner,"Safari iPhone","192.168.1.24");

        assertNotNull(first.getRevokedAt());
        assertNotNull(second.getRevokedAt());
        var response=(AdminSessionAuthResponse)login.response();
        assertNotEquals("sid",response.getAdminSession().sessionId());
        assertNotEquals("second",response.getAdminSession().sessionId());
        verify(sessions).save(argThat(created -> created.getRevokedAt()==null && created.getUser()==owner));
        verify(auditService).record(eq("owner@test.com"),eq(com.grun.calorietracker.enums.AdminAuditActionType.ADMIN_SESSION_CREATE),eq(com.grun.calorietracker.enums.AdminAuditTargetType.ADMIN_SESSION),anyString(),isNull(),anyMap(),isNull());
    }

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

    @Test void authenticatedBackgroundTrafficDoesNotExtendIdleDeadline() {
        Instant lastActivity = Instant.now().minusSeconds(600);
        AdminSessionEntity current = session(lastActivity, Instant.now().plusSeconds(3600));
        when(sessions.findById("sid")).thenReturn(Optional.of(current));
        for (int i = 0; i < 20; i++) assertTrue(service.validateAndTouch("sid", "owner@test.com"));
        assertEquals(lastActivity, current.getLastActivityAt());
        assertFalse(service.validateAndTouch("sid", "another@test.com"));
        assertEquals(lastActivity, current.getLastActivityAt());
    }

    @Test void restoreReturnsConfiguredRemainingDeadlinesWithoutTouchingActivity() {
        ReflectionTestUtils.setField(service, "idleTimeout", Duration.ofMinutes(5));
        Instant lastActivity = Instant.now().minusSeconds(240);
        Instant absolute = Instant.now().plusSeconds(50);
        AdminSessionEntity current = session(lastActivity, absolute);
        when(sessions.findBySessionTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.of(current));
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(jwtUtil.generateAdminSessionToken(anyString(), anyString(), any())).thenReturn("jwt");
        var response = (AdminSessionAuthResponse) service.restore("raw");
        assertEquals(lastActivity, current.getLastActivityAt());
        assertEquals(lastActivity.plusSeconds(300), response.getAdminSession().idleExpiresAt());
        assertEquals(absolute, response.getAdminSession().absoluteExpiresAt());
        assertEquals(300_000, response.getAdminSession().idleTimeoutMs());
        assertEquals("sid", response.getAdminSession().sessionId());
        assertTrue(response.getExpiresIn() <= 50);
        assertFalse(response.getAdminSession().tokenExpiresAt().isAfter(absolute));
        assertNull(response.getRefreshToken());
    }

    @Test void explicitActivityRefreshExtendsIdleButNeverAbsoluteExpiry() {
        Instant previous = Instant.now().minusSeconds(600);
        Instant absolute = Instant.now().plusSeconds(3600);
        AdminSessionEntity current = session(previous, absolute);
        when(sessions.findBySessionTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.of(current));
        var response = (AdminSessionAuthResponse) service.refresh("raw");
        assertTrue(current.getLastActivityAt().isAfter(previous));
        assertEquals(absolute, current.getAbsoluteExpiresAt());
        assertEquals(current.getLastActivityAt().plusSeconds(900), response.getAdminSession().idleExpiresAt());
    }

    @Test void expiredIdleOrAbsoluteSessionCannotBeRestoredOrRefreshed() {
        for (boolean idleExpired : new boolean[]{true, false}) {
            AdminSessionEntity current = session(
                    Instant.now().minusSeconds(idleExpired ? 901 : 1),
                    Instant.now().plusSeconds(idleExpired ? 3600 : -1));
            Instant previous = current.getLastActivityAt();
            when(sessions.findBySessionTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.of(current));
            assertThrows(IllegalArgumentException.class, () -> service.restore("raw"));
            assertThrows(IllegalArgumentException.class, () -> service.refresh("raw"));
            assertEquals(previous, current.getLastActivityAt());
        }
    }

    @Test void disabledLockedRevokedAndDemotedAccountsCannotRestoreOrRefresh() {
        for (int failure = 0; failure < 4; failure++) {
            AdminSessionEntity current = session(Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600));
            if (failure == 0) current.getUser().setAccountEnabled(false);
            if (failure == 1) current.getUser().setAccountLocked(true);
            if (failure == 2) current.setRevokedAt(Instant.now());
            if (failure == 3) current.getUser().setRole(UserRole.STANDARD);
            when(sessions.findBySessionTokenHashAndRevokedAtIsNull(anyString())).thenReturn(Optional.of(current));
            assertThrows(IllegalArgumentException.class, () -> service.restore("raw"));
            assertThrows(IllegalArgumentException.class, () -> service.refresh("raw"));
        }
    }

    private UserEntity user(){UserEntity u=new UserEntity();u.setId(1L);u.setEmail("owner@test.com");u.setRole(UserRole.OWNER);u.setAccountEnabled(true);u.setAccountLocked(false);return u;}
    private AdminSessionEntity session(Instant activity,Instant absolute){AdminSessionEntity s=new AdminSessionEntity();s.setId("sid");s.setUser(user());s.setCreatedAt(activity);s.setLastActivityAt(activity);s.setAbsoluteExpiresAt(absolute);return s;}
}

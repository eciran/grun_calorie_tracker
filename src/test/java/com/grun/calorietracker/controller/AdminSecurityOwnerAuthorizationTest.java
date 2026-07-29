package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAccessGrantRequestDto;
import com.grun.calorietracker.dto.AdminTeamMemberUpdateRequestDto;
import com.grun.calorietracker.dto.AdminSessionRevokeRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminSecurityOwnerAuthorizationTest {

    @Test
    void adminTeamMutationsRequireOwnerRole() throws Exception {
        Method grant = AdminSecurityController.class.getMethod(
                "grantAccess", AdminAccessGrantRequestDto.class, UserDetails.class, HttpServletRequest.class);
        Method update = AdminSecurityController.class.getMethod(
                "updateMember", Long.class, AdminTeamMemberUpdateRequestDto.class,
                UserDetails.class, HttpServletRequest.class);

        assertEquals("hasRole('OWNER')", grant.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('OWNER')", update.getAnnotation(PreAuthorize.class).value());

        Method revoke = AdminSecurityController.class.getMethod("revokeSession", String.class, AdminSessionRevokeRequestDto.class, UserDetails.class, HttpServletRequest.class);
        Method revokeOthers = AdminSecurityController.class.getMethod("revokeOthers", AdminSessionRevokeRequestDto.class, UserDetails.class, HttpServletRequest.class);
        assertEquals("hasRole('OWNER')", revoke.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('OWNER')", revokeOthers.getAnnotation(PreAuthorize.class).value());

        Method ownerSessions = AdminSecurityController.class.getMethod("ownerSessions", int.class, int.class, UserDetails.class, HttpServletRequest.class);
        Method revokeAny = AdminSecurityController.class.getMethod("revokeAnySession", String.class, AdminSessionRevokeRequestDto.class, UserDetails.class, HttpServletRequest.class);
        assertEquals("hasRole('OWNER')", ownerSessions.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('OWNER')", revokeAny.getAnnotation(PreAuthorize.class).value());
    }
}

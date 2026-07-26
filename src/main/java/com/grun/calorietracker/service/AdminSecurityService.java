package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAccessProfileDto;
import com.grun.calorietracker.dto.AdminAccessGrantRequestDto;
import com.grun.calorietracker.dto.AdminTeamMemberDto;
import com.grun.calorietracker.dto.AdminTeamMemberUpdateRequestDto;
import com.grun.calorietracker.dto.AdminTeamPageDto;

public interface AdminSecurityService {
    AdminAccessProfileDto currentAccess(String email);

    AdminTeamPageDto listTeam(int page, int size);

    AdminTeamMemberDto grantAccess(String actorEmail, AdminAccessGrantRequestDto request, String correlationId);

    AdminTeamMemberDto updateMember(
            String actorEmail,
            Long userId,
            AdminTeamMemberUpdateRequestDto request,
            String correlationId
    );
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OwnerErrorGroupStateRequestDto;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service @RequiredArgsConstructor
public class OwnerErrorGroupLifecycleService {
    private final OwnerErrorStore store; private final JwtUtil jwtUtil; private final AdminAuditService auditService;
    @Transactional
    public void update(String fingerprint,OwnerErrorGroupStateRequestDto request,String owner,String token,String correlationId){
        if(fingerprint==null||!fingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid error group fingerprint.");
        if(owner==null||token==null||!jwtUtil.isAdminReauthenticationTokenValid(token,owner,AdminReauthenticationPurpose.OWNER_ERROR_ACTION))throw new IllegalArgumentException("Fresh owner MFA re-authentication is required.");
        var identity=new OwnerErrorStore.GroupIdentity(request.source(),request.status(),request.method(),request.route(),blank(request.errorCode()));
        if(!fingerprint.equals(OwnerErrorStore.fingerprint(identity))||!store.groupExists(identity))throw new IllegalArgumentException("Error group was not found.");
        String old=store.lifecycle(fingerprint).orElse("NEW"), next=request.lifecycleStatus();
        if(!allowed(old,next))throw new IllegalArgumentException("Invalid error group state transition.");
        String reason=request.reason().trim(); store.saveLifecycle(fingerprint,identity,old,next,reason,owner,Instant.now());
        auditService.record(owner,AdminAuditActionType.OWNER_ERROR_GROUP_STATUS_UPDATE,AdminAuditTargetType.OWNER_ERROR_GROUP,fingerprint,
                Map.of("status",old),Map.of("status",next,"reason",reason),correlationId);
    }
    private boolean allowed(String old,String next){return switch(old){case "NEW"->next.equals("INVESTIGATING");case "INVESTIGATING","REOPENED"->next.equals("RESOLVED");case "RESOLVED"->next.equals("REOPENED");default->false;};}
    private String blank(String value){return value==null||value.isBlank()?null:value;}
}

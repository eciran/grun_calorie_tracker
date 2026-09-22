package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OwnerErrorGroupStateRequestDto;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnerErrorGroupLifecycleServiceTest {
    private final OwnerErrorStore store=mock(OwnerErrorStore.class); private final JwtUtil jwt=mock(JwtUtil.class); private final AdminAuditService audit=mock(AdminAuditService.class);
    private final OwnerErrorGroupLifecycleService service=new OwnerErrorGroupLifecycleService(store,jwt,audit);
    private final OwnerErrorStore.GroupIdentity identity=new OwnerErrorStore.GroupIdentity("BACKEND",500,"POST","/api/v1/products/{id}","UNEXPECTED_ERROR");
    private OwnerErrorGroupStateRequestDto request(String status){return new OwnerErrorGroupStateRequestDto(status,"Incident investigation started",identity.source(),identity.status(),identity.method(),identity.route(),identity.errorCode());}
    @Test void newGroupMovesToInvestigatingWithPurposeBoundProofAndAudit(){String fingerprint=OwnerErrorStore.fingerprint(identity);when(jwt.isAdminReauthenticationTokenValid("proof","owner@example.com",AdminReauthenticationPurpose.OWNER_ERROR_ACTION)).thenReturn(true);when(store.groupExists(identity)).thenReturn(true);when(store.lifecycle(fingerprint)).thenReturn(Optional.empty());service.update(fingerprint,request("INVESTIGATING"),"owner@example.com","proof","cid");verify(store).saveLifecycle(eq(fingerprint),eq(identity),eq("NEW"),eq("INVESTIGATING"),eq("Incident investigation started"),eq("owner@example.com"),any());verify(audit).record(eq("owner@example.com"),eq(AdminAuditActionType.OWNER_ERROR_GROUP_STATUS_UPDATE),eq(AdminAuditTargetType.OWNER_ERROR_GROUP),eq(fingerprint),any(),any(),eq("cid"));}
    @Test void invalidTransitionAndForgedFingerprintAreRejected(){when(jwt.isAdminReauthenticationTokenValid(any(),any(),eq(AdminReauthenticationPurpose.OWNER_ERROR_ACTION))).thenReturn(true);String fingerprint=OwnerErrorStore.fingerprint(identity);when(store.groupExists(identity)).thenReturn(true);when(store.lifecycle(fingerprint)).thenReturn(Optional.of("NEW"));assertThrows(IllegalArgumentException.class,()->service.update(fingerprint,request("RESOLVED"),"owner@example.com","proof","cid"));assertThrows(IllegalArgumentException.class,()->service.update("b".repeat(64),request("INVESTIGATING"),"owner@example.com","proof","cid"));verify(store,never()).saveLifecycle(any(),any(),any(),any(),any(),any(),any());}
    @Test void missingProofFailsBeforeReadingGroup(){assertThrows(IllegalArgumentException.class,()->service.update(OwnerErrorStore.fingerprint(identity),request("INVESTIGATING"),"owner@example.com","bad","cid"));verify(store,never()).groupExists(any());}
}

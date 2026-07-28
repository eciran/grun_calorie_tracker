package com.grun.calorietracker.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AdminApprovalRequestEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.AdminApprovalRequestRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminApprovalServiceImplTest {
 @Mock AdminApprovalRequestRepository repository; @Mock JwtUtil jwt; @Mock SubscriptionService subscriptions;
 @Mock AdminAiMealDraftService ai; @Mock AdminNotificationCampaignService campaigns; @Mock RuntimeOperationsService runtime; @Mock AdminAuditService audit;
 AdminApprovalServiceImpl service; ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @BeforeEach void setup(){MockitoAnnotations.openMocks(this);service=new AdminApprovalServiceImpl(repository,mapper,jwt,subscriptions,ai,campaigns,runtime,audit,Validation.buildDefaultValidatorFactory().getValidator());when(repository.save(any())).thenAnswer(i->{AdminApprovalRequestEntity e=i.getArgument(0);if(e.getId()==null)e.setId(1L);return e;});}
 @Test void createsWhitelistedPendingRequest(){var payload=mapper.createObjectNode().put("amount",5).put("validityDays",7).put("note","support");var dto=service.create("maker@grun.app",new AdminApprovalCreateRequestDto(AdminApprovalActionType.AI_ADDON_QUOTA_GRANT,"13",payload,"Support correction"),"cid");assertThat(dto.status()).isEqualTo(AdminApprovalStatus.PENDING);assertThat(dto.payload().fieldNames()).toIterable().containsExactlyInAnyOrder("amount","validityDays","note");}
 @Test void makerCannotApproveOwnRequest(){AdminApprovalRequestEntity e=pending("maker@grun.app");when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));assertThatThrownBy(()->service.approve(1L,"maker@grun.app","proof","ok","cid")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maker");verifyNoInteractions(subscriptions);}
 @Test void checkerNeedsFreshMfaProof(){AdminApprovalRequestEntity e=pending("maker@grun.app");when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("bad","owner@grun.app")).thenReturn(false);assertThatThrownBy(()->service.approve(1L,"owner@grun.app","bad","ok","cid")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("re-authentication");}
 private AdminApprovalRequestEntity pending(String maker){AdminApprovalRequestEntity e=new AdminApprovalRequestEntity();e.setId(1L);e.setActionType(AdminApprovalActionType.ENTITLEMENT_MATRIX_APPLY);e.setStatus(AdminApprovalStatus.PENDING);e.setMakerEmail(maker);e.setTargetKey("13");e.setPayloadJson("{}");e.setRequestReason("reason");e.setCreatedAt(Instant.now());e.setExpiresAt(Instant.now().plusSeconds(3600));return e;}
}
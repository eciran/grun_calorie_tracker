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
import org.springframework.context.ApplicationEventPublisher;
import com.grun.calorietracker.service.support.OwnerApprovalRequestedEvent;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminApprovalServiceImplTest {
 @Test void ownerRejectStillRequiresFreshMfa() {
   var entity = pending("owner@grun.app");
   when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
   assertThatThrownBy(() -> service.reject(1L,"owner@grun.app",true,"bad","reject","cid"))
       .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MFA");
   assertThat(entity.getStatus()).isEqualTo(AdminApprovalStatus.PENDING);
   verify(repository, never()).save(any());
 }
 @Test void expiredRejectIsNotExecuted() {
   var entity = pending("owner@grun.app");
   entity.setExpiresAt(Instant.now().minusSeconds(1));
   when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
   assertThatThrownBy(() -> service.reject(1L,"owner@grun.app",true,"proof","reject","cid"))
       .isInstanceOf(com.grun.calorietracker.exception.ApprovalDecisionException.Expired.class);
   assertThat(entity.getStatus()).isEqualTo(AdminApprovalStatus.EXPIRED);
   verify(repository).save(entity);
   verifyNoInteractions(subscriptions, audit);
 }
 @Test void expiredApproveIsNotExecuted() {
   var entity = pending("owner@grun.app");
   entity.setExpiresAt(Instant.now().minusSeconds(1));
   when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
   assertThatThrownBy(() -> service.approve(1L,"owner@grun.app",true,"proof","approve","cid"))
       .isInstanceOf(com.grun.calorietracker.exception.ApprovalDecisionException.Expired.class);
   verifyNoInteractions(subscriptions, audit);
 }
 @Test void expiryExceptionCommitsButOtherErrorsStillRollback() throws Exception {
   for (String method : new String[]{"approve", "reject"}) {
     var transaction = AdminApprovalServiceImpl.class.getMethod(method,Long.class,String.class,boolean.class,String.class,String.class,String.class)
         .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
     var rule = new org.springframework.transaction.interceptor.RuleBasedTransactionAttribute();
     rule.setRollbackRules(java.util.Arrays.stream(transaction.noRollbackFor())
         .map(type -> (org.springframework.transaction.interceptor.RollbackRuleAttribute)new org.springframework.transaction.interceptor.NoRollbackRuleAttribute(type)).toList());
     assertThat(rule.rollbackOn(new com.grun.calorietracker.exception.ApprovalDecisionException.Expired())).isFalse();
     assertThat(rule.rollbackOn(new IllegalArgumentException("bad MFA"))).isTrue();
     assertThat(rule.rollbackOn(new IllegalStateException("execution failed"))).isTrue();
   }
 }
 @Test void listingExpiresOldPendingRequestsBeforeReading() {
   when(repository.findByStatus(eq(AdminApprovalStatus.PENDING), any())).thenReturn(org.springframework.data.domain.Page.empty());
   service.list(AdminApprovalStatus.PENDING,0,10);
   var order = inOrder(repository);
   order.verify(repository).expirePending(any());
   order.verify(repository).findByStatus(eq(AdminApprovalStatus.PENDING),any());
 }
 @Mock AdminApprovalRequestRepository repository; @Mock JwtUtil jwt; @Mock SubscriptionService subscriptions;
 @Mock AdminAiMealDraftService ai; @Mock AdminNotificationCampaignService campaigns; @Mock RuntimeOperationsService runtime; @Mock AdminMealReminderAutomationService mealReminders; @Mock AdminSubscriptionNotificationService subscriptionNotifications; @Mock AdminPromoService promos; @Mock AdminAuditService audit; @Mock ApplicationEventPublisher events;
 AdminApprovalServiceImpl service; ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @BeforeEach void setup(){MockitoAnnotations.openMocks(this);service=new AdminApprovalServiceImpl(repository,mapper,jwt,subscriptions,ai,campaigns,runtime,mealReminders,subscriptionNotifications,promos,audit,Validation.buildDefaultValidatorFactory().getValidator(),events);when(repository.save(any())).thenAnswer(i->{AdminApprovalRequestEntity e=i.getArgument(0);if(e.getId()==null)e.setId(1L);return e;});}
 @Test void createsWhitelistedPendingRequest(){var payload=mapper.createObjectNode().put("amount",5).put("validityDays",7).put("note","support");var dto=service.create("maker@grun.app",new AdminApprovalCreateRequestDto(AdminApprovalActionType.AI_ADDON_QUOTA_GRANT,"13",payload,"Support correction"),"cid");assertThat(dto.status()).isEqualTo(AdminApprovalStatus.PENDING);assertThat(dto.payload().fieldNames()).toIterable().containsExactlyInAnyOrder("amount","validityDays","note");verify(events).publishEvent(argThat((Object event) -> event instanceof OwnerApprovalRequestedEvent requested && requested.approvalId()==1L && requested.actionType()==AdminApprovalActionType.AI_ADDON_QUOTA_GRANT));}
 @Test void nonOwnerMakerCannotApproveOwnRequest(){AdminApprovalRequestEntity e=pending("maker@grun.app");when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));assertThatThrownBy(()->service.approve(1L,"maker@grun.app",false,"proof","ok","cid")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");verifyNoInteractions(subscriptions);}
 @Test void ownerCanApproveOwnRequestWithFreshMfaProof(){AdminApprovalRequestEntity e=pending("owner@grun.app");when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("proof","owner@grun.app")).thenReturn(true);service.approve(1L,"owner@grun.app",true,"proof","ok","cid");verify(subscriptions).applyCurrentFeatureMatrixToUser(13L);}
 @Test void checkerNeedsFreshMfaProof(){AdminApprovalRequestEntity e=pending("maker@grun.app");when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("bad","owner@grun.app")).thenReturn(false);assertThatThrownBy(()->service.approve(1L,"owner@grun.app",true,"bad","ok","cid")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("re-authentication");}
 @Test void approvedMealReminderPublishUsesExistingCheckerGate(){AdminApprovalRequestEntity e=pending("maker@grun.app");e.setActionType(AdminApprovalActionType.MEAL_REMINDER_POLICY_PUBLISH);when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("proof","owner@grun.app")).thenReturn(true);service.approve(1L,"owner@grun.app",true,"proof","ok","cid");verify(mealReminders).publishApproved(13L,"owner@grun.app","cid",false);}
 @Test void approvedPromotionActivationExecutesOnlyAfterOwnerMfa(){AdminApprovalRequestEntity e=pending("finance@grun.app");e.setActionType(AdminApprovalActionType.PROMOTION_ACTIVATE);when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("proof","owner@grun.app")).thenReturn(true);service.approve(1L,"owner@grun.app",true,"proof","approved","cid");verify(promos).activate(13L,"owner@grun.app","cid");}
 @Test void promotionCreatePayloadIsValidatedAndExecutedByChecker(){var payload=mapper.createObjectNode().put("code","SAVE20").put("name","Save twenty").put("discountPercent",20).put("promoType","CAMPAIGN").put("targetStore","ALL").put("currency","EUR").put("eligibilityRule","ALL_USERS").put("perUserLimit",1).put("storeOfferCodeRequired",false);var created=service.create("finance@grun.app",new AdminApprovalCreateRequestDto(AdminApprovalActionType.PROMOTION_CREATE,"NEW",payload,"Commercial campaign"),"cid");assertThat(created.status()).isEqualTo(AdminApprovalStatus.PENDING);AdminApprovalRequestEntity e=pending("finance@grun.app");e.setActionType(AdminApprovalActionType.PROMOTION_CREATE);e.setTargetKey("NEW");e.setPayloadJson(created.payload().toString());when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));when(jwt.isAdminReauthenticationTokenValid("proof","owner@grun.app")).thenReturn(true);service.approve(1L,"owner@grun.app",true,"proof","approved","cid");verify(promos).create(any(AdminPromoRequestDto.class),eq("owner@grun.app"),eq("cid"));}
 private AdminApprovalRequestEntity pending(String maker){AdminApprovalRequestEntity e=new AdminApprovalRequestEntity();e.setId(1L);e.setActionType(AdminApprovalActionType.ENTITLEMENT_MATRIX_APPLY);e.setStatus(AdminApprovalStatus.PENDING);e.setMakerEmail(maker);e.setTargetKey("13");e.setPayloadJson("{}");e.setRequestReason("reason");e.setCreatedAt(Instant.now());e.setExpiresAt(Instant.now().plusSeconds(3600));return e;}
}

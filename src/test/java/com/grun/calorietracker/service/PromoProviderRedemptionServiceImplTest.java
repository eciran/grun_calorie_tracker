package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PromoProviderRedemptionCommand;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.PromoProviderRedemptionServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromoProviderRedemptionServiceImplTest {
    private PromoCodeRepository promos;
    private AppliedPromoRepository redemptions;
    private UserRepository users;
    private PromoProviderRedemptionServiceImpl service;
    private PromoCodeEntity promo;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        promos = mock(PromoCodeRepository.class);
        redemptions = mock(AppliedPromoRepository.class);
        users = mock(UserRepository.class);
        service = new PromoProviderRedemptionServiceImpl(promos, redemptions, users);
        promo = new PromoCodeEntity();
        promo.setId(8L); promo.setStatus(PromoStatus.ACTIVE); promo.setActive(true);
        promo.setProviderProductId("plus.monthly"); promo.setProviderOfferId("welcome");
        promo.setTargetStore(PromoStore.APPLE_APP_STORE); promo.setEligibilityRule(PromoEligibilityRule.ALL_USERS);
        promo.setPerUserLimit(1); promo.setUsedCount(0); promo.setCurrency("EUR");
        user = new UserEntity(); user.setId(4L); user.setEmail("person@example.com"); user.setMarketRegion(MarketRegion.UK_IE);
        when(promos.findActiveProviderCandidates(eq("plus.monthly"), any(LocalDateTime.class))).thenReturn(List.of(promo));
        when(promos.findByIdForUpdate(8L)).thenReturn(Optional.of(promo));
        when(users.findById(4L)).thenReturn(Optional.of(user));
        when(redemptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void verifiedPurchase_recordsConversionWithoutGrantingEntitlement() {
        service.recordVerifiedPurchase(command("event-1"));
        ArgumentCaptor<AppliedPromoEntity> saved = ArgumentCaptor.forClass(AppliedPromoEntity.class);
        verify(redemptions).save(saved.capture());
        assertEquals(PromoRedemptionStatus.CONVERTED, saved.getValue().getStatus());
        assertEquals("event-1", saved.getValue().getProviderEventId());
        assertEquals(1, promo.getUsedCount());
    }

    @Test
    void duplicateProviderEvent_onlyIncrementsAbuseSignal() {
        AppliedPromoEntity existing = new AppliedPromoEntity(); existing.setDuplicateHits(2);
        when(redemptions.findByProviderEventId("event-1")).thenReturn(Optional.of(existing));
        service.recordVerifiedPurchase(command("event-1"));
        assertEquals(3, existing.getDuplicateHits());
        assertNotNull(existing.getLastDuplicateAt());
        verify(promos, never()).findActiveProviderCandidates(anyString(), any());
    }

    @Test
    void exhaustedUserLimit_recordsRejectedAttribution() {
        when(redemptions.countConvertedForUser(4L, 8L)).thenReturn(1L);
        service.recordVerifiedPurchase(command("event-2"));
        ArgumentCaptor<AppliedPromoEntity> saved = ArgumentCaptor.forClass(AppliedPromoEntity.class);
        verify(redemptions).save(saved.capture());
        assertEquals(PromoRedemptionStatus.REJECTED, saved.getValue().getStatus());
        assertTrue(saved.getValue().getRejectionReason().startsWith("LIMIT:"));
        assertEquals(0, promo.getUsedCount());
    }

    @Test
    void storeMismatch_doesNotAttributePromotion() {
        PromoProviderRedemptionCommand google = new PromoProviderRedemptionCommand(4L, "event-3", "plus.monthly",
                "welcome", "PLAY_STORE", 499L, "EUR", SubscriptionPlan.FREE, null);
        service.recordVerifiedPurchase(google);
        verify(redemptions, never()).save(any());
    }

    private PromoProviderRedemptionCommand command(String eventId) {
        return new PromoProviderRedemptionCommand(4L, eventId, "plus.monthly", "welcome", "APP_STORE",
                499L, "eur", SubscriptionPlan.FREE, null);
    }
}

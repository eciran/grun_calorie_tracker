package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.PromoCodeEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdminPromoServiceImpl;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPromoServiceImplTest {
    private PromoCodeRepository promoRepository;
    private AppliedPromoRepository redemptionRepository;
    private UserRepository userRepository;
    private AdminAuditService auditService;
    private AdminPromoServiceImpl service;

    @BeforeEach
    void setUp() {
        promoRepository = mock(PromoCodeRepository.class);
        redemptionRepository = mock(AppliedPromoRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminPromoServiceImpl(promoRepository, redemptionRepository, userRepository, auditService);
    }

    @Test
    void create_normalizesDraftAndAuditsWithoutGrantingEntitlement() {
        when(promoRepository.save(any())).thenAnswer(invocation -> {
            PromoCodeEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        AdminPromoDto result = service.create(request(PromoStore.ALL), "admin@grun.local", "cid-1");

        assertEquals("WELCOME_20", result.code());
        assertEquals(PromoStatus.DRAFT, result.status());
        assertFalse(result.active());
        assertTrue(result.providerMappingReady());
        verify(auditService).record(eq("admin@grun.local"), eq(AdminAuditActionType.PROMO_CREATE),
                eq(AdminAuditTargetType.PROMOTION), eq("7"), isNull(), any(), eq("cid-1"));
    }

    @Test
    void activate_blocksStoreOfferWithoutProviderMapping() {
        PromoCodeEntity entity = entity(PromoStore.APPLE_APP_STORE);
        when(promoRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(entity));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.activate(3L, "admin@grun.local", "cid-2"));

        assertTrue(error.getMessage().contains("Provider offer"));
        verify(promoRepository, never()).save(any());
    }

    @Test
    void reconcile_disclosesEntitlementGuardrail() {
        PromoCodeEntity entity = entity(PromoStore.REVENUECAT);
        entity.setProviderOfferId("offer-1");
        entity.setProviderProductId("product-1");
        when(promoRepository.findById(3L)).thenReturn(Optional.of(entity));

        AdminPromoReconciliationDto result = service.reconcile(3L, "admin@grun.local", "cid-3");

        assertTrue(result.mappingReady());
        assertTrue(result.entitlementGuardrail().contains("never grant paid entitlement"));
    }

    private AdminPromoRequestDto request(PromoStore store) {
        AdminPromoRequestDto request = new AdminPromoRequestDto();
        request.setCode("welcome_20");
        request.setName("Welcome offer");
        request.setDiscountPercent(20D);
        request.setPromoType(PromoType.INTRO_OFFER);
        request.setTargetStore(store);
        request.setCurrency("eur");
        request.setPerUserLimit(1);
        return request;
    }

    private PromoCodeEntity entity(PromoStore store) {
        PromoCodeEntity entity = new PromoCodeEntity();
        entity.setId(3L);
        entity.setCode("WELCOME_20");
        entity.setName("Welcome offer");
        entity.setDiscountPercent(20D);
        entity.setStatus(PromoStatus.DRAFT);
        entity.setPromoType(PromoType.INTRO_OFFER);
        entity.setTargetStore(store);
        entity.setCurrency("EUR");
        entity.setPerUserLimit(1);
        entity.setUsedCount(0);
        return entity;
    }
}

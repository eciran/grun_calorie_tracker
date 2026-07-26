package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductContributionRequestDto;
import com.grun.calorietracker.dto.FoodProductContributionReviewRequestDto;
import com.grun.calorietracker.entity.FoodProductContributionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodProductContributionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FoodProductContributionServiceImpl;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.InspectedEvidence;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.StoredEvidence;
import com.grun.calorietracker.service.support.FoodContributionEvidenceFileInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductContributionServiceImplTest {

    private static final String CHECKSUM = "a".repeat(64);
    private FoodProductContributionRepository repository;
    private UserRepository userRepository;
    private FoodContributionEvidenceFileInspector inspector;
    private FoodContributionEvidenceStorage storage;
    private FoodProductContributionServiceImpl service;
    private UserEntity user;
    private MockMultipartFile evidenceFile;

    @BeforeEach
    void setUp() {
        repository = mock(FoodProductContributionRepository.class);
        userRepository = mock(UserRepository.class);
        inspector = mock(FoodContributionEvidenceFileInspector.class);
        storage = mock(FoodContributionEvidenceStorage.class);
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.setPrivateBaseUrl("https://api.grun.app");
        service = new FoodProductContributionServiceImpl(repository, userRepository, inspector, storage, properties);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@test.com");
        byte[] evidenceBytes = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        evidenceFile = new MockMultipartFile("file", "label.jpg", "image/jpeg", evidenceBytes);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(inspector.inspect(evidenceFile)).thenReturn(new InspectedEvidence(evidenceBytes, "image/jpeg", ".jpg", CHECKSUM));
        when(storage.store(any(), any(), any())).thenReturn(new StoredEvidence("product-contributions/u7/evidence.jpg", CHECKSUM, "image/jpeg", evidenceFile.getSize()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            FoodProductContributionEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submit_normalizesIdentityStoresPrivateEvidenceAndQueuesWithoutCatalogWrite() {
        FoodProductContributionRequestDto request = validRequest();
        request.setProductName("TEST BISCUIT");
        request.setBrand("TEST BRAND");

        var result = service.submit("user@test.com", request, evidenceFile);

        assertEquals(FoodProductContributionStatus.PENDING_REVIEW, result.getStatus());
        assertEquals("Test Biscuit", result.getProductName());
        assertEquals("Test Brand", result.getBrand());
        assertEquals("https://api.grun.app/api/v1/admin/products/contributions/11/evidence", result.getEvidenceUrl());
        assertEquals("image/jpeg", result.getEvidenceContentType());
        ArgumentCaptor<FoodProductContributionEntity> captor = ArgumentCaptor.forClass(FoodProductContributionEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("8691234567890", captor.getValue().getNormalizedBarcode());
        assertEquals("product-contributions/u7/evidence.jpg", captor.getValue().getEvidenceStorageKey());
        verify(storage).store(any(), any(), any());
    }

    @Test
    void submit_rejectsInvalidGtinBeforeInspectingOrStoringEvidence() {
        FoodProductContributionRequestDto request = validRequest();
        request.setBarcode("8691234567891");

        assertThrows(IllegalArgumentException.class, () -> service.submit("user@test.com", request, evidenceFile));
        verify(inspector, never()).inspect(any());
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void submit_rejectsDuplicateServerCalculatedEvidence() {
        FoodProductContributionRequestDto request = validRequest();
        when(repository.existsBySubmittedByIdAndNormalizedBarcodeAndEvidenceChecksum(7L, "8691234567890", CHECKSUM)).thenReturn(true);

        assertThrows(RequestConflictException.class, () -> service.submit("user@test.com", request, evidenceFile));
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void submit_deletesPrivateObjectWhenDatabaseWriteFails() {
        FoodProductContributionRequestDto request = validRequest();
        doThrow(new IllegalStateException("database unavailable")).when(repository).saveAndFlush(any());

        assertThrows(IllegalStateException.class, () -> service.submit("user@test.com", request, evidenceFile));

        verify(storage).delete("product-contributions/u7/evidence.jpg");
    }

    @Test
    void loadEvidenceForUser_enforcesOwnership() {
        FoodProductContributionEntity entity = pendingEntity();
        when(repository.findById(11L)).thenReturn(Optional.of(entity));
        when(storage.load(entity.getEvidenceStorageKey())).thenReturn(new EvidenceContent(new byte[]{1, 2, 3}, "image/jpeg"));

        EvidenceContent evidence = service.loadEvidenceForUser(11L, "user@test.com");

        assertArrayEquals(new byte[]{1, 2, 3}, evidence.bytes());
        assertEquals("image/jpeg", evidence.contentType());
    }

    @Test
    void loadEvidenceForUser_hidesAnotherUsersContribution() {
        UserEntity other = new UserEntity();
        other.setId(99L);
        FoodProductContributionEntity entity = pendingEntity();
        entity.setSubmittedBy(other);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));

        assertThrows(ResourceNotFoundException.class, () -> service.loadEvidenceForUser(11L, "user@test.com"));
        verify(storage, never()).load(any());
    }

    @Test
    void review_approvalDoesNotCreateCatalogProductAndStoresReviewer() {
        FoodProductContributionEntity entity = pendingEntity();
        when(repository.findById(11L)).thenReturn(Optional.of(entity));
        FoodProductContributionReviewRequestDto request = new FoodProductContributionReviewRequestDto();
        request.setDecision(FoodProductContributionStatus.APPROVED);
        request.setReviewNote("Readable label and matching GTIN.");

        var result = service.review(11L, "admin@test.com", request);

        assertEquals(FoodProductContributionStatus.APPROVED, result.getStatus());
        assertEquals("admin@test.com", entity.getReviewerIdentity());
        verify(repository).save(entity);
    }

    @Test
    void exportApprovedTrEvidenceLedger_matchesS9Contract() {
        FoodProductContributionEntity entity = pendingEntity();
        entity.setStatus(FoodProductContributionStatus.APPROVED);
        entity.setReviewerIdentity("admin@test.com");
        when(repository.findByStatusAndMarketRegionOrderByNormalizedBarcodeAsc(FoodProductContributionStatus.APPROVED, MarketRegion.TR)).thenReturn(List.of(entity));

        String tsv = new String(service.exportApprovedTrEvidenceLedger(), StandardCharsets.UTF_8);

        assertTrue(tsv.startsWith("barcode\tevidenceType\tevidenceSourceId"));
        assertTrue(tsv.contains("8691234567890\tUSER_SUBMITTED_LABEL\tUSER_CONTRIBUTION:11"));
        assertTrue(tsv.contains("\tTR\ttrue\ttrue\tAPPROVED\tadmin@test.com"));
    }

    private FoodProductContributionRequestDto validRequest() {
        FoodProductContributionRequestDto request = new FoodProductContributionRequestDto();
        request.setBarcode("8691234567890");
        request.setProductName("Test Biscuit");
        request.setBrand("Test Brand");
        request.setMarketRegion(MarketRegion.TR);
        request.setCalories(420.0);
        request.setProtein(7.0);
        request.setFat(14.0);
        request.setCarbs(65.0);
        request.setCommercialUseAllowed(true);
        request.setPersistentStorageAllowed(true);
        return request;
    }

    private FoodProductContributionEntity pendingEntity() {
        FoodProductContributionEntity entity = new FoodProductContributionEntity();
        entity.setId(11L);
        entity.setSubmittedBy(user);
        entity.setBarcode("8691234567890");
        entity.setNormalizedBarcode("8691234567890");
        entity.setProductName("Test Biscuit");
        entity.setBrand("Test Brand");
        entity.setMarketRegion(MarketRegion.TR);
        entity.setCalories(420.0);
        entity.setProtein(7.0);
        entity.setFat(14.0);
        entity.setCarbs(65.0);
        entity.setEvidenceUrl("https://api.grun.app/api/v1/admin/products/contributions/11/evidence");
        entity.setEvidenceStorageKey("product-contributions/u7/evidence.jpg");
        entity.setEvidenceContentType("image/jpeg");
        entity.setEvidenceSizeBytes(4L);
        entity.setEvidenceChecksum(CHECKSUM);
        entity.setEvidenceRetrievedAt(OffsetDateTime.now().minusDays(1));
        entity.setCommercialUseAllowed(true);
        entity.setPersistentStorageAllowed(true);
        entity.setStatus(FoodProductContributionStatus.PENDING_REVIEW);
        return entity;
    }
}

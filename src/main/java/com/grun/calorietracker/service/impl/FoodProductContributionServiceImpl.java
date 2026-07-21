package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductContributionDto;
import com.grun.calorietracker.dto.FoodProductContributionPageDto;
import com.grun.calorietracker.dto.FoodProductContributionRequestDto;
import com.grun.calorietracker.dto.FoodProductContributionReviewRequestDto;
import com.grun.calorietracker.entity.FoodProductContributionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodProductContributionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodProductContributionService;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.InspectedEvidence;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.StoredEvidence;
import com.grun.calorietracker.service.support.FoodContributionEvidenceFileInspector;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.GtinValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodProductContributionServiceImpl implements FoodProductContributionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVIDENCE_AGE_DAYS = 365;
    private static final String LEDGER_HEADER = "barcode\tevidenceType\tevidenceSourceId\tevidenceUrl\t" +
            "evidenceRetrievedAt\tevidenceChecksum\tmarketRegion\tcommercialUseAllowed\t" +
            "persistentStorageAllowed\treviewDecision\treviewerId\n";

    private final FoodProductContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final FoodContributionEvidenceFileInspector evidenceFileInspector;
    private final FoodContributionEvidenceStorage evidenceStorage;
    private final FoodContributionStorageProperties storageProperties;

    @Override
    @Transactional
    public FoodProductContributionDto submit(
            String userEmail,
            FoodProductContributionRequestDto request,
            MultipartFile evidenceFile
    ) {
        UserEntity user = getUser(userEmail);
        validateSubmission(request);
        String barcode = FoodProductNormalizationRules.normalizeBarcode(request.getBarcode());
        InspectedEvidence inspected = evidenceFileInspector.inspect(evidenceFile);
        if (contributionRepository.existsBySubmittedByIdAndNormalizedBarcodeAndEvidenceChecksum(
                user.getId(), barcode, inspected.checksum())) {
            throw new RequestConflictException("This label evidence was already submitted for the barcode.");
        }

        StoredEvidence stored = evidenceStorage.store(user.getId(), barcode, inspected);
        try {
            FoodProductContributionEntity entity = new FoodProductContributionEntity();
            entity.setSubmittedBy(user);
            entity.setBarcode(barcode);
            entity.setNormalizedBarcode(barcode);
            entity.setProductName(FoodProductNormalizationRules.normalizeProductDisplayName(request.getProductName()));
            entity.setBrand(FoodProductNormalizationRules.normalizeBrandDisplayName(request.getBrand()));
            entity.setMarketRegion(request.getMarketRegion());
            entity.setCalories(request.getCalories());
            entity.setProtein(request.getProtein());
            entity.setFat(request.getFat());
            entity.setCarbs(request.getCarbs());
            entity.setFiber(request.getFiber());
            entity.setSugar(request.getSugar());
            entity.setSodium(request.getSodium());
            entity.setServingSizeGrams(request.getServingSizeGrams());
            entity.setServingUnit(trimToNull(request.getServingUnit()));
            entity.setEvidenceStorageKey(stored.storageKey());
            entity.setEvidenceChecksum(stored.checksum());
            entity.setEvidenceContentType(stored.contentType());
            entity.setEvidenceSizeBytes(stored.sizeBytes());
            entity.setEvidenceRetrievedAt(OffsetDateTime.now());
            entity.setCommercialUseAllowed(request.isCommercialUseAllowed());
            entity.setPersistentStorageAllowed(request.isPersistentStorageAllowed());
            entity.setStatus(FoodProductContributionStatus.PENDING_REVIEW);
            entity = contributionRepository.saveAndFlush(entity);
            entity.setEvidenceUrl(privateEvidenceUrl(entity.getId()));
            return toDto(contributionRepository.save(entity));
        } catch (RuntimeException exception) {
            try {
                evidenceStorage.delete(stored.storageKey());
            } catch (RuntimeException ignored) {
                // Preserve the database failure; object cleanup can be retried operationally.
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductContributionPageDto listMine(String userEmail, int page, int size) {
        UserEntity user = getUser(userEmail);
        return toPage(contributionRepository.findBySubmittedByIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(safePage(page), safeSize(size))));
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductContributionPageDto listForReview(
            FoodProductContributionStatus status,
            MarketRegion marketRegion,
            int page,
            int size
    ) {
        return toPage(contributionRepository.findForReview(
                status, marketRegion, PageRequest.of(safePage(page), safeSize(size))));
    }

    @Override
    @Transactional
    public FoodProductContributionDto review(
            Long contributionId,
            String adminEmail,
            FoodProductContributionReviewRequestDto request
    ) {
        if (request.getDecision() != FoodProductContributionStatus.APPROVED
                && request.getDecision() != FoodProductContributionStatus.REJECTED) {
            throw new IllegalArgumentException("Review decision must be APPROVED or REJECTED.");
        }
        FoodProductContributionEntity entity = findContribution(contributionId);
        if (entity.getStatus() != FoodProductContributionStatus.PENDING_REVIEW) {
            throw new RequestConflictException("Only pending contributions can be reviewed.");
        }
        if (request.getDecision() == FoodProductContributionStatus.REJECTED
                && trimToNull(request.getReviewNote()) == null) {
            throw new IllegalArgumentException("A rejection note is required.");
        }
        if (request.getDecision() == FoodProductContributionStatus.APPROVED) {
            validateApproval(entity);
            if (contributionRepository.existsByNormalizedBarcodeAndStatusAndIdNot(
                    entity.getNormalizedBarcode(), FoodProductContributionStatus.APPROVED, entity.getId())) {
                throw new RequestConflictException("Another approved contribution already exists for this barcode.");
            }
        }
        entity.setStatus(request.getDecision());
        entity.setReviewerIdentity(adminEmail);
        entity.setReviewNote(trimToNull(request.getReviewNote()));
        entity.setReviewedAt(LocalDateTime.now());
        return toDto(contributionRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceContent loadEvidenceForUser(Long contributionId, String userEmail) {
        UserEntity user = getUser(userEmail);
        FoodProductContributionEntity entity = findContribution(contributionId);
        if (entity.getSubmittedBy() == null || !user.getId().equals(entity.getSubmittedBy().getId())) {
            throw new ResourceNotFoundException("Food product contribution was not found.");
        }
        return loadEvidence(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceContent loadEvidenceForAdmin(Long contributionId) {
        return loadEvidence(findContribution(contributionId));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportApprovedTrEvidenceLedger() {
        List<FoodProductContributionEntity> approved = contributionRepository
                .findByStatusAndMarketRegionOrderByNormalizedBarcodeAsc(
                        FoodProductContributionStatus.APPROVED, MarketRegion.TR);
        StringBuilder tsv = new StringBuilder(LEDGER_HEADER);
        for (FoodProductContributionEntity entity : approved) {
            validateApproval(entity);
            tsv.append(tsv(entity.getNormalizedBarcode())).append('\t')
                    .append("USER_SUBMITTED_LABEL\t")
                    .append("USER_CONTRIBUTION:").append(entity.getId()).append('\t')
                    .append(tsv(entity.getEvidenceUrl())).append('\t')
                    .append(entity.getEvidenceRetrievedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\t')
                    .append(entity.getEvidenceChecksum()).append("\tTR\ttrue\ttrue\tAPPROVED\t")
                    .append(tsv(entity.getReviewerIdentity())).append('\n');
        }
        return tsv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private EvidenceContent loadEvidence(FoodProductContributionEntity entity) {
        if (entity.getEvidenceStorageKey() == null || entity.getEvidenceStorageKey().isBlank()) {
            throw new ResourceNotFoundException("Product label evidence was not found.");
        }
        EvidenceContent loaded = evidenceStorage.load(entity.getEvidenceStorageKey());
        String contentType = loaded.contentType() == null || loaded.contentType().isBlank()
                ? entity.getEvidenceContentType()
                : loaded.contentType();
        return new EvidenceContent(loaded.bytes(), contentType);
    }

    private void validateSubmission(FoodProductContributionRequestDto request) {
        String barcode = FoodProductNormalizationRules.normalizeBarcode(request.getBarcode());
        if (!GtinValidator.isValid(barcode)) {
            throw new IllegalArgumentException("Barcode has an invalid GTIN check digit.");
        }
        if (request.getMarketRegion() != MarketRegion.TR) {
            throw new IllegalArgumentException("The current contribution pilot accepts TR products only.");
        }
        if (request.getProtein() + request.getFat() + request.getCarbs() > 110.0) {
            throw new IllegalArgumentException("Protein, fat, and carbohydrate total cannot exceed 110 grams per 100 grams.");
        }
    }

    private void validateApproval(FoodProductContributionEntity entity) {
        if (entity.getMarketRegion() != MarketRegion.TR) {
            throw new IllegalArgumentException("Only TR evidence can enter the S9 evidence ledger.");
        }
        if (!Boolean.TRUE.equals(entity.getCommercialUseAllowed())
                || !Boolean.TRUE.equals(entity.getPersistentStorageAllowed())) {
            throw new IllegalArgumentException("Commercial use and persistent storage consent are required.");
        }
        if (entity.getEvidenceStorageKey() == null || entity.getEvidenceStorageKey().isBlank()) {
            throw new IllegalArgumentException("Private evidence object is missing.");
        }
        validateHttpsUrl(entity.getEvidenceUrl());
        if (entity.getEvidenceRetrievedAt().isBefore(OffsetDateTime.now().minusDays(MAX_EVIDENCE_AGE_DAYS))) {
            throw new IllegalArgumentException("Evidence is older than the 365-day S9 acceptance window.");
        }
        if (entity.getEvidenceChecksum() == null || !entity.getEvidenceChecksum().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Evidence checksum is not a valid SHA-256 value.");
        }
        if (entity.getReviewerIdentity() == null && entity.getStatus() == FoodProductContributionStatus.APPROVED) {
            throw new IllegalArgumentException("Approved evidence is missing reviewer identity.");
        }
    }

    private String privateEvidenceUrl(Long contributionId) {
        String baseUrl = storageProperties.getPrivateBaseUrl() == null
                ? ""
                : storageProperties.getPrivateBaseUrl().replaceAll("/+$", "");
        String value = baseUrl + "/api/v1/admin/products/contributions/" + contributionId + "/evidence";
        validateHttpsUrl(value);
        return value;
    }

    private void validateHttpsUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Evidence URL must be an HTTPS URL without embedded credentials.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Evidence URL must be a valid HTTPS URL without embedded credentials.");
        }
    }

    private FoodProductContributionEntity findContribution(Long contributionId) {
        return contributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product contribution was not found."));
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private FoodProductContributionPageDto toPage(Page<FoodProductContributionEntity> page) {
        FoodProductContributionPageDto dto = new FoodProductContributionPageDto();
        dto.setContent(page.getContent().stream().map(this::toDto).toList());
        dto.setPage(page.getNumber());
        dto.setSize(page.getSize());
        dto.setTotalElements(page.getTotalElements());
        dto.setTotalPages(page.getTotalPages());
        dto.setFirst(page.isFirst());
        dto.setLast(page.isLast());
        return dto;
    }

    private FoodProductContributionDto toDto(FoodProductContributionEntity entity) {
        FoodProductContributionDto dto = new FoodProductContributionDto();
        dto.setId(entity.getId());
        dto.setSubmittedByUserId(entity.getSubmittedBy().getId());
        dto.setBarcode(entity.getNormalizedBarcode());
        dto.setProductName(entity.getProductName());
        dto.setBrand(entity.getBrand());
        dto.setMarketRegion(entity.getMarketRegion());
        dto.setCalories(entity.getCalories());
        dto.setProtein(entity.getProtein());
        dto.setFat(entity.getFat());
        dto.setCarbs(entity.getCarbs());
        dto.setFiber(entity.getFiber());
        dto.setSugar(entity.getSugar());
        dto.setSodium(entity.getSodium());
        dto.setServingSizeGrams(entity.getServingSizeGrams());
        dto.setServingUnit(entity.getServingUnit());
        dto.setEvidenceUrl(entity.getEvidenceUrl());
        dto.setEvidenceContentType(entity.getEvidenceContentType());
        dto.setEvidenceSizeBytes(entity.getEvidenceSizeBytes());
        dto.setEvidenceChecksum(entity.getEvidenceChecksum());
        dto.setEvidenceRetrievedAt(entity.getEvidenceRetrievedAt());
        dto.setCommercialUseAllowed(entity.getCommercialUseAllowed());
        dto.setPersistentStorageAllowed(entity.getPersistentStorageAllowed());
        dto.setStatus(entity.getStatus());
        dto.setReviewNote(entity.getReviewNote());
        dto.setReviewedAt(entity.getReviewedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private int safePage(int page) {
        return Math.max(0, page);
    }

    private int safeSize(int size) {
        return size < 1 ? 25 : Math.min(size, MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String tsv(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
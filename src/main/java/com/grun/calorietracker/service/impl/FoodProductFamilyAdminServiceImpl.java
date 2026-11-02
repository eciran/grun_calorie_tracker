package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyMemberRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyPageDto;
import com.grun.calorietracker.dto.FoodProductFamilyMembershipDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import com.grun.calorietracker.entity.FoodProductFamilyMemberEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductFamilyMemberRepository;
import com.grun.calorietracker.repository.FoodProductFamilyRepository;
import com.grun.calorietracker.service.FoodProductFamilyAdminService;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import com.grun.calorietracker.service.support.FoodBrandSearchRules;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class FoodProductFamilyAdminServiceImpl implements FoodProductFamilyAdminService {
    private static final Set<FoodPortionUnit> PACKAGE_UNITS = Set.of(
            FoodPortionUnit.GRAM, FoodPortionUnit.MILLILITER
    );

    private final FoodItemRepository foodItemRepository;
    private final FoodProductFamilyRepository familyRepository;
    private final FoodProductFamilyMemberRepository memberRepository;
    private final ExternalFoodImportFence externalFoodImportFence;

    @Override
    @Transactional(readOnly = true)
    public FoodProductFamilyPreviewDto preview(FoodProductFamilyRequestDto request) {
        return validate(request).preview();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"},
            allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodProductFamilyDto apply(FoodProductFamilyRequestDto request, String actor) {
        requireActor(actor);
        externalFoodImportFence.acquire();
        Validation validation = validate(request);
        return applyValidated(request, validation, List.of(), actor);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"},
            allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodProductFamilyDto apply(FoodProductFamilyRequestDto request, String previewFingerprint,
                                      List<String> acknowledgedWarnings, String actor) {
        requireActor(actor);
        externalFoodImportFence.acquire();
        Validation validation = validate(request);
        if (!Objects.equals(validation.fingerprint(), previewFingerprint)) {
            throw new IllegalArgumentException("Preview is stale; one or more products changed. Run preview again.");
        }
        Set<String> acknowledgements = new HashSet<>(acknowledgedWarnings == null ? List.of() : acknowledgedWarnings);
        Set<String> expectedWarnings = new HashSet<>(validation.warnings());
        if (!acknowledgements.equals(expectedWarnings)) {
            throw new IllegalArgumentException("Acknowledged warnings must exactly match the latest preview warnings");
        }
        return applyValidated(request, validation, validation.warnings(), actor);
    }

    private FoodProductFamilyDto applyValidated(FoodProductFamilyRequestDto request, Validation validation,
                                                List<String> acknowledgedWarnings, String actor) {
        requireActor(actor);
        if (!validation.errors().isEmpty()) {
            throw new IllegalArgumentException("Product family validation failed: " + String.join("; ", validation.errors()));
        }

        FoodItemEntity representative = validation.products().get(request.representativeProductId());
        normalizeVolumeFamilyMeasurements(request.members(), validation.products());
        String familyKey = request.familyKey().trim().toLowerCase(Locale.ROOT);
        FoodProductFamilyEntity family = new FoodProductFamilyEntity();
        family.setFamilyKey(familyKey);
        family.setBrandKey(FoodBrandSearchRules.key(representative.getBrand()));
        family.setVariantKey(variantKey(familyKey));
        family.setDisplayName(request.displayName().trim());
        family.setRepresentativeFoodItem(representative);
        family.setEvidenceReason(request.evidenceReason().trim());
        family.setAppliedBy(actor.trim());
        family.setPreviewFingerprint(validation.fingerprint());
        family.setAcknowledgedWarnings(String.join("\n", acknowledgedWarnings));
        family.setActive(true);
        family = familyRepository.saveAndFlush(family);

        int sortOrder = 0;
        List<FoodProductFamilyMemberEntity> members = new ArrayList<>();
        for (FoodProductFamilyMemberRequestDto requested : request.members()) {
            FoodProductFamilyMemberEntity member = new FoodProductFamilyMemberEntity();
            member.setFamily(family);
            member.setFoodItem(validation.products().get(requested.productId()));
            member.setPackageQuantity(BigDecimal.valueOf(requested.packageQuantity()));
            member.setPackageUnit(requested.packageUnit());
            member.setPackageLabel(readyAmountLabel(requested.packageQuantity(), requested.packageUnit()));
            member.setDefaultVariant(requested.defaultVariant());
            member.setSortOrder(sortOrder++);
            member.setActive(true);
            members.add(member);
        }
        memberRepository.saveAllAndFlush(members);
        return toDto(family, members);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductFamilyPageDto list(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        var page = familyRepository.search(normalizedQuery, active, pageable);
        List<FoodProductFamilyDto> content = page.getContent().stream()
                .map(family -> toDto(family, memberRepository.findAllByFamilyIdOrderBySortOrderAscIdAsc(family.getId())))
                .toList();
        return new FoodProductFamilyPageDto(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductFamilyDto get(Long familyId) {
        FoodProductFamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ProductNotFoundException("Product family not found"));
        return toDto(family, memberRepository.findAllByFamilyIdOrderBySortOrderAscIdAsc(familyId));
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductFamilyPreviewDto previewAddition(Long familyId, FoodProductFamilyMemberRequestDto member,
                                                       String reason) {
        Addition addition = addition(familyId, member, reason);
        return addition.validation().preview();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"},
            allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodProductFamilyDto addMember(Long familyId, FoodProductFamilyMemberRequestDto member, String reason,
                                          String previewFingerprint, List<String> acknowledgedWarnings, String actor) {
        requireActor(actor);
        externalFoodImportFence.acquire();
        Addition addition = addition(familyId, member, reason);
        Validation validation = addition.validation();
        if (!Objects.equals(validation.fingerprint(), previewFingerprint)) {
            throw new IllegalArgumentException("Preview is stale; the family or product changed. Run preview again.");
        }
        Set<String> acknowledgements = new HashSet<>(acknowledgedWarnings == null ? List.of() : acknowledgedWarnings);
        if (!acknowledgements.equals(new HashSet<>(validation.warnings()))) {
            throw new IllegalArgumentException("Acknowledged warnings must exactly match the latest preview warnings");
        }
        if (!validation.errors().isEmpty()) {
            throw new IllegalArgumentException("Product family validation failed: " + String.join("; ", validation.errors()));
        }
        FoodProductFamilyMemberEntity entity = new FoodProductFamilyMemberEntity();
        normalizeVolumeFamilyMeasurements(validation.request().members(), validation.products());
        entity.setFamily(addition.family());
        entity.setFoodItem(validation.products().get(member.productId()));
        entity.setPackageQuantity(BigDecimal.valueOf(member.packageQuantity()));
        entity.setPackageUnit(member.packageUnit());
        entity.setPackageLabel(readyAmountLabel(member.packageQuantity(), member.packageUnit()));
        entity.setDefaultVariant(false);
        entity.setSortOrder(addition.existingMembers().size());
        entity.setActive(true);
        memberRepository.saveAndFlush(entity);
        FoodProductFamilyEntity family = addition.family();
        family.setPreviewFingerprint(validation.fingerprint());
        family.setAcknowledgedWarnings(String.join("\n", validation.warnings()));
        familyRepository.saveAndFlush(family);
        List<FoodProductFamilyMemberEntity> members = new ArrayList<>(addition.existingMembers());
        members.add(entity);
        return toDto(family, members);
    }

    private Addition addition(Long familyId, FoodProductFamilyMemberRequestDto member, String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("An addition reason of at least 10 characters is required");
        }
        FoodProductFamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ProductNotFoundException("Product family not found"));
        if (!family.isActive()) throw new IllegalArgumentException("Products can only be added to an active family");
        List<FoodProductFamilyMemberEntity> existing = memberRepository
                .findAllByFamilyIdOrderBySortOrderAscIdAsc(familyId).stream().filter(FoodProductFamilyMemberEntity::isActive).toList();
        if (existing.size() >= 20) throw new IllegalArgumentException("A package family may contain at most 20 products");
        if (existing.stream().anyMatch(item -> item.getFoodItem().getId().equals(member.productId()))) {
            throw new IllegalArgumentException("Product already belongs to this family: " + member.productId());
        }
        List<FoodProductFamilyMemberRequestDto> members = new ArrayList<>();
        existing.forEach(item -> members.add(new FoodProductFamilyMemberRequestDto(item.getFoodItem().getId(),
                item.getPackageQuantity().doubleValue(), item.getPackageUnit(), item.getPackageLabel(), item.isDefaultVariant())));
        members.add(new FoodProductFamilyMemberRequestDto(member.productId(), member.packageQuantity(),
                member.packageUnit(), member.packageLabel(), false));
        FoodProductFamilyRequestDto request = new FoodProductFamilyRequestDto(family.getFamilyKey(),
                family.getDisplayName(), family.getRepresentativeFoodItem().getId(), reason.trim(), members);
        return new Addition(family, existing, validate(request, familyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductFamilyMembershipDto> memberships(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        return memberRepository.findActiveMembershipsForProducts(new HashSet<>(productIds)).stream()
                .map(member -> new FoodProductFamilyMembershipDto(member.getFoodItem().getId(),
                        member.getFamily().getId(), member.getFamily().getFamilyKey(), member.getFamily().getDisplayName()))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"},
            allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodProductFamilyDto deactivate(Long familyId, String reason, String actor) {
        requireActor(actor);
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("A revert reason of at least 10 characters is required");
        }
        externalFoodImportFence.acquire();
        FoodProductFamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ProductNotFoundException("Product family not found"));
        if (!family.isActive()) {
            throw new IllegalArgumentException("Product family is already inactive");
        }
        List<FoodProductFamilyMemberEntity> members =
                memberRepository.findAllByFamilyIdOrderBySortOrderAscIdAsc(familyId);
        members.forEach(member -> member.setActive(false));
        family.setActive(false);
        family.setRevertedBy(actor.trim());
        family.setRevertedAt(LocalDateTime.now());
        family.setRevertReason(reason.trim());
        memberRepository.saveAll(members);
        familyRepository.saveAndFlush(family);
        return toDto(family, members);
    }

    private Validation validate(FoodProductFamilyRequestDto request) {
        return validate(request, null);
    }

    private Validation validate(FoodProductFamilyRequestDto request, Long currentFamilyId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Long> ids = request.members().stream().map(FoodProductFamilyMemberRequestDto::productId).toList();
        Set<Long> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) errors.add("Member product ids must be unique");
        if (!uniqueIds.contains(request.representativeProductId())) {
            errors.add("Representative product must be included in members");
        }
        long defaults = request.members().stream().filter(FoodProductFamilyMemberRequestDto::defaultVariant).count();
        if (defaults != 1) errors.add("Exactly one default package variant is required");
        request.members().stream()
                .filter(FoodProductFamilyMemberRequestDto::defaultVariant)
                .findFirst()
                .filter(member -> !member.productId().equals(request.representativeProductId()))
                .ifPresent(member -> errors.add("Default package variant must be the representative product"));
        request.members().forEach(member -> {
            if (!PACKAGE_UNITS.contains(member.packageUnit())) {
                errors.add("Package unit must be GRAM or MILLILITER for product " + member.productId());
            }
            String canonicalLabel = readyAmountLabel(member.packageQuantity(), member.packageUnit());
            if (!canonicalLabel.equalsIgnoreCase(member.packageLabel().trim())) {
                warnings.add("Ready amount label for product " + member.productId()
                        + " will be normalized to " + canonicalLabel);
            }
        });
        if (request.members().stream().map(FoodProductFamilyMemberRequestDto::packageUnit).distinct().count() > 1) {
            errors.add("All members of a product family must use the same package unit");
        }

        Map<Long, FoodItemEntity> products = new LinkedHashMap<>();
        foodItemRepository.findAllById(uniqueIds).forEach(product -> products.put(product.getId(), product));
        uniqueIds.stream().filter(id -> !products.containsKey(id))
                .forEach(id -> errors.add("Food product not found: " + id));
        familyRepository.findFirstByFamilyKeyIgnoreCaseAndActiveTrue(request.familyKey().trim())
                .filter(existing -> !existing.getId().equals(currentFamilyId))
                .ifPresent(existing -> errors.add("Family key already exists: " + existing.getId()));
        memberRepository.findActiveMembershipsForProducts(uniqueIds).stream()
                .filter(member -> !member.getFamily().getId().equals(currentFamilyId))
                .forEach(member -> errors.add("Product " + member.getFoodItem().getId()
                        + " already belongs to active family " + member.getFamily().getId()));

        Set<String> brandKeys = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        for (FoodItemEntity product : products.values()) {
            String brandKey = FoodBrandSearchRules.key(product.getBrand());
            if (brandKey == null) errors.add("Product has no normalized brand: " + product.getId());
            else brandKeys.add(brandKey);
            String barcode = product.getNormalizedBarcode();
            if (barcode == null || barcode.isBlank()) errors.add("Product has no normalized barcode: " + product.getId());
            else if (!barcodes.add(barcode)) errors.add("Member barcodes must be distinct: " + barcode);
            if (product.getCatalogType() != FoodCatalogType.BRANDED_PRODUCT) {
                errors.add("Only branded products can join a package family: " + product.getId());
            }
            if (product.getPublicationStatus() != CatalogPublicationStatus.PUBLISHED) {
                errors.add("Product is not published: " + product.getId());
            }
            if (product.getVerificationStatus() == VerificationStatus.REJECTED) {
                errors.add("Rejected product cannot join a package family: " + product.getId());
            }
        }
        if (brandKeys.size() > 1) errors.add("All family members must resolve to the same brand");
        if (hasNutritionVariance(products.values().stream().toList())) {
            warnings.add("Nutrition profiles differ materially; source evidence must confirm package-only variation");
        }
        if (products.values().stream().map(FoodItemEntity::getNutritionReferenceUnit).distinct().count() > 1) {
            warnings.add("Nutrition reference units differ; source evidence must confirm comparable package variants");
        }
        if (products.values().stream().map(FoodItemEntity::getPreparationState).distinct().count() > 1) {
            warnings.add("Preparation states differ; source evidence must confirm package-only variation");
        }
        if (request.displayName().matches("(?i).*\\b\\d+(?:[.,]\\d+)?\\s*(?:g|gr|kg|ml|l)\\b.*")) {
            errors.add("Family display name must not contain a package size");
        }
        return new Validation(request, products, errors, warnings,
                fingerprint(request, products, memberRepository.findActiveMembershipsForProducts(uniqueIds)));
    }

    private String fingerprint(FoodProductFamilyRequestDto request, Map<Long, FoodItemEntity> products,
                               List<FoodProductFamilyMemberEntity> memberships) {
        StringBuilder value = new StringBuilder(request.familyKey()).append('|').append(request.displayName())
                .append('|').append(request.representativeProductId());
        request.members().forEach(member -> {
            FoodItemEntity product = products.get(member.productId());
            value.append('|').append(member.productId()).append(':').append(member.packageQuantity())
                    .append(':').append(member.packageUnit()).append(':').append(member.packageLabel())
                    .append(':').append(member.defaultVariant());
            if (product != null) value.append(':').append(product.getNormalizedBarcode())
                    .append(':').append(FoodBrandSearchRules.key(product.getBrand()))
                    .append(':').append(product.getCalories()).append(':').append(product.getProtein())
                    .append(':').append(product.getCarbs()).append(':').append(product.getFat())
                    .append(':').append(product.getNutritionReferenceUnit())
                    .append(':').append(product.getPreparationState()).append(':').append(product.getCatalogType())
                    .append(':').append(product.getServingSizeGrams()).append(':').append(product.getServingUnit())
                    .append(':').append(product.getPublicationStatus()).append(':').append(product.getVerificationStatus());
        });
        memberships.stream().map(member -> member.getFamily().getId() + ":" + member.getFoodItem().getId())
                .sorted().forEach(item -> value.append("|membership:").append(item));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasNutritionVariance(List<FoodItemEntity> products) {
        if (products.size() < 2) return false;
        FoodItemEntity first = products.get(0);
        return products.stream().skip(1).anyMatch(other ->
                materiallyDifferent(first.getCalories(), other.getCalories(), 5.0, 0.05)
                        || materiallyDifferent(first.getProtein(), other.getProtein(), 1.0, 0.10)
                        || materiallyDifferent(first.getCarbs(), other.getCarbs(), 1.0, 0.10)
                        || materiallyDifferent(first.getFat(), other.getFat(), 1.0, 0.10));
    }

    private void normalizeVolumeFamilyMeasurements(
            List<FoodProductFamilyMemberRequestDto> members,
            Map<Long, FoodItemEntity> products
    ) {
        boolean allVolume = !members.isEmpty() && members.stream()
                .allMatch(member -> member.packageUnit() == FoodPortionUnit.MILLILITER);
        if (!allVolume) return;
        List<FoodItemEntity> changed = new ArrayList<>();
        for (FoodProductFamilyMemberRequestDto member : members) {
            FoodItemEntity product = products.get(member.productId());
            if (product == null) continue;
            boolean dirty = product.getNutritionReferenceUnit() != FoodNutritionReferenceUnit.PER_100ML
                    || !"MILLILITER".equalsIgnoreCase(Objects.toString(product.getServingUnit(), ""))
                    || product.getServingSizeGrams() != null;
            if (!dirty) continue;
            product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
            product.setServingUnit("MILLILITER");
            product.setServingSizeGrams(null);
            changed.add(product);
        }
        if (!changed.isEmpty()) foodItemRepository.saveAll(changed);
    }

    private String readyAmountLabel(Double quantity, FoodPortionUnit unit) {
        BigDecimal value = BigDecimal.valueOf(quantity).stripTrailingZeros();
        if (unit == FoodPortionUnit.MILLILITER && value.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return value.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString() + " L";
        }
        if (unit == FoodPortionUnit.GRAM && value.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return value.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString() + " kg";
        }
        return value.toPlainString() + (unit == FoodPortionUnit.MILLILITER ? " ml" : " g");
    }

    private boolean materiallyDifferent(Double left, Double right, double absoluteTolerance, double relativeTolerance) {
        if (left == null || right == null) return !Objects.equals(left, right);
        double scale = Math.max(Math.max(Math.abs(left), Math.abs(right)), 1.0);
        return Math.abs(left - right) > Math.max(absoluteTolerance, scale * relativeTolerance);
    }

    private FoodProductFamilyDto toDto(
            FoodProductFamilyEntity family,
            List<FoodProductFamilyMemberEntity> members
    ) {
        return new FoodProductFamilyDto(
                family.getId(), family.getFamilyKey(), family.getDisplayName(),
                family.getRepresentativeFoodItem().getId(), family.isActive(),
                family.getEvidenceReason(), family.getAppliedBy(), family.getAppliedAt(),
                family.getPreviewFingerprint(), splitWarnings(family.getAcknowledgedWarnings()),
                family.getRevertedBy(), family.getRevertedAt(), family.getRevertReason(),
                members.stream().map(member -> new FoodProductFamilyDto.Member(
                        member.getFoodItem().getId(), member.getFoodItem().getBarcode(),
                        member.getFoodItem().getDisplayName(), member.getPackageQuantity().doubleValue(),
                        member.getPackageUnit(), member.getPackageLabel(),
                        member.isDefaultVariant(), member.isActive()
                )).toList()
        );
    }

    private List<String> splitWarnings(String value) {
        return value == null || value.isBlank() ? List.of() : value.lines().filter(line -> !line.isBlank()).toList();
    }

    private String variantKey(String familyKey) {
        int separator = familyKey.indexOf(':');
        return separator < 0 ? familyKey : familyKey.substring(separator + 1);
    }

    private void requireActor(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Admin actor is required");
    }

    private record Validation(
            FoodProductFamilyRequestDto request,
            Map<Long, FoodItemEntity> products,
            List<String> errors,
            List<String> warnings,
            String fingerprint
    ) {
        FoodProductFamilyPreviewDto preview() {
            return new FoodProductFamilyPreviewDto(
                    errors.isEmpty(), request.familyKey(), request.representativeProductId(),
                    request.members().stream().map(FoodProductFamilyMemberRequestDto::productId).toList(),
                    List.copyOf(errors), List.copyOf(warnings), fingerprint
            );
        }
    }

    private record Addition(FoodProductFamilyEntity family,
                            List<FoodProductFamilyMemberEntity> existingMembers,
                            Validation validation) {
    }
}

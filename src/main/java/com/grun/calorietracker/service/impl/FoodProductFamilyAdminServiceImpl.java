package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyMemberRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import com.grun.calorietracker.entity.FoodProductFamilyMemberEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;
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
        if (!validation.errors().isEmpty()) {
            throw new IllegalArgumentException("Product family validation failed: " + String.join("; ", validation.errors()));
        }

        FoodItemEntity representative = validation.products().get(request.representativeProductId());
        String familyKey = request.familyKey().trim().toLowerCase(Locale.ROOT);
        FoodProductFamilyEntity family = new FoodProductFamilyEntity();
        family.setFamilyKey(familyKey);
        family.setBrandKey(FoodBrandSearchRules.key(representative.getBrand()));
        family.setVariantKey(variantKey(familyKey));
        family.setDisplayName(request.displayName().trim());
        family.setRepresentativeFoodItem(representative);
        family.setEvidenceReason(request.evidenceReason().trim());
        family.setAppliedBy(actor.trim());
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
            member.setPackageLabel(requested.packageLabel().trim());
            member.setDefaultVariant(requested.defaultVariant());
            member.setSortOrder(sortOrder++);
            member.setActive(true);
            members.add(member);
        }
        memberRepository.saveAllAndFlush(members);
        return toDto(family, members);
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
        });

        Map<Long, FoodItemEntity> products = new LinkedHashMap<>();
        foodItemRepository.findAllById(uniqueIds).forEach(product -> products.put(product.getId(), product));
        uniqueIds.stream().filter(id -> !products.containsKey(id))
                .forEach(id -> errors.add("Food product not found: " + id));
        familyRepository.findFirstByFamilyKeyIgnoreCaseAndActiveTrue(request.familyKey().trim())
                .ifPresent(existing -> errors.add("Family key already exists: " + existing.getId()));
        memberRepository.findActiveMembershipsForProducts(uniqueIds).forEach(member ->
                errors.add("Product " + member.getFoodItem().getId()
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
        if (request.displayName().matches(".*\\b\\d+(?:[.,]\\d+)?\\s*(?:g|gr|kg|ml|l)\\b.*")) {
            warnings.add("Family display name appears to contain a package size");
        }
        return new Validation(request, products, errors, warnings);
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
                members.stream().map(member -> new FoodProductFamilyDto.Member(
                        member.getFoodItem().getId(), member.getFoodItem().getBarcode(),
                        member.getFoodItem().getDisplayName(), member.getPackageQuantity().doubleValue(),
                        member.getPackageUnit(), member.getPackageLabel(),
                        member.isDefaultVariant(), member.isActive()
                )).toList()
        );
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
            List<String> warnings
    ) {
        FoodProductFamilyPreviewDto preview() {
            return new FoodProductFamilyPreviewDto(
                    errors.isEmpty(), request.familyKey(), request.representativeProductId(),
                    request.members().stream().map(FoodProductFamilyMemberRequestDto::productId).toList(),
                    List.copyOf(errors), List.copyOf(warnings)
            );
        }
    }
}

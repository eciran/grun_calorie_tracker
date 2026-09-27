package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.CatalogClassificationQualityDto;
import com.grun.calorietracker.dto.FoodBrandSummaryDto;
import com.grun.calorietracker.dto.FoodCategoryTreeDto;
import com.grun.calorietracker.entity.FoodBrandEntity;
import com.grun.calorietracker.entity.FoodCategoryEntity;
import com.grun.calorietracker.enums.FoodBrandStatus;
import com.grun.calorietracker.repository.FoodBrandAliasRepository;
import com.grun.calorietracker.repository.FoodBrandRepository;
import com.grun.calorietracker.repository.FoodCategoryRepository;
import com.grun.calorietracker.service.CatalogClassificationService;
import com.grun.calorietracker.service.support.FoodBrandSearchRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogClassificationServiceImpl implements CatalogClassificationService {
    private final FoodBrandRepository brandRepository;
    private final FoodBrandAliasRepository aliasRepository;
    private final FoodCategoryRepository categoryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<FoodBrandSummaryDto> autocompleteBrands(String query, int limit) {
        String text = query == null ? "" : query.trim();
        String normalized = normalizedKey(text).orElse("");
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return brandRepository.autocomplete(text, normalized, PageRequest.of(0, boundedLimit)).stream()
                .map(this::brandDto)
                .toList();
    }

    @Override
    public Optional<FoodBrandSummaryDto> resolveBrand(String value) {
        return normalizedKey(value).flatMap(key ->
                brandRepository.findByNormalizedKeyAndStatus(key, FoodBrandStatus.ACTIVE)
                        .or(() -> aliasRepository.findByNormalizedAlias(key)
                                .map(alias -> canonicalTarget(alias.getBrand())))
        ).map(this::brandDto);
    }

    @Override
    public List<FoodCategoryTreeDto> activeCategoryTree() {
        List<FoodCategoryEntity> categories = categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameEnAsc();
        validateNoCycles(categories);
        Map<Long, List<FoodCategoryEntity>> children = new LinkedHashMap<>();
        List<FoodCategoryEntity> roots = new ArrayList<>();
        for (FoodCategoryEntity category : categories) {
            if (category.getParent() == null) roots.add(category);
            else children.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>()).add(category);
        }
        Comparator<FoodCategoryEntity> order = Comparator.comparingInt(FoodCategoryEntity::getSortOrder)
                .thenComparing(FoodCategoryEntity::getNameEn).thenComparing(FoodCategoryEntity::getId);
        roots.sort(order);
        children.values().forEach(items -> items.sort(order));
        return roots.stream().map(root -> categoryDto(root, children, new java.util.HashSet<>())).toList();
    }

    private void validateNoCycles(List<FoodCategoryEntity> categories) {
        for (FoodCategoryEntity category : categories) {
            java.util.Set<Long> path = new java.util.HashSet<>();
            FoodCategoryEntity current = category;
            while (current != null) {
                if (current.getId() == null || !path.add(current.getId())) {
                    throw new IllegalStateException("Category cycle detected for id=" + category.getId());
                }
                current = current.getParent();
            }
        }
    }

    @Override
    public CatalogClassificationQualityDto qualitySummary() {
        long total = count("select count(*) from food_items");
        long legacy = count("select count(*) from food_items where brand is not null and trim(brand) <> ''");
        long canonical = count("select count(*) from food_items where brand_id is not null");
        long primary = count("select count(*) from food_item_categories where primary_category");
        long missing = count("""
                select count(*) from food_items
                where catalog_type = 'BRANDED_PRODUCT' and (brand is null or trim(brand) = '')
                """);
        return new CatalogClassificationQualityDto(total, legacy, canonical, primary, missing,
                Math.max(0, legacy - canonical));
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private Optional<String> normalizedKey(String value) {
        return Optional.ofNullable(FoodBrandSearchRules.key(value)).filter(key -> !key.isBlank());
    }

    private FoodBrandEntity canonicalTarget(FoodBrandEntity brand) {
        FoodBrandEntity current = brand;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (current.getStatus() == FoodBrandStatus.MERGED && current.getMergedInto() != null
                && current.getId() != null && visited.add(current.getId())) {
            current = current.getMergedInto();
        }
        return current;
    }

    private FoodBrandSummaryDto brandDto(FoodBrandEntity brand) {
        FoodBrandEntity canonical = canonicalTarget(brand);
        return new FoodBrandSummaryDto(canonical.getId(), canonical.getCanonicalName(),
                canonical.getManufacturerName(), canonical.getCountryCode(), canonical.getLogoUrl(),
                canonical.isVerified());
    }

    private FoodCategoryTreeDto categoryDto(FoodCategoryEntity category,
                                             Map<Long, List<FoodCategoryEntity>> children,
                                             java.util.Set<Long> path) {
        if (!path.add(category.getId())) {
            throw new IllegalStateException("Category cycle detected for id=" + category.getId());
        }
        List<FoodCategoryTreeDto> nested = children.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> categoryDto(child, children, new java.util.HashSet<>(path))).toList();
        return new FoodCategoryTreeDto(category.getId(), category.getSlug(), category.getNameEn(),
                category.getNameTr(), category.getDescriptionEn(), category.getDescriptionTr(),
                category.getIconKey(), category.getImageUrl(), category.getSortOrder(), nested);
    }
}

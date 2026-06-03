package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.OpenFoodFactsService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final OpenFoodFactsService openFoodFactsService;

    public FoodItemServiceImpl(FoodItemRepository foodItemRepository, OpenFoodFactsService openFoodFactsService) {
        this.foodItemRepository = foodItemRepository;
        this.openFoodFactsService = openFoodFactsService;
    }

    @Override
    public FoodItemEntity getOrSaveFoodItemByBarcode(String barcode) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        if (normalizedBarcode == null) {
            throw new ProductNotFoundException("Product barcode must not be empty.");
        }

        java.util.Optional<FoodItemEntity> localProduct = findByNormalizedBarcode(normalizedBarcode);
        if (localProduct.isPresent()) {
            FoodItemEntity product = localProduct.get();
            if (isRejected(product)) {
                throw new ProductNotFoundException("Product is not available for barcode: " + normalizedBarcode);
            }
            return product;
        }

        return fetchAndCacheExternalProduct(normalizedBarcode);
    }

    @Override
    public List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria) {
        return searchFoodItems(criteria, 0, 100).getContent();
    }

    @Override
    public FoodProductSearchPageDto searchFoodItems(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        Sort sort = buildSort(safeCriteria);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), sort);

        Page<FoodItemEntity> prioritizedLocalProducts = searchLocalProductsByRegionPriority(safeCriteria, pageable);
        if (prioritizedLocalProducts.hasContent()) {
            return toSearchPageDto(prioritizedLocalProducts);
        }

        Specification<FoodItemEntity> specification = buildSearchSpecification(safeCriteria, true);
        Page<FoodItemEntity> localProducts = foodItemRepository.findAll(specification, pageable);
        if (localProducts.hasContent()) {
            return toSearchPageDto(localProducts);
        }

        return searchAndCacheExternalProducts(safeCriteria, pageable);
    }

    private Specification<FoodItemEntity> buildSearchSpecification(FoodSearchCriteriaDto criteria, boolean expandRegionFallbacks) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("verificationStatus")),
                    criteriaBuilder.notEqual(root.get("verificationStatus"), VerificationStatus.REJECTED)
            ));
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("isCustom")),
                    criteriaBuilder.isFalse(root.get("isCustom"))
            ));

            String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
            if (searchQuery != null) {
                String pattern = "%" + searchQuery.toLowerCase(Locale.ROOT) + "%";
                String normalizedBarcodeQuery = FoodProductNormalizationRules.normalizeBarcode(searchQuery);
                String barcodePattern = normalizedBarcodeQuery == null
                        ? pattern
                        : "%" + normalizedBarcodeQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("normalizedBarcode")), barcodePattern)
                ));
            }

            String nutriScore = FoodProductNormalizationRules.normalizeText(criteria.getNutriScore());
            if (nutriScore != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("nutriScore")),
                        nutriScore.toLowerCase(Locale.ROOT)
                ));
            }

            if (criteria.getMinCalories() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("calories"), criteria.getMinCalories()));
            }

            if (criteria.getMaxCalories() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("calories"), criteria.getMaxCalories()));
            }

            if (criteria.getMarketRegion() != null) {
                if (expandRegionFallbacks) {
                    predicates.add(root.get("marketRegion").in(resolveSearchRegions(criteria.getMarketRegion())));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("marketRegion"), criteria.getMarketRegion()));
                }
            }

            if (criteria.getCatalogType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("catalogType"), criteria.getCatalogType()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(FoodSearchCriteriaDto criteria) {
        String sortBy = FoodProductNormalizationRules.normalizeText(criteria.getSortBy());
        String sortOrder = FoodProductNormalizationRules.normalizeText(criteria.getSortOrder());
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return switch (sortBy == null ? "" : sortBy) {
            case "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "nutriScore" ->
                    Sort.by(direction, sortBy);
            case "qualityScore", "usageCount" -> Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "name"));
            default -> buildDefaultSearchSort();
        };
    }

    private Sort buildDefaultSearchSort() {
        return Sort.by(
                Sort.Order.desc("qualityScore"),
                Sort.Order.desc("usageCount"),
                Sort.Order.asc("name")
        );
    }

    private FoodItemEntity fetchAndCacheExternalProduct(String barcode) {
        FoodProductDto externalProduct = openFoodFactsService.getProductByBarcode(barcode)
                .orElseThrow(() -> new ProductNotFoundException("Product not found for barcode: " + barcode));

        FoodItemEntity entity = buildImportedFoodItem(externalProduct, barcode);
        return foodItemRepository.save(entity);
    }

    private FoodProductSearchPageDto searchAndCacheExternalProducts(FoodSearchCriteriaDto criteria, Pageable pageable) {
        String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
        if (searchQuery == null || pageable.getPageNumber() > 0) {
            return emptySearchPage(pageable);
        }

        List<FoodItemEntity> cachedProducts = openFoodFactsService.searchProductsByCriteria(criteria)
                .stream()
                .map(this::cacheExternalSearchProduct)
                .filter(Objects::nonNull)
                .toList();

        if (cachedProducts.isEmpty()) {
            return emptySearchPage(pageable);
        }

        int fromIndex = Math.min((int) pageable.getOffset(), cachedProducts.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), cachedProducts.size());
        return toSearchPageDto(
                new org.springframework.data.domain.PageImpl<>(
                        cachedProducts.subList(fromIndex, toIndex),
                        pageable,
                        cachedProducts.size()
                )
        );
    }

    private FoodItemEntity cacheExternalSearchProduct(FoodProductDto externalProduct) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(externalProduct.getBarcode());
        if (normalizedBarcode == null) {
            return null;
        }

        java.util.Optional<FoodItemEntity> localProduct = findByNormalizedBarcode(normalizedBarcode);
        if (localProduct.isPresent()) {
            FoodItemEntity product = localProduct.get();
            return isRejected(product) ? null : product;
        }

        return foodItemRepository.save(buildImportedFoodItem(externalProduct, normalizedBarcode));
    }

    private FoodItemEntity buildImportedFoodItem(FoodProductDto externalProduct, String barcode) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        FoodItemEntity entity = FoodItemMapper.mapDtoToEntity(externalProduct);
        entity.setBarcode(normalizedBarcode);
        entity.setNormalizedBarcode(normalizedBarcode);
        entity.setSourceKey("barcode:" + normalizedBarcode);
        entity.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        entity.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        entity.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        entity.setExternalImageUrl(resolveExternalImageUrl(externalProduct));
        entity.setDisplayImageUrl(null);
        entity.setImageSource(ImageSource.OPEN_FOOD_FACTS);
        entity.setImageStatus(ImageStatus.NEEDS_REVIEW);
        entity.setMarketRegion(externalProduct.getMarketRegion());
        entity.setIsCustom(false);
        FoodProductQualityRules.markExternalImport(entity);
        return entity;
    }

    private String resolveExternalImageUrl(FoodProductDto product) {
        String externalImageUrl = FoodProductNormalizationRules.normalizeText(product.getExternalImageUrl());
        if (externalImageUrl != null) {
            return externalImageUrl;
        }
        return FoodProductNormalizationRules.normalizeText(product.getImageUrl());
    }

    private java.util.Optional<FoodItemEntity> findByNormalizedBarcode(String normalizedBarcode) {
        return foodItemRepository.findByNormalizedBarcode(normalizedBarcode)
                .or(() -> foodItemRepository.findByBarcode(normalizedBarcode)
                        .map(this::backfillNormalizedBarcodeIfMissing));
    }

    private FoodItemEntity backfillNormalizedBarcodeIfMissing(FoodItemEntity product) {
        if (FoodProductNormalizationRules.normalizeText(product.getNormalizedBarcode()) != null) {
            return product;
        }

        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(product.getBarcode());
        if (normalizedBarcode == null) {
            return product;
        }

        product.setNormalizedBarcode(normalizedBarcode);
        return foodItemRepository.save(product);
    }

    private boolean isRejected(FoodItemEntity product) {
        return product.getVerificationStatus() == VerificationStatus.REJECTED;
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, 100);
    }

    private Page<FoodItemEntity> searchLocalProductsByRegionPriority(FoodSearchCriteriaDto criteria, Pageable pageable) {
        if (criteria.getMarketRegion() == null || hasExplicitSort(criteria)) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        int requestedRows = Math.max(1, (int) pageable.getOffset() + pageable.getPageSize());
        List<FoodItemEntity> mergedProducts = new ArrayList<>();
        long totalElements = 0;

        for (MarketRegion region : resolveSearchRegions(criteria.getMarketRegion())) {
            FoodSearchCriteriaDto regionalCriteria = copyCriteriaWithRegion(criteria, region);
            Page<FoodItemEntity> regionalPage = foodItemRepository.findAll(
                    buildSearchSpecification(regionalCriteria, false),
                    PageRequest.of(0, requestedRows, buildDefaultSearchSort())
            );
            totalElements += regionalPage.getTotalElements();
            mergedProducts.addAll(regionalPage.getContent());
        }

        int fromIndex = Math.min((int) pageable.getOffset(), mergedProducts.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), mergedProducts.size());
        return new org.springframework.data.domain.PageImpl<>(
                mergedProducts.subList(fromIndex, toIndex),
                pageable,
                totalElements
        );
    }

    private boolean hasExplicitSort(FoodSearchCriteriaDto criteria) {
        return FoodProductNormalizationRules.normalizeText(criteria.getSortBy()) != null;
    }

    private FoodSearchCriteriaDto copyCriteriaWithRegion(FoodSearchCriteriaDto criteria, MarketRegion region) {
        FoodSearchCriteriaDto copy = new FoodSearchCriteriaDto();
        copy.setQuery(criteria.getQuery());
        copy.setBrand(criteria.getBrand());
        copy.setCategory(criteria.getCategory());
        copy.setMinCalories(criteria.getMinCalories());
        copy.setMaxCalories(criteria.getMaxCalories());
        copy.setSortBy(criteria.getSortBy());
        copy.setSortOrder(criteria.getSortOrder());
        copy.setNutriScore(criteria.getNutriScore());
        copy.setMarketRegion(region);
        copy.setCatalogType(criteria.getCatalogType());
        return copy;
    }

    private List<MarketRegion> resolveSearchRegions(MarketRegion marketRegion) {
        return switch (marketRegion) {
            case UK_IE -> List.of(
                    MarketRegion.UK_IE,
                    MarketRegion.EU,
                    MarketRegion.GLOBAL
            );
            case EU -> List.of(
                    MarketRegion.EU,
                    MarketRegion.GLOBAL
            );
            case TR -> List.of(
                    MarketRegion.TR,
                    MarketRegion.GLOBAL
            );
            case GLOBAL -> List.of(MarketRegion.GLOBAL);
        };
    }

    private FoodProductSearchPageDto toSearchPageDto(Page<FoodItemEntity> products) {
        FoodProductSearchPageDto dto = new FoodProductSearchPageDto();
        dto.setContent(FoodItemMapper.mapEntityListToDtoList(products.getContent()));
        dto.setPage(products.getNumber());
        dto.setSize(products.getSize());
        dto.setTotalElements(products.getTotalElements());
        dto.setTotalPages(products.getTotalPages());
        dto.setFirst(products.isFirst());
        dto.setLast(products.isLast());
        return dto;
    }

    private FoodProductSearchPageDto emptySearchPage(Pageable pageable) {
        return toSearchPageDto(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));
    }

}

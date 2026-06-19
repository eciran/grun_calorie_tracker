package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.OpenFoodFactsService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final OpenFoodFactsService openFoodFactsService;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    public FoodItemServiceImpl(
            FoodItemRepository foodItemRepository,
            FoodItemServingOptionRepository foodItemServingOptionRepository,
            OpenFoodFactsService openFoodFactsService,
            FoodProductQualityIssueTracker foodProductQualityIssueTracker
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemServingOptionRepository = foodItemServingOptionRepository;
        this.openFoodFactsService = openFoodFactsService;
        this.foodProductQualityIssueTracker = foodProductQualityIssueTracker;
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
    @Transactional(readOnly = true)
    public FoodProductDto getFoodItemById(Long id, String email) {
        FoodItemEntity product = foodItemRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
        if (isRejected(product) || !isVisibleToUser(product, email)) {
            throw new ProductNotFoundException("Product not found: " + id);
        }
        return toProductDto(product);
    }

    @Override
    public List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria) {
        return searchFoodItems(criteria, 0, 100).getContent();
    }

    @Override
    public FoodProductSearchPageDto searchFoodItems(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        Sort sort = hasExplicitSort(safeCriteria) ? buildSort(safeCriteria) : Sort.unsorted();
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), sort);

        Page<FoodItemEntity> prioritizedLocalProducts = searchLocalProductsByRegionPriority(safeCriteria, pageable);
        if (prioritizedLocalProducts.hasContent()) {
            return toSearchPageDto(prioritizedLocalProducts);
        }

        Specification<FoodItemEntity> specification = buildSearchSpecification(safeCriteria, true, !hasExplicitSort(safeCriteria));
        Page<FoodItemEntity> localProducts = foodItemRepository.findAll(specification, pageable);
        if (localProducts.hasContent()) {
            return toSearchPageDto(localProducts);
        }

        return searchAndCacheExternalProducts(safeCriteria, pageable);
    }

    private Specification<FoodItemEntity> buildSearchSpecification(
            FoodSearchCriteriaDto criteria,
            boolean expandRegionFallbacks,
            boolean applyDefaultOrdering
    ) {
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
                List<Predicate> searchPredicates = new ArrayList<>();
                for (String term : FoodProductNormalizationRules.expandSearchTerms(searchQuery)) {
                    String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), pattern));
                }
                String normalizedBarcodeQuery = FoodProductNormalizationRules.normalizeBarcode(searchQuery);
                String barcodePattern = normalizedBarcodeQuery == null
                        ? "%" + searchQuery.toLowerCase(Locale.ROOT) + "%"
                        : "%" + normalizedBarcodeQuery.toLowerCase(Locale.ROOT) + "%";
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), barcodePattern));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("normalizedBarcode")), barcodePattern));
                predicates.add(criteriaBuilder.or(searchPredicates.toArray(new Predicate[0])));
            }

            String brand = FoodProductNormalizationRules.normalizeText(criteria.getBrand());
            if (brand != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("brand")),
                        "%" + brand.toLowerCase(Locale.ROOT) + "%"
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

            if (applyDefaultOrdering && query != null) {
                query.orderBy(buildDefaultSearchOrders(root, criteriaBuilder, searchQuery));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<jakarta.persistence.criteria.Order> buildDefaultSearchOrders(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery
    ) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();

        if (searchQuery != null) {
            String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);
            var lowerName = criteriaBuilder.lower(root.get("name"));
            var lowerBrand = criteriaBuilder.lower(root.get("brand"));
            orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                    .when(criteriaBuilder.equal(lowerName, normalizedQuery), 0)
                    .when(criteriaBuilder.equal(lowerBrand, normalizedQuery), 1)
                    .when(criteriaBuilder.or(
                            criteriaBuilder.like(lowerName, normalizedQuery + " %"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery + " %"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery),
                            criteriaBuilder.like(lowerBrand, normalizedQuery + " %"),
                            criteriaBuilder.like(lowerBrand, "% " + normalizedQuery + " %"),
                            criteriaBuilder.like(lowerBrand, "% " + normalizedQuery)
                    ), 2)
                    .when(criteriaBuilder.or(
                            criteriaBuilder.like(lowerName, normalizedQuery + "%"),
                            criteriaBuilder.like(lowerBrand, normalizedQuery + "%")
                    ), 3)
                    .otherwise(4)));
            orders.add(criteriaBuilder.asc(criteriaBuilder.length(root.get("name"))));
        }

        orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED), 0)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.NEEDS_REVIEW), 1)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.RAW_IMPORTED), 2)
                .otherwise(3)));

        orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.LOCAL_DISH), 0)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.GENERIC_INGREDIENT), 1)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.BRANDED_PRODUCT), 2)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.USER_CUSTOM), 3)
                .otherwise(4)));

        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("qualityScore"), 0)));
        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("usageCount"), 0L)));
        orders.add(criteriaBuilder.asc(root.get("name")));
        return orders;
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
        FoodItemEntity saved = foodItemRepository.save(entity);
        foodProductQualityIssueTracker.syncReviewIssues(saved, "open-food-facts");
        return saved;
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

        FoodItemEntity saved = foodItemRepository.save(buildImportedFoodItem(externalProduct, normalizedBarcode));
        foodProductQualityIssueTracker.syncReviewIssues(saved, "open-food-facts");
        return saved;
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

    private boolean isVisibleToUser(FoodItemEntity product, String email) {
        if (!Boolean.TRUE.equals(product.getIsCustom())) {
            return true;
        }
        UserEntity owner = product.getCreatedByUser();
        return owner != null
                && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(email);
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
                    buildSearchSpecification(regionalCriteria, false, true),
                    PageRequest.of(0, requestedRows, Sort.unsorted())
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
        dto.setContent(products.getContent().stream().map(this::toProductDto).toList());
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

    private FoodProductDto toProductDto(FoodItemEntity product) {
        FoodProductDto dto = FoodItemMapper.mapEntityToDto(product);
        foodItemServingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product)
                .stream()
                .findFirst()
                .ifPresent(option -> dto.setDefaultServingOptionId(option.getId()));
        return dto;
    }
}

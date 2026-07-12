package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.mapper.FoodServingOptionMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.OpenFoodFactsService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.Set;

@Service
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final OpenFoodFactsService openFoodFactsService;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    public FoodItemServiceImpl(
            FoodItemRepository foodItemRepository,
            FoodItemLocalizationRepository foodItemLocalizationRepository,
            FoodItemServingOptionRepository foodItemServingOptionRepository,
            OpenFoodFactsService openFoodFactsService,
            FoodProductQualityIssueTracker foodProductQualityIssueTracker
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemLocalizationRepository = foodItemLocalizationRepository;
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
    @Transactional
    @Cacheable(cacheNames = "foodProductByBarcode", key = "T(com.grun.calorietracker.service.support.FoodProductCacheKeys).barcode(#barcode)", unless = "#result == null")
    public FoodProductDto getFoodProductByBarcode(String barcode) {
        return toProductDto(getOrSaveFoodItemByBarcode(barcode));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "foodProductById", key = "#id + ':' + (#email == null ? 'anonymous' : #email.toLowerCase())", unless = "#result == null")
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
    @Cacheable(cacheNames = "foodProductSearch", key = "T(com.grun.calorietracker.service.support.FoodProductCacheKeys).search(#criteria, #page, #size)", unless = "#result == null")
    public FoodProductSearchPageDto searchFoodItems(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        Sort sort = hasExplicitSort(safeCriteria) ? buildSort(safeCriteria) : Sort.unsorted();
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), sort);

        Page<FoodItemEntity> prioritizedLocalProducts = searchLocalProductsByRegionPriority(safeCriteria, pageable);
        if (prioritizedLocalProducts.hasContent()) {
            return toSearchPageDto(prioritizedLocalProducts, safeCriteria.getPreferredLanguage());
        }

        Specification<FoodItemEntity> specification = buildSearchSpecification(safeCriteria, true, !hasExplicitSort(safeCriteria));
        Page<FoodItemEntity> localProducts = foodItemRepository.findAll(specification, pageable);
        if (localProducts.hasContent()) {
            return toSearchPageDto(localProducts, safeCriteria.getPreferredLanguage());
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
            predicates.add(criteriaBuilder.not(criteriaBuilder.exists(blockingQualityIssueSubquery(root, query, criteriaBuilder))));

            String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
            if (searchQuery != null) {
                List<Predicate> searchPredicates = new ArrayList<>();
                for (String term : FoodProductNormalizationRules.expandSearchTerms(searchQuery)) {
                    String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                    String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(term);
                    String normalizedAliasPattern = normalizedAlias == null ? pattern : "%" + normalizedAlias + "%";
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDisplayName")), pattern));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), pattern));
                    var aliasSubquery = query.subquery(Long.class);
                    var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
                    aliasSubquery.select(aliasRoot.get("id"));
                    aliasSubquery.where(
                            criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                            criteriaBuilder.isTrue(aliasRoot.get("active")),
                            criteriaBuilder.or(
                                    criteriaBuilder.like(criteriaBuilder.lower(aliasRoot.get("alias")), pattern),
                                    criteriaBuilder.like(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), normalizedAliasPattern)
                            )
                    );
                    searchPredicates.add(criteriaBuilder.exists(aliasSubquery));

                    var localizationSubquery = query.subquery(Long.class);
                    var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
                    localizationSubquery.select(localizationRoot.get("id"));
                    localizationSubquery.where(
                            criteriaBuilder.equal(localizationRoot.get("foodItem"), root),
                            criteriaBuilder.isTrue(localizationRoot.get("active")),
                            criteriaBuilder.or(
                                    criteriaBuilder.like(criteriaBuilder.lower(localizationRoot.get("displayName")), pattern),
                                    criteriaBuilder.like(criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), pattern)
                            )
                    );
                    searchPredicates.add(criteriaBuilder.exists(localizationSubquery));
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

            if (criteria.getPreparationState() != null) {
                predicates.add(criteriaBuilder.equal(root.get("preparationState"), criteria.getPreparationState()));
            }

            if (applyDefaultOrdering && query != null) {
                query.orderBy(buildDefaultSearchOrders(root, query, criteriaBuilder, searchQuery, criteria.getMarketRegion()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private jakarta.persistence.criteria.Subquery<Long> blockingQualityIssueSubquery(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        var subquery = query.subquery(Long.class);
        var issueRoot = subquery.from(FoodProductQualityIssueEntity.class);
        subquery.select(issueRoot.get("id"));
        subquery.where(
                criteriaBuilder.equal(issueRoot.get("foodItem"), root),
                criteriaBuilder.isFalse(issueRoot.get("resolved")),
                issueRoot.get("issueType").in(blockingUserSearchQualityIssues())
        );
        return subquery;
    }

    private List<FoodProductQualityIssue> blockingUserSearchQualityIssues() {
        return List.of(
                FoodProductQualityIssue.MISSING_CALORIES,
                FoodProductQualityIssue.MISSING_MACROS,
                FoodProductQualityIssue.SUSPICIOUS_CALORIES,
                FoodProductQualityIssue.SUSPICIOUS_MACROS
        );
    }

    private List<jakarta.persistence.criteria.Order> buildDefaultSearchOrders(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery,
            MarketRegion requestedRegion
    ) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();

        if (searchQuery != null) {
            String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);
            var lowerName = criteriaBuilder.lower(root.get("name"));
            var lowerBrand = criteriaBuilder.lower(root.get("brand"));
            orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                    .when(buildExactLocalizedNameMatch(root, query, criteriaBuilder, normalizedQuery), 0)
                    .when(buildExactAliasMatch(root, query, criteriaBuilder, searchQuery, normalizedQuery), 0)
                    .when(criteriaBuilder.equal(lowerName, normalizedQuery), 0)
                    .when(criteriaBuilder.equal(lowerBrand, normalizedQuery), 1)
                    .when(criteriaBuilder.or(
                            criteriaBuilder.like(lowerName, normalizedQuery + " %"),
                            criteriaBuilder.like(lowerName, normalizedQuery + "s %"),
                            criteriaBuilder.like(lowerName, normalizedQuery + ",%"),
                            criteriaBuilder.like(lowerName, normalizedQuery + "s,%"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery + " %"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery + "s %"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery + ",%"),
                            criteriaBuilder.like(lowerName, "% " + normalizedQuery + "s,%"),
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
            orders.add(buildCatalogTypeRank(root, criteriaBuilder));
            if (isCoreFoodQuery(searchQuery)) {
                orders.add(buildDerivedProductPenalty(root, criteriaBuilder, searchQuery));
            }
            orders.add(criteriaBuilder.asc(criteriaBuilder.length(root.get("name"))));
        }

        orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED), 0)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.NEEDS_REVIEW), 1)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.RAW_IMPORTED), 2)
                .otherwise(3)));

        if (searchQuery == null) {
            orders.add(buildCatalogTypeRank(root, criteriaBuilder));
        }

        if (requestedRegion != null) {
            List<MarketRegion> regions = resolveSearchRegions(requestedRegion);
            var regionRank = criteriaBuilder.selectCase();
            for (int index = 0; index < regions.size(); index++) {
                regionRank.when(criteriaBuilder.equal(root.get("marketRegion"), regions.get(index)), index);
            }
            orders.add(criteriaBuilder.asc(regionRank.otherwise(regions.size())));
        }

        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("qualityScore"), 0)));
        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("usageCount"), 0L)));
        orders.add(criteriaBuilder.asc(root.get("name")));
        return orders;
    }

    private Predicate buildExactLocalizedNameMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String normalizedQuery
    ) {
        var localizationSubquery = query.subquery(Long.class);
        var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
        localizationSubquery.select(localizationRoot.get("id"));
        localizationSubquery.where(
                criteriaBuilder.equal(localizationRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(localizationRoot.get("active")),
                criteriaBuilder.or(
                        criteriaBuilder.equal(criteriaBuilder.lower(localizationRoot.get("displayName")), normalizedQuery),
                        criteriaBuilder.equal(criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), normalizedQuery)
                )
        );
        return criteriaBuilder.exists(localizationSubquery);
    }

    private Predicate buildExactAliasMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery,
            String normalizedQuery
    ) {
        String normalizedAliasQuery = FoodProductNormalizationRules.normalizeSearchAlias(searchQuery);
        var aliasSubquery = query.subquery(Long.class);
        var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
        aliasSubquery.select(aliasRoot.get("id"));

        Predicate aliasMatch = criteriaBuilder.equal(criteriaBuilder.lower(aliasRoot.get("alias")), normalizedQuery);
        if (normalizedAliasQuery != null) {
            aliasMatch = criteriaBuilder.or(
                    aliasMatch,
                    criteriaBuilder.equal(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), normalizedAliasQuery)
            );
        }

        aliasSubquery.where(
                criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(aliasRoot.get("active")),
                aliasMatch
        );
        return criteriaBuilder.exists(aliasSubquery);
    }

    private jakarta.persistence.criteria.Order buildDerivedProductPenalty(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery
    ) {
        if (!isCoreFoodQuery(searchQuery)) {
            return criteriaBuilder.asc(criteriaBuilder.literal(0));
        }

        var lowerName = criteriaBuilder.lower(root.get("name"));
        Predicate derivedName = criteriaBuilder.or(
                criteriaBuilder.like(lowerName, "babyfood%"),
                criteriaBuilder.like(lowerName, "baby food%"),
                criteriaBuilder.like(lowerName, "flour%"),
                criteriaBuilder.like(lowerName, "soup%"),
                criteriaBuilder.like(lowerName, "broth%"),
                criteriaBuilder.like(lowerName, "mix%"),
                criteriaBuilder.like(lowerName, "restaurant%"),
                criteriaBuilder.like(lowerName, "% babyfood%"),
                criteriaBuilder.like(lowerName, "baby food%"),
                criteriaBuilder.like(lowerName, "% baby food%"),
                criteriaBuilder.like(lowerName, "% bread%"),
                criteriaBuilder.like(lowerName, "% bread"),
                criteriaBuilder.like(lowerName, "% chips%"),
                criteriaBuilder.like(lowerName, "% chips"),
                criteriaBuilder.like(lowerName, "% loaf%"),
                criteriaBuilder.like(lowerName, "% loaf"),
                criteriaBuilder.like(lowerName, "% cake%"),
                criteriaBuilder.like(lowerName, "% cake"),
                criteriaBuilder.like(lowerName, "% cookie%"),
                criteriaBuilder.like(lowerName, "% cookie"),
                criteriaBuilder.like(lowerName, "% cookies%"),
                criteriaBuilder.like(lowerName, "% cookies"),
                criteriaBuilder.like(lowerName, "% biscuit%"),
                criteriaBuilder.like(lowerName, "% biscuit"),
                criteriaBuilder.like(lowerName, "% biscuits%"),
                criteriaBuilder.like(lowerName, "% biscuits"),
                criteriaBuilder.like(lowerName, "% snack%"),
                criteriaBuilder.like(lowerName, "% snack"),
                criteriaBuilder.like(lowerName, "% bar%"),
                criteriaBuilder.like(lowerName, "% bar"),
                criteriaBuilder.like(lowerName, "% flour%"),
                criteriaBuilder.like(lowerName, "% flour"),
                criteriaBuilder.like(lowerName, "% broth%"),
                criteriaBuilder.like(lowerName, "% broth"),
                criteriaBuilder.like(lowerName, "% soup%"),
                criteriaBuilder.like(lowerName, "% soup"),
                criteriaBuilder.like(lowerName, "% cube%"),
                criteriaBuilder.like(lowerName, "% cube"),
                criteriaBuilder.like(lowerName, "% cubes%"),
                criteriaBuilder.like(lowerName, "% cubes"),
                criteriaBuilder.like(lowerName, "% powder%"),
                criteriaBuilder.like(lowerName, "% powder")
        );
        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(derivedName, 1)
                .otherwise(0));
    }

    private boolean isCoreFoodQuery(String searchQuery) {
        String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(searchQuery);
        if (normalizedAlias == null) {
            return false;
        }
        return Set.of(
                "apple", "elma",
                "banana", "muz",
                "broccoli", "brokoli",
                "chicken", "tavuk", "chicken breast", "tavuk gogsu",
                "egg", "yumurta",
                "milk", "sut",
                "oats", "yulaf",
                "potato", "patates",
                "rice", "pirinc",
                "yogurt", "yoghurt"
        ).contains(normalizedAlias);
    }

    private jakarta.persistence.criteria.Order buildCatalogTypeRank(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.LOCAL_DISH), 0)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.GENERIC_INGREDIENT), 1)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.BRANDED_PRODUCT), 2)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.USER_CUSTOM), 3)
                .otherwise(4));
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
                ),
                criteria.getPreferredLanguage()
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
        if (criteria.getMarketRegion() == null || hasExplicitSort(criteria) || hasSearchQuery(criteria)) {
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

    private boolean hasSearchQuery(FoodSearchCriteriaDto criteria) {
        return FoodProductNormalizationRules.normalizeText(criteria.getQuery()) != null;
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
        copy.setPreparationState(criteria.getPreparationState());
        copy.setPreferredLanguage(criteria.getPreferredLanguage());
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
        return toSearchPageDto(products, null);
    }

    private FoodProductSearchPageDto toSearchPageDto(Page<FoodItemEntity> products, PreferredLanguage language) {
        FoodProductSearchPageDto dto = new FoodProductSearchPageDto();
        dto.setContent(products.getContent().stream().map(product -> toProductDto(product, language)).toList());
        dto.setPage(products.getNumber());
        dto.setSize(products.getSize());
        dto.setTotalElements(products.getTotalElements());
        dto.setTotalPages(products.getTotalPages());
        dto.setFirst(products.isFirst());
        dto.setLast(products.isLast());
        return dto;
    }

    private FoodProductSearchPageDto emptySearchPage(Pageable pageable) {
        return toSearchPageDto(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0), null);
    }

    private FoodProductDto toProductDto(FoodItemEntity product) {
        return toProductDto(product, null);
    }

    private void applyLocalization(FoodProductDto dto, FoodItemEntity product, PreferredLanguage language) {
        PreferredLanguage resolvedLanguage = language == null ? PreferredLanguage.EN : language;
        dto.setLanguage(resolvedLanguage);
        foodItemLocalizationRepository.findByFoodItemIdAndLanguageAndActiveTrue(product.getId(), resolvedLanguage)
                .or(() -> resolvedLanguage == PreferredLanguage.EN
                        ? java.util.Optional.empty()
                        : foodItemLocalizationRepository.findByFoodItemIdAndLanguageAndActiveTrue(product.getId(), PreferredLanguage.EN))
                .ifPresent(localization -> {
                    String localizedDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getDisplayName());
                    String localizedShortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getShortDisplayName());
                    if (localizedDisplayName != null) {
                        dto.setDisplayName(localizedDisplayName);
                    }
                    if (localizedShortDisplayName != null) {
                        dto.setShortDisplayName(localizedShortDisplayName);
                    } else if (localizedDisplayName != null) {
                        dto.setShortDisplayName(localizedDisplayName);
                    }
                    dto.setProductName(dto.getShortDisplayName() != null ? dto.getShortDisplayName() : dto.getDisplayName());
                });
    }
    private FoodProductDto toProductDto(FoodItemEntity product, PreferredLanguage language) {
        FoodProductDto dto = FoodItemMapper.mapEntityToDto(product);
        var servingOptions = foodItemServingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product);
        applyLocalization(dto, product, language);
        dto.setServingOptions(servingOptions.stream().map(FoodServingOptionMapper::toDto).toList());
        servingOptions.stream()
                .filter(option -> Boolean.TRUE.equals(option.getIsDefault()))
                .findFirst()
                .or(() -> servingOptions.stream().findFirst())
                .ifPresent(option -> dto.setDefaultServingOptionId(option.getId()));
        return dto;
    }
}


package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemCategoryEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseMemberEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
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
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.CatalogPublicationService;
import com.grun.calorietracker.service.OpenFoodFactsService;
import com.grun.calorietracker.service.FoodProductEvidenceService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodSearchLikePatterns;
import com.grun.calorietracker.service.support.FoodBrandSearchRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FoodItemServiceImpl implements FoodItemService {

    private static final Set<String> EXACT_FOOD_QUALIFIERS = Set.of(
            "salted", "unsalted", "tuzlu", "tuzsuz", "sweetened", "unsweetened", "sekerli", "sekersiz",
            "caffeinated", "decaffeinated", "kafeinli", "kafeinsiz", "raw", "cooked", "cig", "pismis");

    private final FoodItemRepository foodItemRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;
    private final OpenFoodFactsService openFoodFactsService;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    private final FoodProductEvidenceService foodProductEvidenceService;
    private final CatalogPublicationService catalogPublicationService;
    private PostgresFoodSearchCandidateProvider postgresFoodSearchCandidateProvider;
    private ExternalFoodImportFence externalFoodImportFence;
    public FoodItemServiceImpl(
            FoodItemRepository foodItemRepository,
            FoodItemLocalizationRepository foodItemLocalizationRepository,
            FoodItemServingOptionRepository foodItemServingOptionRepository,
            FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository,
            OpenFoodFactsService openFoodFactsService,
            FoodProductQualityIssueTracker foodProductQualityIssueTracker,
            FoodProductEvidenceService foodProductEvidenceService,
            CatalogPublicationService catalogPublicationService
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemLocalizationRepository = foodItemLocalizationRepository;
        this.foodItemServingOptionRepository = foodItemServingOptionRepository;
        this.foodItemServingOptionLocalizationRepository = foodItemServingOptionLocalizationRepository;
        this.openFoodFactsService = openFoodFactsService;
        this.foodProductQualityIssueTracker = foodProductQualityIssueTracker;
        this.foodProductEvidenceService = foodProductEvidenceService;
        this.catalogPublicationService = catalogPublicationService;
    }

    @Autowired(required = false)
    void setPostgresFoodSearchCandidateProvider(PostgresFoodSearchCandidateProvider candidateProvider) {
        this.postgresFoodSearchCandidateProvider = candidateProvider;
    }

    @Autowired
    void setExternalFoodImportFence(ExternalFoodImportFence fence) {
        this.externalFoodImportFence = fence;
    }

    @Override
    @Transactional
    public FoodItemEntity getOrSaveFoodItemByBarcode(String barcode) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        if (normalizedBarcode == null) {
            throw new ProductNotFoundException("Product barcode must not be empty.");
        }

        java.util.Optional<FoodItemEntity> localProduct = findByNormalizedBarcode(normalizedBarcode);
        if (localProduct.isPresent()) {
            FoodItemEntity product = localProduct.get();
            if (isRejected(product) || !isPublished(product) || !FoodProductQualityRules.hasValidCoreNutrition(product)) {
                throw new ProductNotFoundException("Product is not available for barcode: " + normalizedBarcode);
            }
            return product;
        }

        return fetchAndCacheExternalProduct(normalizedBarcode);
    }

    @Override
    @Transactional
    @Cacheable(cacheNames = "foodProductByBarcode", cacheResolver = "foodProductCacheResolver", key = "@foodProductCacheRevision.key(T(com.grun.calorietracker.service.support.FoodProductCacheKeys).barcode(#barcode))", unless = "#result == null")
    public FoodProductDto getFoodProductByBarcode(String barcode) {
        return toProductDto(getOrSaveFoodItemByBarcode(barcode));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "foodProductById", cacheResolver = "foodProductCacheResolver", key = "@foodProductCacheRevision.key(#id + ':' + @foodProductCacheUserScopeResolver.resolve(#email))", unless = "#result == null")
    public FoodProductDto getFoodItemById(Long id, String email) {
        return getFoodItemById(id, email, PreferredLanguage.EN);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "foodProductById", cacheResolver = "foodProductCacheResolver", key = "@foodProductCacheRevision.key(#id + ':' + @foodProductCacheUserScopeResolver.resolve(#email) + ':' + (#language == null ? 'EN' : #language.name()))", unless = "#result == null")
    public FoodProductDto getFoodItemById(Long id, String email, PreferredLanguage language) {
        FoodItemEntity product = foodItemRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
        if (isRejected(product) || !isVisibleToUser(product, email)
                || (!Boolean.TRUE.equals(product.getIsCustom()) && !FoodProductQualityRules.hasValidCoreNutrition(product))) {
            throw new ProductNotFoundException("Product not found: " + id);
        }
        return toProductDto(product, language);
    }

    @Override
    // A search may cache a trusted Open Food Facts result when the local catalog has no match.
    // Keep this transaction writable so that publication, quality issue and evidence writes are atomic.
    @Transactional
    public List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria) {
        return searchFoodItems(criteria, 0, 100).getContent();
    }

    @Override
    @Transactional
    @Cacheable(cacheNames = "foodProductSearch", cacheResolver = "foodProductCacheResolver", key = "@foodProductCacheRevision.key(T(com.grun.calorietracker.service.support.FoodProductCacheKeys).search(#criteria, #page, #size))", unless = "#result == null || !#result.cacheable")
    public FoodProductSearchPageDto searchFoodItems(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        FoodProductNormalizationRules.validateSearchQuery(safeCriteria.getQuery());
        Sort sort = hasExplicitSort(safeCriteria) ? buildSort(safeCriteria) : Sort.unsorted();
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), sort);

        Page<FoodItemEntity> localProducts = searchLocalProducts(safeCriteria, pageable);
        if (localProducts.hasContent() || localProducts.getTotalElements() > 0) {
            return toSearchPageDto(localProducts, safeCriteria.getPreferredLanguage());
        }

        // OFF cannot supply canonical categories or curated generic/local-dish catalog entries.
        if (safeCriteria.getCategoryId() != null || (safeCriteria.getCatalogType() != null
                && safeCriteria.getCatalogType() != FoodCatalogType.BRANDED_PRODUCT)) {
            return toSearchPageDto(localProducts, safeCriteria.getPreferredLanguage());
        }

        return searchAndCacheExternalProducts(safeCriteria, pageable);
    }

    private Page<FoodItemEntity> searchLocalProducts(FoodSearchCriteriaDto criteria, Pageable pageable) {
        Specification<FoodItemEntity> specification = buildSearchSpecification(criteria, true, !hasExplicitSort(criteria));
        String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
        if (searchQuery != null && postgresFoodSearchCandidateProvider != null) {
            var candidateIds = postgresFoodSearchCandidateProvider.findCandidateIds(searchQuery);
            if (candidateIds.isPresent()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                        root.get("id").in(candidateIds.get()));
            }
        }
        return foodItemRepository.findAll(specification, pageable);
    }

    private Specification<FoodItemEntity> buildSearchSpecification(
            FoodSearchCriteriaDto criteria,
            boolean expandRegionFallbacks,
            boolean applyDefaultOrdering
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("publicationStatus").in(
                    CatalogPublicationStatus.PUBLISHED
            ));
            predicates.add(criticalNutritionEligibilityPredicate(root, criteriaBuilder));
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("verificationStatus")),
                    criteriaBuilder.notEqual(root.get("verificationStatus"), VerificationStatus.REJECTED)
            ));
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("isCustom")),
                    criteriaBuilder.isFalse(root.get("isCustom"))
            ));
            var brandedCollapseSubquery = query.subquery(Long.class);
            var brandedCollapseMember = brandedCollapseSubquery
                    .from(FoodBrandedDuplicateSearchCollapseMemberEntity.class);
            brandedCollapseSubquery.select(brandedCollapseMember.get("id"));
            brandedCollapseSubquery.where(
                    criteriaBuilder.equal(brandedCollapseMember.get("suppressedFoodItem"), root),
                    criteriaBuilder.isTrue(brandedCollapseMember.get("collapse").get("active"))
            );
            predicates.add(criteriaBuilder.not(criteriaBuilder.exists(brandedCollapseSubquery)));

            var canonicalResolutionSubquery = query.subquery(String.class);
            predicates.add(criteriaBuilder.not(criteriaBuilder.exists(
                    blockingQualityIssueSubquery(root, query, criteriaBuilder))));
            var canonicalResolutionRoot = canonicalResolutionSubquery.from(FoodCanonicalResolutionEntity.class);
            jakarta.persistence.criteria.Path<FoodItemEntity> resolvedPrimary =
                    canonicalResolutionRoot.get("primaryFoodItem");
            canonicalResolutionSubquery.select(canonicalResolutionRoot.get("canonicalFoodKey"));
            canonicalResolutionSubquery.where(
                    criteriaBuilder.equal(
                            canonicalResolutionRoot.get("canonicalFoodKey"),
                            root.get("canonicalFoodKey")
                    ),
                    criteriaBuilder.notEqual(resolvedPrimary, root),
                    resolvedPrimary.get("publicationStatus").in(
                            CatalogPublicationStatus.PUBLISHED
                    ),
                    visibleVerificationStatusPredicate(resolvedPrimary, criteriaBuilder),
                    criticalNutritionEligibilityPredicate(resolvedPrimary, criteriaBuilder),
                    criteriaBuilder.not(criteriaBuilder.exists(
                            blockingQualityIssueSubquery(resolvedPrimary, query, criteriaBuilder)))
            );
            predicates.add(criteriaBuilder.not(criteriaBuilder.exists(canonicalResolutionSubquery)));

            String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
            if (searchQuery != null) {
                List<Predicate> searchPredicates = new ArrayList<>();
                for (String term : FoodProductNormalizationRules.expandSearchTerms(searchQuery)) {
                    String pattern = FoodSearchLikePatterns.contains(term.toLowerCase(Locale.ROOT));
                    String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(term);
                    String normalizedAliasPattern = normalizedAlias == null ? pattern : FoodSearchLikePatterns.contains(normalizedAlias);
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, FoodSearchLikePatterns.ESCAPE));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern, FoodSearchLikePatterns.ESCAPE));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDisplayName")), pattern, FoodSearchLikePatterns.ESCAPE));
                    searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), pattern, FoodSearchLikePatterns.ESCAPE));
                    var aliasSubquery = query.subquery(Long.class);
                    var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
                    aliasSubquery.select(aliasRoot.get("id"));
                    aliasSubquery.where(
                            criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                            criteriaBuilder.isTrue(aliasRoot.get("active")),
                            criteriaBuilder.or(
                                    criteriaBuilder.like(criteriaBuilder.lower(aliasRoot.get("alias")), pattern, FoodSearchLikePatterns.ESCAPE),
                                    criteriaBuilder.like(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), normalizedAliasPattern, FoodSearchLikePatterns.ESCAPE)
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
                                    criteriaBuilder.like(criteriaBuilder.lower(localizationRoot.get("displayName")), pattern, FoodSearchLikePatterns.ESCAPE),
                                    criteriaBuilder.like(criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), pattern, FoodSearchLikePatterns.ESCAPE)
                            )
                    );
                    searchPredicates.add(criteriaBuilder.exists(localizationSubquery));
                }
                String brandKey = FoodBrandSearchRules.key(searchQuery);
                if (brandKey != null) {
                    searchPredicates.add(criteriaBuilder.equal(
                            FoodBrandSearchRules.expression(criteriaBuilder, root.get("brand")),
                            brandKey));
                }
                List<Set<String>> tokenGroups = FoodProductNormalizationRules.searchTokenVariants(searchQuery);
                // Explicit food qualifiers cannot be satisfied by a substring or a brand-only match.
                for (Set<String> variants : tokenGroups) {
                    if (variants.stream().anyMatch(this::isExactCompositionQualifier)) {
                        predicates.add(criteriaBuilder.or(variants.stream()
                                .map(token -> buildSearchTokenMatch(root, query, criteriaBuilder, token))
                                .toArray(Predicate[]::new)));
                    }
                }
                if (tokenGroups.size() >= 2) {
                    List<Predicate> tokenPredicates = new ArrayList<>();
                    for (Set<String> variants : tokenGroups) {
                        tokenPredicates.add(criteriaBuilder.or(variants.stream()
                                .map(token -> buildSearchTokenMatch(root, query, criteriaBuilder, token))
                                .toArray(Predicate[]::new)));
                    }
                    searchPredicates.add(criteriaBuilder.and(tokenPredicates.toArray(new Predicate[0])));
                }
                String normalizedBarcodeQuery = FoodProductNormalizationRules.normalizeBarcode(searchQuery);
                String barcodePattern = normalizedBarcodeQuery == null
                        ? FoodSearchLikePatterns.contains(searchQuery.toLowerCase(Locale.ROOT))
                        : FoodSearchLikePatterns.contains(normalizedBarcodeQuery.toLowerCase(Locale.ROOT));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), barcodePattern, FoodSearchLikePatterns.ESCAPE));
                searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("normalizedBarcode")), barcodePattern, FoodSearchLikePatterns.ESCAPE));
                predicates.add(criteriaBuilder.or(searchPredicates.toArray(new Predicate[0])));
                if (isCoreFoodQuery(searchQuery)) {
                    predicates.add(buildCoreFoodWordSearchMatch(
                            root, query, criteriaBuilder, searchQuery
                    ));
                }
            }
            String brand = FoodProductNormalizationRules.normalizeText(criteria.getBrand());
            if (brand != null) {
                List<Predicate> reviewedBrandMatches = new ArrayList<>();
                for (String alias : com.grun.calorietracker.service.support.FoodBrandDisplayRules.searchAliases(brand)) {
                    reviewedBrandMatches.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")),
                            FoodSearchLikePatterns.contains(alias.toLowerCase(Locale.ROOT)), FoodSearchLikePatterns.ESCAPE));
                }
                Predicate literalBrandMatch = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("brand")),
                        FoodSearchLikePatterns.contains(brand.toLowerCase(Locale.ROOT)), FoodSearchLikePatterns.ESCAPE
                );
                String brandKey = FoodBrandSearchRules.key(brand);
                reviewedBrandMatches.add(brandKey == null ? literalBrandMatch : criteriaBuilder.or(literalBrandMatch,
                        criteriaBuilder.equal(FoodBrandSearchRules.expression(
                                criteriaBuilder, root.get("brand")), brandKey)));
                predicates.add(criteriaBuilder.or(reviewedBrandMatches.toArray(new Predicate[0])));
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
                List<MarketRegion> visibleRegions = expandRegionFallbacks
                        ? resolveSearchRegions(criteria.getMarketRegion())
                        : List.of(criteria.getMarketRegion());
                predicates.add(buildMarketAvailabilityMatch(root, query, criteriaBuilder, visibleRegions));
            }

            if (criteria.getCatalogType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("catalogType"), criteria.getCatalogType()));
            }

            if (criteria.getCategoryId() != null) {
                var categorySubquery = query.subquery(Long.class);
                var assignment = categorySubquery.from(FoodItemCategoryEntity.class);
                categorySubquery.select(assignment.get("foodItem").get("id"));
                categorySubquery.where(
                        criteriaBuilder.equal(assignment.get("foodItem").get("id"), root.get("id")),
                        criteriaBuilder.or(
                                criteriaBuilder.equal(assignment.get("category").get("id"), criteria.getCategoryId()),
                                criteriaBuilder.equal(assignment.get("category").get("parent").get("id"), criteria.getCategoryId())
                        )
                );
                predicates.add(criteriaBuilder.exists(categorySubquery));
            }

            if (criteria.getPreparationState() != null) {
                predicates.add(criteriaBuilder.equal(root.get("preparationState"), criteria.getPreparationState()));
            }

            if (applyDefaultOrdering && query != null) {
                query.orderBy(buildDefaultSearchOrders(
                        root,
                        query,
                        criteriaBuilder,
                        searchQuery,
                        criteria.getMarketRegion(),
                        criteria.getPreferredLanguage()
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate buildMarketAvailabilityMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<MarketRegion> regions
    ) {
        var availabilitySubquery = query.subquery(Long.class);
        Root<FoodItemEntity> marketProduct = availabilitySubquery.from(FoodItemEntity.class);
        var marketJoin = marketProduct.<FoodItemEntity, MarketRegion>joinSet("marketRegions");
        availabilitySubquery.select(marketProduct.get("id"));
        availabilitySubquery.where(
                criteriaBuilder.equal(marketProduct.get("id"), root.get("id")),
                marketJoin.in(regions)
        );
        return criteriaBuilder.or(
                root.get("marketRegion").in(regions),
                criteriaBuilder.exists(availabilitySubquery)
        );
    }
    private Predicate buildSearchTokenMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String token
    ) {
        List<Predicate> matches = new ArrayList<>();
        matches.add(buildTokenTextMatch(criteriaBuilder.lower(root.get("name")), criteriaBuilder, token));
        matches.add(buildTokenTextMatch(criteriaBuilder.lower(root.get("displayName")), criteriaBuilder, token));
        matches.add(buildTokenTextMatch(criteriaBuilder.lower(root.get("shortDisplayName")), criteriaBuilder, token));

        Set<String> brandKeys = FoodBrandSearchRules.searchKeys(token);
        if (!isExactCompositionQualifier(token)) {
            matches.add(buildTokenTextMatch(criteriaBuilder.lower(root.get("brand")), criteriaBuilder, token));
        }
        if (!brandKeys.isEmpty() && !isExactCompositionQualifier(token)) {
            matches.add(FoodBrandSearchRules.expression(criteriaBuilder, root.get("brand")).in(brandKeys));
        }

        var aliasSubquery = query.subquery(Long.class);
        var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
        aliasSubquery.select(aliasRoot.get("id"));
        aliasSubquery.where(
                criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(aliasRoot.get("active")),
                criteriaBuilder.or(
                        buildTokenTextMatch(criteriaBuilder.lower(aliasRoot.get("alias")), criteriaBuilder, token),
                        buildTokenTextMatch(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), criteriaBuilder, token)
                )
        );
        matches.add(criteriaBuilder.exists(aliasSubquery));

        var localizationSubquery = query.subquery(Long.class);
        var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
        localizationSubquery.select(localizationRoot.get("id"));
        localizationSubquery.where(
                criteriaBuilder.equal(localizationRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(localizationRoot.get("active")),
                criteriaBuilder.or(
                        buildTokenTextMatch(criteriaBuilder.lower(localizationRoot.get("displayName")), criteriaBuilder, token),
                        buildTokenTextMatch(criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), criteriaBuilder, token)
                )
        );
        matches.add(criteriaBuilder.exists(localizationSubquery));
        return criteriaBuilder.or(matches.toArray(new Predicate[0]));
    }

    private boolean isExactCompositionQualifier(String token) {
        return EXACT_FOOD_QUALIFIERS.contains(
                FoodProductNormalizationRules.normalizeSearchAlias(token));
    }

    private Predicate buildTokenTextMatch(
            jakarta.persistence.criteria.Expression<String> text,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            String token
    ) {
        if (!isExactCompositionQualifier(token)) {
            return builder.like(text, FoodSearchLikePatterns.contains(token.toLowerCase(Locale.ROOT)), FoodSearchLikePatterns.ESCAPE);
        }
        var words = text;
        for (String separator : List.of("(", ")", "-", ",", "/", ":", ";", "\u00a0")) {
            words = builder.function("replace", String.class, words, builder.literal(separator), builder.literal(" "));
        }
        return buildExactWordMatch(words, builder, token.toLowerCase(Locale.ROOT));
    }
    private jakarta.persistence.criteria.Subquery<Long> blockingQualityIssueSubquery(
            jakarta.persistence.criteria.Path<?> root,
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

    private Predicate visibleVerificationStatusPredicate(
            jakarta.persistence.criteria.Path<?> product,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(product.get("verificationStatus")),
                criteriaBuilder.notEqual(product.get("verificationStatus"), VerificationStatus.REJECTED)
        );
    }

    private Predicate criticalNutritionEligibilityPredicate(
            jakarta.persistence.criteria.Path<?> product,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        var calories = product.<Double>get("calories");
        var protein = product.<Double>get("protein");
        var fat = product.<Double>get("fat");
        var carbs = product.<Double>get("carbs");
        return criteriaBuilder.and(
                criteriaBuilder.isNotNull(calories),
                criteriaBuilder.between(calories, 0.0, 1000.0),
                criteriaBuilder.between(protein, 0.0, 100.0),
                criteriaBuilder.between(fat, 0.0, 100.0),
                criteriaBuilder.between(carbs, 0.0, 100.0)
        );
    }

    private List<FoodProductQualityIssue> blockingUserSearchQualityIssues() {
        return FoodProductQualityRules.blockingUserSearchQualityIssues();
    }

    private List<jakarta.persistence.criteria.Order> buildDefaultSearchOrders(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery,
            MarketRegion requestedRegion,
            PreferredLanguage preferredLanguage
    ) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();

        if (searchQuery != null) {
            boolean coreFoodQuery = isCoreFoodQuery(searchQuery);
            if (coreFoodQuery) {
                boolean plainRiceQuery = isPlainRiceQuery(searchQuery);
                if (plainRiceQuery) {
                    orders.add(buildDerivedProductPenalty(root, criteriaBuilder, searchQuery));
                }
                orders.add(buildPreparedProductPriority(root, criteriaBuilder));
                orders.add(buildCoreFoodIdentityRank(
                        root, query, criteriaBuilder, searchQuery
                ));
                if (!plainRiceQuery) {
                    orders.add(buildDerivedProductPenalty(root, criteriaBuilder, searchQuery));
                }
                orders.add(buildCoreFoodCatalogTypeRank(root, criteriaBuilder));
            }

            String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);
            String queryPattern = FoodSearchLikePatterns.literal(normalizedQuery);
            if (preferredLanguage != null) {
                orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                        .when(criteriaBuilder.or(
                                buildExactLocalizedNameMatch(
                                        root,
                                        query,
                                        criteriaBuilder,
                                        normalizedQuery,
                                        preferredLanguage
                                ),
                                buildExactAliasMatch(
                                        root,
                                        query,
                                        criteriaBuilder,
                                        searchQuery,
                                        normalizedQuery,
                                        preferredLanguage
                                )
                        ), 0)
                        .otherwise(1)));
            }
            var lowerName = criteriaBuilder.lower(root.get("name"));
            var lowerBrand = criteriaBuilder.lower(root.get("brand"));
            String brandKey = FoodBrandSearchRules.key(searchQuery);
            Predicate equivalentBrand = brandKey == null ? criteriaBuilder.disjunction()
                    : criteriaBuilder.equal(FoodBrandSearchRules.expression(criteriaBuilder, root.get("brand")), brandKey);
            orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                    .when(buildExactLocalizedNameMatch(root, query, criteriaBuilder, normalizedQuery, null), 0)
                    .when(buildExactAliasMatch(root, query, criteriaBuilder, searchQuery, normalizedQuery, null), 0)
                    .when(criteriaBuilder.equal(lowerName, normalizedQuery), 0)
                    .when(buildExactBrandProductMatch(root, query, criteriaBuilder, normalizedQuery), 0)
                    .when(criteriaBuilder.or(criteriaBuilder.equal(lowerBrand, normalizedQuery), equivalentBrand), 1)
                    .when(criteriaBuilder.or(
                            criteriaBuilder.like(lowerName, queryPattern + " %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, queryPattern + "s %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, queryPattern + ",%", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, queryPattern + "s,%", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, "% " + queryPattern + " %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, "% " + queryPattern + "s %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, "% " + queryPattern + ",%", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, "% " + queryPattern + "s,%", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerName, "% " + queryPattern, FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerBrand, queryPattern + " %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerBrand, "% " + queryPattern + " %", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerBrand, "% " + queryPattern, FoodSearchLikePatterns.ESCAPE)
                    ), 2)
                    .when(criteriaBuilder.or(
                            criteriaBuilder.like(lowerName, queryPattern + "%", FoodSearchLikePatterns.ESCAPE),
                            criteriaBuilder.like(lowerBrand, queryPattern + "%", FoodSearchLikePatterns.ESCAPE)
                    ), 3)
                    .otherwise(4)));
            if (!coreFoodQuery) {
                orders.add(buildPreparedProductPriority(root, criteriaBuilder));
                orders.add(buildCatalogTypeRank(root, criteriaBuilder));
            }
        }

        if (searchQuery == null) {
            // Category browsing keeps market priority, but each product is paged once.
            if (requestedRegion != null) {
                orders.add(buildRegionPriorityOrder(root, query, criteriaBuilder, requestedRegion));
            }
            orders.add(buildCatalogTypeRank(root, criteriaBuilder));
        }

        if (requestedRegion != null && searchQuery != null) {
            orders.add(buildRegionPriorityOrder(root, query, criteriaBuilder, requestedRegion));
        }

        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("searchSelectionCount"), 0L)));
        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("usageCount"), 0L)));
        orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED), 0)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.NEEDS_REVIEW), 1)
                .when(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.RAW_IMPORTED), 2)
                .otherwise(3)));
        orders.add(criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("qualityScore"), 0)));
        if (searchQuery != null) {
            orders.add(criteriaBuilder.asc(criteriaBuilder.length(root.get("name"))));
        }
        orders.add(criteriaBuilder.asc(root.get("name")));
        orders.add(criteriaBuilder.asc(root.get("id")));
        return orders;
    }

    private jakarta.persistence.criteria.Order buildPreparedProductPriority(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.STANDARD_PREPARED_ITEM),
                        criteriaBuilder.equal(root.get("preparationState"), FoodPreparationState.PREPARED)
                ), 0)
                .when(root.get("preparationState").in(
                        FoodPreparationState.COOKED,
                        FoodPreparationState.BOILED,
                        FoodPreparationState.GRILLED,
                        FoodPreparationState.FRIED,
                        FoodPreparationState.BAKED,
                        FoodPreparationState.ROASTED,
                        FoodPreparationState.STEAMED
                ), 1)
                .when(criteriaBuilder.equal(root.get("preparationState"), FoodPreparationState.RAW), 2)
                .otherwise(3));
    }

    private jakarta.persistence.criteria.Order buildRegionPriorityOrder(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            MarketRegion requestedRegion
    ) {
        List<MarketRegion> regions = resolveSearchRegions(requestedRegion);
        var regionRank = criteriaBuilder.selectCase();
        for (int index = 0; index < regions.size(); index++) {
            regionRank.when(buildMarketAvailabilityMatch(
                    root, query, criteriaBuilder, List.of(regions.get(index))), index);
        }
        return criteriaBuilder.asc(regionRank.otherwise(regions.size()));
    }

    private Predicate buildExactBrandProductMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            String normalizedQuery
    ) {
        if (normalizedQuery.chars().noneMatch(Character::isWhitespace)) {
            return builder.disjunction();
        }
        String pairQuery = normalizedQuery.replaceAll("\\s+", " ");
        var brand = builder.lower(root.<String>get("brand"));
        List<Predicate> matches = new ArrayList<>();
        for (String field : List.of("name", "displayName", "shortDisplayName")) {
            var name = builder.lower(root.<String>get(field));
            matches.add(exactBrandNamePair(builder, brand, name, pairQuery));
        }
        var localized = query.subquery(Long.class);
        var label = localized.from(FoodItemLocalizationEntity.class);
        localized.select(label.get("id"));
        localized.where(
                builder.equal(label.get("foodItem"), root),
                builder.isTrue(label.get("active")),
                builder.or(
                        exactBrandNamePair(builder, brand, builder.lower(label.get("displayName")), pairQuery),
                        exactBrandNamePair(builder, brand, builder.lower(label.get("shortDisplayName")), pairQuery)));
        matches.add(builder.exists(localized));
        return builder.and(builder.isNotNull(brand), builder.notEqual(builder.trim(brand), ""),
                builder.or(matches.toArray(new Predicate[0])));
    }

    private Predicate exactBrandNamePair(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> brand,
            jakarta.persistence.criteria.Expression<String> name,
            String normalizedQuery
    ) {
        List<Predicate> pairs = new ArrayList<>();
        pairs.add(builder.equal(builder.concat(builder.concat(brand, " "), name), normalizedQuery));
        pairs.add(builder.equal(builder.concat(builder.concat(name, " "), brand), normalizedQuery));
        var brandKey = FoodBrandSearchRules.expression(builder, brand);
        // Normalize only the brand part. Product spelling/composition still requires an exact name.
        for (int split = normalizedQuery.indexOf(' '); split >= 0;
             split = normalizedQuery.indexOf(' ', split + 1)) {
            String prefix = normalizedQuery.substring(0, split);
            String suffix = normalizedQuery.substring(split + 1);
            Set<String> prefixKeys = FoodBrandSearchRules.searchKeys(prefix);
            if (!prefixKeys.isEmpty()) {
                pairs.add(builder.and(brandKey.in(prefixKeys), builder.equal(name, suffix)));
            }
            Set<String> suffixKeys = FoodBrandSearchRules.searchKeys(suffix);
            if (!suffixKeys.isEmpty()) {
                pairs.add(builder.and(brandKey.in(suffixKeys), builder.equal(name, prefix)));
            }
        }
        return builder.or(pairs.toArray(new Predicate[0]));
    }

    private Predicate buildExactLocalizedNameMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String normalizedQuery,
            PreferredLanguage preferredLanguage
    ) {
        var localizationSubquery = query.subquery(Long.class);
        var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
        localizationSubquery.select(localizationRoot.get("id"));
        List<Predicate> localizationPredicates = new ArrayList<>();
        localizationPredicates.add(criteriaBuilder.equal(localizationRoot.get("foodItem"), root));
        localizationPredicates.add(criteriaBuilder.isTrue(localizationRoot.get("active")));
        localizationPredicates.add(criteriaBuilder.or(
                criteriaBuilder.equal(criteriaBuilder.lower(localizationRoot.get("displayName")), normalizedQuery),
                criteriaBuilder.equal(criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), normalizedQuery)
        ));
        if (preferredLanguage != null) {
            localizationPredicates.add(criteriaBuilder.equal(
                    localizationRoot.get("language"),
                    preferredLanguage
            ));
        }
        localizationSubquery.where(localizationPredicates.toArray(new Predicate[0]));
        return criteriaBuilder.exists(localizationSubquery);
    }

    private Predicate buildExactAliasMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery,
            String normalizedQuery,
            PreferredLanguage preferredLanguage
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

        List<Predicate> aliasPredicates = new ArrayList<>();
        aliasPredicates.add(criteriaBuilder.equal(aliasRoot.get("foodItem"), root));
        aliasPredicates.add(criteriaBuilder.isTrue(aliasRoot.get("active")));
        aliasPredicates.add(aliasMatch);
        if (preferredLanguage != null) {
            aliasPredicates.add(criteriaBuilder.equal(aliasRoot.get("language"), preferredLanguage));
        }
        aliasSubquery.where(aliasPredicates.toArray(new Predicate[0]));
        return criteriaBuilder.exists(aliasSubquery);
    }
    private jakarta.persistence.criteria.Order buildCoreFoodIdentityRank(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery
    ) {
        Set<String> identityTerms = resolveSearchIdentityTerms(searchQuery);

        if (identityTerms.isEmpty()) {
            return criteriaBuilder.asc(criteriaBuilder.literal(0));
        }

        List<Predicate> identityMatches = new ArrayList<>();
        List<Predicate> exactIdentityMatches = new ArrayList<>();
        // Prefer the displayed identity; use source name only when display fields are absent.
        var effectiveName = criteriaBuilder.lower(criteriaBuilder.coalesce(
                root.<String>get("shortDisplayName"), criteriaBuilder.coalesce(
                        root.<String>get("displayName"), root.<String>get("name"))));
        for (String term : identityTerms) {
            exactIdentityMatches.add(criteriaBuilder.equal(effectiveName, term));
            exactIdentityMatches.add(buildExactLocalizedNameMatch(root, query, criteriaBuilder, term, null));
            identityMatches.add(buildWholeWordMatch(effectiveName, criteriaBuilder, term));
            identityMatches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("displayName")), criteriaBuilder, term));
            identityMatches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("shortDisplayName")), criteriaBuilder, term));
        }

        var aliasSubquery = query.subquery(Long.class);
        var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
        List<Predicate> aliasMatches = new ArrayList<>();
        for (String term : identityTerms) {
            aliasMatches.add(buildWholeWordMatch(criteriaBuilder.lower(aliasRoot.get("alias")), criteriaBuilder, term));
            aliasMatches.add(buildWholeWordMatch(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), criteriaBuilder, term));
        }
        aliasSubquery.select(aliasRoot.get("id"));
        aliasSubquery.where(
                criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(aliasRoot.get("active")),
                criteriaBuilder.or(aliasMatches.toArray(new Predicate[0]))
        );
        Predicate aliasIdentityMatch = criteriaBuilder.exists(aliasSubquery);

        var localizationSubquery = query.subquery(Long.class);
        var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
        List<Predicate> localizationMatches = new ArrayList<>();
        for (String term : identityTerms) {
            localizationMatches.add(buildWholeWordMatch(
                    criteriaBuilder.lower(localizationRoot.get("displayName")), criteriaBuilder, term));
            localizationMatches.add(buildWholeWordMatch(
                    criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), criteriaBuilder, term));
        }
        localizationSubquery.select(localizationRoot.get("id"));
        localizationSubquery.where(
                criteriaBuilder.equal(localizationRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(localizationRoot.get("active")),
                criteriaBuilder.or(localizationMatches.toArray(new Predicate[0]))
        );
        identityMatches.add(criteriaBuilder.exists(localizationSubquery));

        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(buildStandardGenericIdentityMatch(root, query, criteriaBuilder, searchQuery, effectiveName), 0)
                .when(criteriaBuilder.or(exactIdentityMatches.toArray(new Predicate[0])), 0)
                .when(criteriaBuilder.or(identityMatches.toArray(new Predicate[0])), 1)
                .when(aliasIdentityMatch, 2)
                .otherwise(3));
    }

    private Predicate buildStandardGenericIdentityMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery,
            jakarta.persistence.criteria.Expression<String> effectiveName
    ) {
        Set<String> baseNames = new java.util.LinkedHashSet<>(resolveSearchIdentityTerms(searchQuery));
        String normalized = FoodProductNormalizationRules.normalizeSearchAlias(searchQuery);
        if (Set.of("milk", "sut").contains(normalized)) {
            baseNames.addAll(List.of("whole milk", "skim milk", "skimmed milk", "semi-skimmed milk",
                    "semi skimmed milk", "low-fat milk", "reduced-fat milk", "nonfat milk",
                    "tam ya\u011fl\u0131 s\u00fct", "yar\u0131m ya\u011fl\u0131 s\u00fct", "ya\u011fs\u0131z s\u00fct"));
        } else if (Set.of("rice", "pirinc").contains(normalized)) {
            baseNames.addAll(List.of("white rice", "brown rice", "beyaz pirin\u00e7", "esmer pirin\u00e7"));
        } else if (Set.of("banana", "muz").contains(normalized)) {
            baseNames.add("bananas");
        }
        Set<String> standardNames = new java.util.LinkedHashSet<>();
        for (String base : baseNames) {
            standardNames.add(base);
            for (String prefix : List.of("raw", "cooked", "boiled", "grilled", "roasted", "baked",
                    "steamed", "fried", "prepared", "\u00e7i\u011f", "pi\u015fmi\u015f", "ha\u015flanm\u0131\u015f",
                    "\u0131zgara", "f\u0131r\u0131nlanm\u0131\u015f", "haz\u0131rlanm\u0131\u015f")) {
                standardNames.add(prefix + " " + base);
            }
        }
        // Match complete displayed identities, never broad aliases or arbitrary descriptors.
        var localizationSubquery = query.subquery(Long.class);
        var localization = localizationSubquery.from(FoodItemLocalizationEntity.class);
        var localizedIdentity = criteriaBuilder.lower(criteriaBuilder.coalesce(
                localization.<String>get("shortDisplayName"), localization.<String>get("displayName")));
        localizationSubquery.select(localization.get("id"));
        localizationSubquery.where(
                criteriaBuilder.equal(localization.get("foodItem"), root),
                criteriaBuilder.isTrue(localization.get("active")),
                localizedIdentity.in(standardNames)
        );
        return criteriaBuilder.and(
                criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.GENERIC_INGREDIENT),
                criteriaBuilder.or(effectiveName.in(standardNames), criteriaBuilder.exists(localizationSubquery))
        );
    }

    private Predicate buildCoreFoodWordSearchMatch(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String searchQuery
    ) {
        Set<String> identityTerms = resolveSearchIdentityTerms(searchQuery);
        List<Predicate> matches = new ArrayList<>();
        for (String term : identityTerms) {
            matches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("name")), criteriaBuilder, term));
            matches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("displayName")), criteriaBuilder, term));
            matches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("shortDisplayName")), criteriaBuilder, term));
            matches.add(buildWholeWordMatch(criteriaBuilder.lower(root.get("brand")), criteriaBuilder, term));
        }

        var aliasSubquery = query.subquery(Long.class);
        var aliasRoot = aliasSubquery.from(FoodItemSearchAliasEntity.class);
        List<Predicate> aliasMatches = new ArrayList<>();
        for (String term : identityTerms) {
            aliasMatches.add(buildWholeWordMatch(criteriaBuilder.lower(aliasRoot.get("alias")), criteriaBuilder, term));
            aliasMatches.add(buildWholeWordMatch(criteriaBuilder.lower(aliasRoot.get("normalizedAlias")), criteriaBuilder, term));
        }
        aliasSubquery.select(aliasRoot.get("id"));
        aliasSubquery.where(
                criteriaBuilder.equal(aliasRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(aliasRoot.get("active")),
                criteriaBuilder.or(aliasMatches.toArray(new Predicate[0]))
        );
        matches.add(criteriaBuilder.exists(aliasSubquery));

        var localizationSubquery = query.subquery(Long.class);
        var localizationRoot = localizationSubquery.from(FoodItemLocalizationEntity.class);
        List<Predicate> localizationMatches = new ArrayList<>();
        for (String term : identityTerms) {
            localizationMatches.add(buildWholeWordMatch(
                    criteriaBuilder.lower(localizationRoot.get("displayName")), criteriaBuilder, term));
            localizationMatches.add(buildWholeWordMatch(
                    criteriaBuilder.lower(localizationRoot.get("shortDisplayName")), criteriaBuilder, term));
        }
        localizationSubquery.select(localizationRoot.get("id"));
        localizationSubquery.where(
                criteriaBuilder.equal(localizationRoot.get("foodItem"), root),
                criteriaBuilder.isTrue(localizationRoot.get("active")),
                criteriaBuilder.or(localizationMatches.toArray(new Predicate[0]))
        );
        matches.add(criteriaBuilder.exists(localizationSubquery));
        return criteriaBuilder.or(matches.toArray(new Predicate[0]));
    }

    private Set<String> resolveSearchIdentityTerms(String searchQuery) {
        Set<String> identityTerms = new java.util.LinkedHashSet<>();
        for (String term : FoodProductNormalizationRules.expandSearchTerms(searchQuery)) {
            String normalizedText = FoodProductNormalizationRules.normalizeText(term);
            if (normalizedText != null) {
                identityTerms.add(normalizedText.toLowerCase(Locale.ROOT));
            }
            String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(term);
            if (normalizedAlias != null) {
                identityTerms.add(normalizedAlias.toLowerCase(Locale.ROOT));
            }
        }
        String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(searchQuery);
        if (normalizedAlias != null) {
            identityTerms.add(normalizedAlias.toLowerCase(Locale.ROOT));
        }
        return identityTerms;
    }
    private Predicate buildWholeWordMatch(
            jakarta.persistence.criteria.Expression<String> expression,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String term
    ) {
        Predicate singularMatch = buildExactWordMatch(expression, criteriaBuilder, term);
        if (!term.matches("[a-z]{3,}")) {
            return singularMatch;
        }
        return criteriaBuilder.or(
                singularMatch,
                buildExactWordMatch(expression, criteriaBuilder, term + "s")
        );
    }

    private Predicate buildExactWordMatch(
            jakarta.persistence.criteria.Expression<String> expression,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String term
    ) {
        String literal = FoodSearchLikePatterns.literal(term);
        return criteriaBuilder.or(
                criteriaBuilder.equal(expression, term),
                criteriaBuilder.like(expression, literal + " %", FoodSearchLikePatterns.ESCAPE),
                criteriaBuilder.like(expression, literal + ",%", FoodSearchLikePatterns.ESCAPE),
                criteriaBuilder.like(expression, "% " + literal + " %", FoodSearchLikePatterns.ESCAPE),
                criteriaBuilder.like(expression, "% " + literal + ",%", FoodSearchLikePatterns.ESCAPE),
                criteriaBuilder.like(expression, "% " + literal, FoodSearchLikePatterns.ESCAPE)
        );
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
                criteriaBuilder.like(lowerName, "% chocolate%"),
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
        if (isPlainRiceQuery(searchQuery)) {
            derivedName = criteriaBuilder.or(derivedName,
                    criteriaBuilder.like(lowerName, "%cream of rice%"),
                    criteriaBuilder.like(lowerName, "%rice cream%"),
                    criteriaBuilder.like(lowerName, "%rice pudding%"),
                    criteriaBuilder.like(lowerName, "%pirinç krem%"),
                    criteriaBuilder.like(lowerName, "%pirinc krem%"),
                    criteriaBuilder.like(lowerName, "%pirinç puding%"),
                    criteriaBuilder.like(lowerName, "%pirinc puding%"));
        }
        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(derivedName, 1)
                .otherwise(0));
    }

    private boolean isPlainRiceQuery(String searchQuery) {
        String normalized = FoodProductNormalizationRules.normalizeSearchAlias(searchQuery);
        return "rice".equals(normalized) || "pirinc".equals(normalized);
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

    private jakarta.persistence.criteria.Order buildCoreFoodCatalogTypeRank(
            Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.asc(criteriaBuilder.selectCase()
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.GENERIC_INGREDIENT), 0)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.LOCAL_DISH), 1)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.BRANDED_PRODUCT), 2)
                .when(criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.USER_CUSTOM), 3)
                .otherwise(4));
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

        return (switch (sortBy == null ? "" : sortBy) {
            case "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "nutriScore" ->
                    Sort.by(direction, sortBy);
            case "qualityScore", "usageCount" -> Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "name"));
            default -> buildDefaultSearchSort();
        }).and(Sort.by(Sort.Direction.ASC, "id"));
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
        if (!canPublishExternalProduct(externalProduct, entity)) {
            throw new ProductNotFoundException("Product has incomplete or invalid source nutrition: " + barcode);
        }
        acquireExternalImportFence();
        var existing = findByNormalizedBarcode(barcode);
        if (existing.isPresent()) {
            FoodItemEntity product = existing.get();
            if (isRejected(product) || !isPublished(product) || !FoodProductQualityRules.hasValidCoreNutrition(product)) {
                throw new ProductNotFoundException("Product is not available for barcode: " + barcode);
            }
            return product;
        }
        FoodItemEntity saved = catalogPublicationService.publishNew(
                entity,
                "system:open-food-facts",
                "Trusted barcode lookup import",
                null
        );
        foodProductQualityIssueTracker.syncReviewIssues(saved, "open-food-facts");
        recordOpenFoodFactsEvidence(saved);
        return saved;
    }

    private FoodProductSearchPageDto searchAndCacheExternalProducts(FoodSearchCriteriaDto criteria, Pageable pageable) {
        String searchQuery = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
        if (searchQuery == null || pageable.getPageNumber() > 0) {
            return emptySearchPage(pageable);
        }

        var external = openFoodFactsService.searchProductsOutcome(criteria);
        if (external == null || !external.available()) {
            var unavailable = emptySearchPage(pageable);
            unavailable.setCacheable(false);
            return unavailable;
        }
        if (!external.products().isEmpty()) acquireExternalImportFence();
        List<FoodItemEntity> cachedProducts = external.products()
                .stream()
                .map(this::cacheExternalSearchProduct)
                .filter(Objects::nonNull)
                .toList();

        if (cachedProducts.isEmpty()) {
            return emptySearchPage(pageable);
        }

        // JDBC candidate lookup must see new rows and quality decisions before rebuilding the search.
        foodItemRepository.flush();
        return toSearchPageDto(searchLocalProducts(criteria, pageable), criteria.getPreferredLanguage());
    }

    private void acquireExternalImportFence() {
        // Manually constructed unit fixtures have no Spring transaction/fence; managed services require it.
        if (externalFoodImportFence != null) externalFoodImportFence.acquire();
    }

    private FoodItemEntity cacheExternalSearchProduct(FoodProductDto externalProduct) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(externalProduct.getBarcode());
        if (normalizedBarcode == null) {
            return null;
        }

        java.util.Optional<FoodItemEntity> localProduct = findByNormalizedBarcode(normalizedBarcode);
        if (localProduct.isPresent()) {
            FoodItemEntity product = localProduct.get();
            return isRejected(product) || !isPublished(product) || !FoodProductQualityRules.hasValidCoreNutrition(product)
                    ? null : product;
        }
        FoodItemEntity entity = buildImportedFoodItem(externalProduct, normalizedBarcode);
        if (!canPublishExternalProduct(externalProduct, entity)) {
            return null;
        }
        FoodItemEntity saved = catalogPublicationService.publishNew(
                entity,
                "system:open-food-facts",
                "Trusted search cache import",
                null
        );
        foodProductQualityIssueTracker.syncReviewIssues(saved, "open-food-facts");
        recordOpenFoodFactsEvidence(saved);
        return saved;
    }

    private boolean canPublishExternalProduct(FoodProductDto source, FoodItemEntity product) {
        return source.getNutritionReferenceUnit() != null
                && product.getName() != null && !product.getName().isBlank()
                && !FoodProductQualityRules.hasCriticalIssue(product);
    }

    private void recordOpenFoodFactsEvidence(FoodItemEntity product) {
        foodProductEvidenceService.recordImportEvidence(
                List.of(product),
                product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML
                        ? FoodEvidenceBasis.PER_100_ML
                        : FoodEvidenceBasis.PER_100_G,
                LocalDateTime.now(),
                "OPEN_FOOD_FACTS_API",
                "system:open-food-facts"
        );
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
        entity.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
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
        if (product.getPublicationStatus() == null) {
            return !Boolean.TRUE.equals(product.getIsCustom())
                    || isOwnedBy(product, email);
        }
        if (product.getPublicationStatus() == CatalogPublicationStatus.PUBLISHED) {
            return true;
        }
        if (product.getPublicationStatus() != CatalogPublicationStatus.PRIVATE_USER) {
            return false;
        }
        return isOwnedBy(product, email);
    }

    private boolean isPublished(FoodItemEntity product) {
        return product.getPublicationStatus() == null
                || product.getPublicationStatus() == CatalogPublicationStatus.PUBLISHED;
    }

    private boolean isOwnedBy(FoodItemEntity product, String email) {
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

    private boolean hasExplicitSort(FoodSearchCriteriaDto criteria) {
        return FoodProductNormalizationRules.normalizeText(criteria.getSortBy()) != null;
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
        List<FoodItemEntity> content = products.getContent();
        List<Long> productIds = content.stream().map(FoodItemEntity::getId).toList();
        Map<Long, List<FoodItemServingOptionEntity>> servingOptionsByProductId = loadServingOptions(productIds);
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> localizationsByProductId =
                loadLocalizations(productIds, language);
        Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> servingLocalizationsByOptionId =
                loadServingOptionLocalizations(servingOptionsByProductId, language);
        dto.setContent(content.stream()
                .map(product -> toProductDto(
                        product,
                        language,
                        servingOptionsByProductId.getOrDefault(product.getId(), List.of()),
                        resolveLocalization(localizationsByProductId.get(product.getId()), language),
                        servingLocalizationsByOptionId
                ))
                .toList());
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

    private Map<Long, List<FoodItemServingOptionEntity>> loadServingOptions(List<Long> productIds) {
        Map<Long, List<FoodItemServingOptionEntity>> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        foodItemServingOptionRepository
                .findByFoodItemIdInAndQualityStatusOrderByFoodItemIdAscIsDefaultDescLabelAsc(
                        productIds,
                        com.grun.calorietracker.enums.FoodServingOptionQualityStatus.VERIFIED
                )
                .stream()
                .filter(option -> !Boolean.FALSE.equals(option.getUserVisible()))
                .forEach(option -> result
                        .computeIfAbsent(option.getFoodItem().getId(), ignored -> new ArrayList<>())
                        .add(option));
        return result;
    }

    private Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> loadServingOptionLocalizations(
            Map<Long, List<FoodItemServingOptionEntity>> servingOptionsByProductId,
            PreferredLanguage language
    ) {
        List<Long> servingOptionIds = servingOptionsByProductId.values().stream()
                .flatMap(List::stream)
                .map(FoodItemServingOptionEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        return loadServingOptionLocalizations(servingOptionIds, language);
    }

    private Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> loadServingOptionLocalizations(
            List<Long> servingOptionIds,
            PreferredLanguage language
    ) {
        Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> result = new HashMap<>();
        if (servingOptionIds.isEmpty()) {
            return result;
        }
        PreferredLanguage resolvedLanguage = resolveLanguage(language);
        Set<PreferredLanguage> languages = resolvedLanguage == PreferredLanguage.EN
                ? Set.of(PreferredLanguage.EN)
                : Set.of(resolvedLanguage, PreferredLanguage.EN);
        foodItemServingOptionLocalizationRepository
                .findByServingOptionIdInAndLanguageInAndActiveTrue(servingOptionIds, languages)
                .forEach(localization -> result
                        .computeIfAbsent(localization.getServingOption().getId(), ignored -> new HashMap<>())
                        .put(localization.getLanguage(), localization));
        return result;
    }
    private Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> loadLocalizations(
            List<Long> productIds,
            PreferredLanguage language
    ) {
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        PreferredLanguage resolvedLanguage = resolveLanguage(language);
        Set<PreferredLanguage> languages = resolvedLanguage == PreferredLanguage.EN
                ? Set.of(PreferredLanguage.EN)
                : Set.of(resolvedLanguage, PreferredLanguage.EN);
        foodItemLocalizationRepository
                .findByFoodItemIdInAndLanguageInAndActiveTrue(productIds, languages)
                .forEach(localization -> result
                        .computeIfAbsent(localization.getFoodItem().getId(), ignored -> new HashMap<>())
                        .put(localization.getLanguage(), localization));
        return result;
    }

    private FoodItemLocalizationEntity resolveLocalization(
            Map<PreferredLanguage, FoodItemLocalizationEntity> localizations,
            PreferredLanguage language
    ) {
        if (localizations == null || localizations.isEmpty()) {
            return null;
        }
        PreferredLanguage resolvedLanguage = resolveLanguage(language);
        FoodItemLocalizationEntity localization = localizations.get(resolvedLanguage);
        return localization != null ? localization : localizations.get(PreferredLanguage.EN);
    }

    private FoodItemServingOptionLocalizationEntity resolveServingLocalization(
            Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity> localizations,
            PreferredLanguage language
    ) {
        if (localizations == null || localizations.isEmpty()) {
            return null;
        }
        PreferredLanguage resolvedLanguage = resolveLanguage(language);
        FoodItemServingOptionLocalizationEntity localization = localizations.get(resolvedLanguage);
        return localization != null ? localization : localizations.get(PreferredLanguage.EN);
    }
    private PreferredLanguage resolveLanguage(PreferredLanguage language) {
        return language == null ? PreferredLanguage.EN : language;
    }

    private void applyLocalization(
            FoodProductDto dto,
            FoodItemEntity product,
            PreferredLanguage language,
            FoodItemLocalizationEntity localization
    ) {
        dto.setLanguage(resolveLanguage(language));
        if (localization == null) {
            return;
        }
        String localizedDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getDisplayName(), localization.getLanguage());
        String localizedShortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getShortDisplayName(), localization.getLanguage());
        if (localizedDisplayName != null) {
            dto.setDisplayName(localizedDisplayName);
        }
        if (localizedShortDisplayName != null) {
            dto.setShortDisplayName(localizedShortDisplayName);
        } else if (localizedDisplayName != null) {
            dto.setShortDisplayName(localizedDisplayName);
        }
        boolean hasExplicitPreparation = product.getPreparationState() != null
                && product.getPreparationState() != FoodPreparationState.UNSPECIFIED;
        String preferredProductName = hasExplicitPreparation
                ? dto.getDisplayName()
                : dto.getShortDisplayName();
        dto.setProductName(preferredProductName != null ? preferredProductName : dto.getDisplayName());
    }

    private FoodProductDto toProductDto(FoodItemEntity product, PreferredLanguage language) {
        List<FoodItemServingOptionEntity> servingOptions =
                foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                        product,
                        com.grun.calorietracker.enums.FoodServingOptionQualityStatus.VERIFIED
                ).stream().filter(option -> !Boolean.FALSE.equals(option.getUserVisible())).toList();
        PreferredLanguage resolvedLanguage = resolveLanguage(language);
        FoodItemLocalizationEntity localization = foodItemLocalizationRepository
                .findByFoodItemIdAndLanguageAndActiveTrue(product.getId(), resolvedLanguage)
                .or(() -> resolvedLanguage == PreferredLanguage.EN
                        ? java.util.Optional.empty()
                        : foodItemLocalizationRepository.findByFoodItemIdAndLanguageAndActiveTrue(
                                product.getId(),
                                PreferredLanguage.EN
                        ))
                .orElse(null);
Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> servingLocalizations =
                loadServingOptionLocalizations(
                        servingOptions.stream().map(FoodItemServingOptionEntity::getId).filter(Objects::nonNull).toList(),
                        language
                );
        return toProductDto(product, language, servingOptions, localization, servingLocalizations);
    }

    private FoodProductDto toProductDto(
            FoodItemEntity product,
            PreferredLanguage language,
            List<FoodItemServingOptionEntity> servingOptions,
            FoodItemLocalizationEntity localization,
            Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> servingLocalizations
    ) {
        FoodProductDto dto = FoodItemMapper.mapEntityToDto(product);
        applyLocalization(dto, product, language, localization);
        var compatibleOptions = servingOptions.stream()
                .filter(option -> !Boolean.FALSE.equals(option.getUserVisible()))
                .filter(option -> com.grun.calorietracker.service.support.FoodPortionUnitResolver.supportsOption(product, option))
                .toList();
dto.setServingOptions(compatibleOptions.stream().map(option -> {
            var servingDto = FoodServingOptionMapper.toDto(option);
            FoodItemServingOptionLocalizationEntity servingLocalization = resolveServingLocalization(
                    servingLocalizations.get(option.getId()),
                    language
            );
            if (servingLocalization != null) {
                servingDto.setLabel(servingLocalization.getLabel());
            }
            return servingDto;
        }).toList());
        compatibleOptions.stream()
                .filter(option -> Boolean.TRUE.equals(option.getIsDefault()))
                .findFirst()
                .or(() -> compatibleOptions.stream().findFirst())
                .ifPresent(option -> dto.setDefaultServingOptionId(option.getId()));
        return dto;
    }
}


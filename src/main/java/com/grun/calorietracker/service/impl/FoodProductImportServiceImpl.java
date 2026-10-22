package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules;
import com.grun.calorietracker.service.support.FoodSourceNameRules;
import com.grun.calorietracker.service.support.FoodCatalogNamingRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductImportErrorDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodProductImportWarningDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.service.FoodProductImportService;
import com.grun.calorietracker.service.FoodProductEvidenceService;
import com.grun.calorietracker.service.support.BatchQuerySupport;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodLiquidUnitClassifier;
import com.grun.calorietracker.service.support.FoodCategoryImportResolutionService;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;
import com.grun.calorietracker.service.support.FoodNutritionUnitContract;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Transactional
public class FoodProductImportServiceImpl implements FoodProductImportService {

    private static final int MAX_ERROR_DETAILS = 50;
    private static final int MAX_WARNING_DETAILS = 50;
    private static final Set<String> ALIAS_COLUMNS = Set.of(
            "alias_tr", "aliases_tr", "search_alias_tr", "search_aliases_tr", "turkish_alias", "turkish_aliases",
            "alias_en", "aliases_en", "search_alias_en", "search_aliases_en", "english_alias", "english_aliases",
            "alias", "aliases", "search_alias", "search_aliases");
    private static final Set<String> NUMERIC_COLUMNS = Set.of(
            "calories", "energy_kcal_100g", "energy_kcal", "kcal_100g",
            "protein", "proteins_100g", "protein_100g", "fat", "fat_100g",
            "carbs", "carbohydrates_100g", "carbohydrate_100g", "fiber", "fiber_100g",
            "sugar", "sugars_100g", "sodium", "sodium_100g", "potassium", "potassium_100g",
            "cholesterol", "cholesterol_100g", "calcium", "calcium_100g", "iron", "iron_100g",
            "magnesium", "magnesium_100g", "zinc", "zinc_100g",
            "vitamin_a", "vitamina", "vitamin_a_100g", "vitamina_100g",
            "vitamin_c", "vitaminc", "vitamin_c_100g", "vitaminc_100g",
            "vitamin_d", "vitamind", "vitamin_d_100g", "vitamind_100g",
            "vitamin_e", "vitamine", "vitamin_e_100g", "vitamine_100g",
            "vitamin_b12", "vitaminb12", "vitamin_b12_100g", "vitaminb12_100g",
            "saturated_fat", "saturatedfat", "saturated_fat_100g", "saturatedfat_100g",
            "trans_fat", "transfat", "trans_fat_100g", "transfat_100g",
            "sugar_alcohol", "sugaralcohol", "sugar_alcohol_100g", "sugaralcohol_100g",
            "servingsizegrams", "serving_size_grams", "serving_quantity");

    private final FoodItemRepository foodItemRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;
    private final FoodProductEvidenceService foodProductEvidenceService;
    private final ObjectMapper objectMapper;
    private FoodCategoryImportResolutionService foodCategoryImportResolutionService;
    private com.grun.calorietracker.service.support.ExternalFoodImportFence externalFoodImportFence;

    @Autowired
    void setExternalFoodImportFence(com.grun.calorietracker.service.support.ExternalFoodImportFence fence) {
        this.externalFoodImportFence = fence;
    }

    @Autowired
    void setFoodCategoryImportResolutionService(
            FoodCategoryImportResolutionService foodCategoryImportResolutionService) {
        this.foodCategoryImportResolutionService = foodCategoryImportResolutionService;
    }

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy) {
        return importCsv(file, importedBy, FoodProductImportMode.CURATED_ADMIN, FoodProductImportFormat.AUTO);
    }

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode) {
        return importCsv(file, importedBy, importMode, FoodProductImportFormat.AUTO);
    }

    @Override
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode, FoodProductImportFormat importFormat) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }

        ParsedCsv parsedCsv = parseCsv(file, normalizeImportFormat(importFormat));
        if (!parsedCsv.rows().isEmpty() && externalFoodImportFence != null) externalFoodImportFence.acquire();
        Map<String, FoodItemEntity> existingProducts = loadExistingProducts(
                parsedCsv.normalizedBarcodes(),
                parsedCsv.sourceKeys()
        );

        List<FoodItemEntity> productsToSave = new ArrayList<>();
        List<ProductImportContext> productImportContexts = new ArrayList<>();
        List<FoodProductImportErrorDto> errors = new ArrayList<>();
        List<FoodProductImportWarningDto> warnings = new ArrayList<>();
        Set<String> seenInputKeys = new HashSet<>();
        int insertedRows = 0;
        int updatedRows = 0;
        int duplicateInputRows = 0;
        int missingMarketRegionRows = 0;
        int unsupportedMarketRegionRows = 0;
        Map<String, Integer> marketRegionCounts = new LinkedHashMap<>();
        Map<String, Integer> catalogTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> dataSourceCounts = new LinkedHashMap<>();
        Map<String, Integer> qualityWarningCounts = new LinkedHashMap<>();

        for (CsvRow row : parsedCsv.rows()) {
            RegionResolution regionResolution = resolveMarketRegion(row, null);
            String inputSourceKey;
            try {
                inputSourceKey = resolveInputKey(row, regionResolution.region());
            } catch (IllegalArgumentException exception) {
                addError(errors, new FoodProductImportErrorDto(row.rowNumber(), null, exception.getMessage()));
                continue;
            }
            if (inputSourceKey != null && seenInputKeys.contains(inputSourceKey)) {
                duplicateInputRows++;
                addWarning(
                        qualityWarningCounts,
                        warnings,
                        row,
                        inputSourceKey,
                        "DUPLICATE_INPUT_KEY",
                        "Input file contains a duplicate barcode or source key. The later row was skipped and the first valid row was retained."
                );
                continue;
            }
            FoodItemEntity existingProduct = existingProducts.get(inputSourceKey);
            boolean evidenceBasisKnown = firstText(row, "nutrition_reference_unit", "nutritionreferenceunit", "nutrition_unit", "nutritionunit") != null;
            if (existingProduct != null) {
                regionResolution = resolveMarketRegion(row, existingProduct.getMarketRegion());
            }
            RowResult rowResult = mapRow(row, existingProducts, importedBy, normalizeImportMode(importMode), parsedCsv.sourceFormat(), regionResolution);
            if (rowResult.error() != null) {
                addError(errors, rowResult.error());
                continue;
            }

            if (inputSourceKey != null) {
                seenInputKeys.add(inputSourceKey);
            }

            if (regionResolution.missing()) {
                missingMarketRegionRows++;
            }
            if (regionResolution.unsupported()) {
                unsupportedMarketRegionRows++;
            }
            productsToSave.add(rowResult.product());
            productImportContexts.add(new ProductImportContext(rowResult.product(), regionResolution, row, evidenceBasisKnown));
            String regionKey = rowResult.product().getMarketRegion() == null ? "UNSPECIFIED" : rowResult.product().getMarketRegion().name();
            marketRegionCounts.merge(regionKey, 1, Integer::sum);
            String catalogTypeKey = rowResult.product().getCatalogType() == null ? "UNSPECIFIED" : rowResult.product().getCatalogType().name();
            catalogTypeCounts.merge(catalogTypeKey, 1, Integer::sum);
            String dataSourceKey = rowResult.product().getDataSource() == null ? "UNSPECIFIED" : rowResult.product().getDataSource().name();
            dataSourceCounts.merge(dataSourceKey, 1, Integer::sum);
            addQualityWarnings(qualityWarningCounts, warnings, row, rowResult.product(), regionResolution);
            registerExistingProduct(existingProducts, rowResult.product());
            if (rowResult.inserted()) {
                insertedRows++;
            } else {
                updatedRows++;
            }
        }

        addPotentialCanonicalDuplicateWarnings(
                qualityWarningCounts,
                warnings,
                productImportContexts
        );

        List<FoodItemEntity> savedProducts = new ArrayList<>();
        foodItemRepository.saveAll(productsToSave).forEach(savedProducts::add);
        List<FoodItemEntity> evidenceProducts = new ArrayList<>();
        for (int index = 0; index < productImportContexts.size(); index++) {
            if (productImportContexts.get(index).evidenceBasisKnown()) {
                evidenceProducts.add(index < savedProducts.size() ? savedProducts.get(index) : productImportContexts.get(index).product());
            }
        }
        LocalDateTime evidenceObservedAt = LocalDateTime.now();
        for (FoodNutritionReferenceUnit unit : FoodNutritionReferenceUnit.values()) {
            List<FoodItemEntity> sameBasis = evidenceProducts.stream()
                    .filter(product -> product.getNutritionReferenceUnit() == unit).toList();
            if (!sameBasis.isEmpty()) {
                foodProductEvidenceService.recordImportEvidence(sameBasis,
                        switch (unit) {
                            case PER_100ML -> FoodEvidenceBasis.PER_100_ML;
                            case PER_SERVING -> FoodEvidenceBasis.SERVING;
                            case PER_100G -> FoodEvidenceBasis.PER_100_G;
                        },
                        evidenceObservedAt, parsedCsv.sourceFormat().name(), importedBy);
            }
        }
        syncQualityIssues(savedProducts, productImportContexts, importedBy);
        if (normalizeImportMode(importMode) != FoodProductImportMode.SOURCE_REFRESH) {
            syncSearchAliases(savedProducts, productImportContexts);
            syncLocalizations(savedProducts, productImportContexts);
        }
        syncServingOptions(savedProducts, productImportContexts, normalizeImportMode(importMode));
        if (foodCategoryImportResolutionService != null
                && normalizeImportMode(importMode) != FoodProductImportMode.SOURCE_REFRESH) {
            foodCategoryImportResolutionService.resolveAfterImport(savedProducts, importedBy);
        }
        int skippedRows = parsedCsv.rows().size() - productsToSave.size();
        int reviewRequiredRows = (int) productsToSave.stream()
                .filter(this::requiresReview)
                .count();
        int importQualityScore = calculateImportQualityScore(parsedCsv.rows().size(), skippedRows, qualityWarningCounts);

        return new FoodProductImportResultDto(
                parsedCsv.rows().size(),
                insertedRows,
                updatedRows,
                skippedRows,
                productsToSave.size(),
                duplicateInputRows,
                reviewRequiredRows,
                missingMarketRegionRows,
                unsupportedMarketRegionRows,
                marketRegionCounts,
                catalogTypeCounts,
                dataSourceCounts,
                qualityWarningCounts,
                importQualityScore,
                parsedCsv.format(),
                parsedCsv.sourceFormat().name(),
                errors,
                warnings
        );
    }

    @Override
    public FoodProductImportResultDto previewCsv(
            MultipartFile file,
            String importedBy,
            FoodProductImportMode importMode,
            FoodProductImportFormat importFormat
    ) {
        FoodProductImportResultDto result = importCsv(file, importedBy, importMode, importFormat);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return result;
    }

    private ParsedCsv parseCsv(MultipartFile file, FoodProductImportFormat requestedFormat) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV header row is required.");
            }

            char delimiter = detectDelimiter(headerLine);
            Map<String, Integer> headers = indexHeaders(parseDelimitedLine(headerLine, delimiter));
            FoodProductImportFormat sourceFormat = detectSourceFormat(headers, requestedFormat);
            requireAnyHeader(headers, "name", "productname", "product_name", "description", "food_description");

            List<CsvRow> rows = new ArrayList<>();
            List<String> normalizedBarcodes = new ArrayList<>();
            List<String> sourceKeys = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                CsvRow row = new CsvRow(rowNumber, headers, parseDelimitedLine(line, delimiter));
                rows.add(row);
                String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(firstText(row, "barcode", "code", "gtin", "ean", "upc"));
                if (normalizedBarcode != null) {
                    normalizedBarcodes.add(normalizedBarcode);
                }
                String sourceKey;
                try {
                    sourceKey = resolveInputKey(row, resolveMarketRegion(row, null).region());
                } catch (IllegalArgumentException exception) {
                    // Invalid classification is reported per row, not as a whole-file failure.
                    sourceKey = null;
                }
                if (sourceKey != null) {
                    sourceKeys.add(sourceKey);
                }
            }

            return new ParsedCsv(
                    rows,
                    normalizedBarcodes.stream().distinct().toList(),
                    sourceKeys.stream().distinct().toList(),
                    delimiter == '\t' ? "TSV" : "CSV",
                    sourceFormat
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV file could not be read.");
        }
    }

    private Map<String, FoodItemEntity> loadExistingProducts(List<String> normalizedBarcodes, List<String> sourceKeys) {
        if (normalizedBarcodes.isEmpty() && sourceKeys.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, FoodItemEntity> byKey = new LinkedHashMap<>();
        BatchQuerySupport.loadInChunks(
                normalizedBarcodes,
                chunk -> foodItemRepository.findByNormalizedBarcodeIn(chunk, Sort.by(Sort.Order.asc("id")))
        ).forEach(product -> registerExistingProduct(byKey, product));
        BatchQuerySupport.loadInChunks(
                sourceKeys,
                chunk -> foodItemRepository.findBySourceKeyIn(chunk, Sort.by(Sort.Order.asc("id")))
        ).forEach(product -> registerExistingProduct(byKey, product));
        return byKey;
    }

    private RowResult mapRow(
            CsvRow row,
            Map<String, FoodItemEntity> existingProducts,
            String importedBy,
            FoodProductImportMode importMode,
            FoodProductImportFormat sourceFormat,
            RegionResolution regionResolution
    ) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(firstText(row, "barcode", "code", "gtin", "ean", "upc"));
        String rawName = firstText(row, "name", "productname", "product_name", "description", "food_description", "lowercase_description");
        String name = FoodProductNormalizationRules.normalizeProductDisplayName(rawName, displayLanguage(row));
        if (name == null) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), firstText(row, "barcode", "code", "fdc_id"), "Product name is required."));
        }

        FoodCatalogType catalogType;
        try {
            catalogType = resolveCatalogType(row, normalizedBarcode);
        } catch (IllegalArgumentException exception) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), firstText(row, "source_key", "fdc_id"), exception.getMessage()));
        }
        FoodPreparationState preparationState = resolvePreparationState(row);
        String sourceKey = resolveSourceKey(row, normalizedBarcode, name, catalogType, regionResolution.region(), preparationState);
        if (sourceKey == null) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(),
                    firstText(row, "barcode", "code", "fdc_id"),
                    "Barcode is required for branded products. Non-barcode rows must use GENERIC_INGREDIENT or LOCAL_DISH catalog_type."
            ));
        }

        FoodItemEntity product = existingProducts.get(sourceKey);
        boolean inserted = product == null;
        boolean missingReferenceUnit = firstText(row, "nutrition_reference_unit", "nutritionreferenceunit",
                "nutrition_unit", "nutritionunit", "nutrition_data_per") == null;
        boolean knownReferenceUnit = !missingReferenceUnit;
        FoodNutritionReferenceUnit referenceUnit;
        String conceptKey;
        String variantKey;
        String sourceDataType;
        FoodSourceNameRules.Decision reviewedNaming;
        try {
            // Validate before touching a managed entity: skipped rows must not flush partial edits.
            String approvalStatus = firstText(row, "approval_status", "approvalstatus");
            if (approvalStatus != null && !"APPROVED".equalsIgnoreCase(approvalStatus)) {
                throw new IllegalArgumentException("Review-only source artifacts cannot be imported. Resolve evidence/identity decisions and create a separately approved release manifest.");
            }
            for (String column : ALIAS_COLUMNS) {
                String input = firstText(row, column);
                if (input != null) parseAliasEntries(input);
            }
            if (isUsdaRow(row)) {
                com.grun.calorietracker.service.support.UsdaCatalogClassificationRules.validateSourceIdentity(
                        firstText(row, "fdc_id", "fdcid", "fdc"), firstText(row, "sourcekey", "source_key", "externalid", "external_id"));
            }
            for (String column : row.headers().keySet()) {
                if (NUMERIC_COLUMNS.contains(column)) {
                    Double value = parseDouble(null, row, column);
                    if (value != null && (column.startsWith("serving") && value <= 0.0)) {
                        throw new IllegalArgumentException("Positive serving quantity required for field: " + column);
                    }
                }
            }
            parseServingSizeGrams(row, null);
            referenceUnit = resolveNutritionReferenceUnit(row);
            if (missingReferenceUnit
                    && product != null && product.getNutritionReferenceUnit() != null) {
                referenceUnit = product.getNutritionReferenceUnit();
            }
            if (referenceUnit == FoodNutritionReferenceUnit.PER_SERVING) {
                String servingOptionsJson = firstText(
                        row, "serving_options_json", "servingoptionsjson", "portion_options_json");
                ServingOptionsParseResult servingOptions = parseServingOptions(row, referenceUnit);
                if (servingOptionsJson == null || !servingOptions.valid() || servingOptions.options().isEmpty()) {
                    throw new IllegalArgumentException(
                            "PER_SERVING nutrition requires exactly one default native serving option."
                    );
                }
            }
            sourceDataType = isUsdaRow(row) ? com.grun.calorietracker.service.support.UsdaCatalogClassificationRules.normalizeDataType(
                    firstText(row, "source_data_type", "data_type", "datatype")) : null;
            if (sourceDataType == null && product != null) {
                sourceDataType = product.getSourceDataType();
            }
            reviewedNaming = FoodSourceNameRules.match(sourceKey, rawName, sourceDataType).orElse(null);
            if (reviewedNaming == null && FoodSourceNameRules.reviewedSource(sourceKey)
                    .filter(decision -> decision.targetState() == FoodPreparationState.PREPARED).isPresent()) {
                throw new IllegalArgumentException("Reviewed prepared source requires its exact source description and subtype; audited evidence review required.");
            }
            if (reviewedNaming != null) {
                if (product != null && product.getFoodConceptKey() != null && product.getPreparationState() != reviewedNaming.targetState()) {
                    throw new IllegalArgumentException("Source naming correction changes reviewed preparation identity; audited compatibility review required.");
                }
                preparationState = reviewedNaming.targetState();
            }
            if (product != null && product.getFoodConceptKey() != null) {
                if (declaredCatalogType(row) == null) {
                    catalogType = product.getCatalogType();
                }
                if (firstText(row, "preparationstate", "preparation_state", "prepstate", "prep_state", "cookingstate", "cooking_state", "state") == null) {
                    preparationState = product.getPreparationState();
                }
            }
            if (isUsdaRow(row)) {
                catalogType = com.grun.calorietracker.service.support.UsdaCatalogClassificationRules.resolve(
                        sourceDataType, product != null && product.getFoodConceptKey() != null && declaredCatalogType(row) == null
                                ? product.getCatalogType() : declaredCatalogType(row), normalizedBarcode != null, sourceKey, rawName);
                var namedBrand = com.grun.calorietracker.service.support.UsdaNamedBrandRules.match(sourceKey, rawName, sourceDataType);
                if (namedBrand.isPresent()) {
                    String incomingBrand = firstText(row, "brand", "brands", "manufacturer", "producer");
                    if (incomingBrand != null && !namedBrand.get().canonicalBrand().equalsIgnoreCase(incomingBrand)) {
                        throw new IllegalArgumentException("Incoming brand conflicts with exact USDA named-manufacturer evidence.");
                    }
                    if (product != null && product.getCanonicalBrand() != null
                            && (!namedBrand.get().canonicalBrand().equalsIgnoreCase(product.getCanonicalBrand().getCanonicalName())
                                || product.getCanonicalBrand().getStatus() != com.grun.calorietracker.enums.FoodBrandStatus.ACTIVE)) {
                        throw new IllegalArgumentException("Existing canonical brand conflicts with named source; audited reassignment required.");
                    }
                    if (product != null && product.getCatalogType() != FoodCatalogType.BRANDED_PRODUCT
                            && (importMode != FoodProductImportMode.CURATED_ADMIN || !"APPROVED".equalsIgnoreCase(approvalStatus))) {
                        throw new IllegalArgumentException("Existing generic-to-brand identity needs explicit approved curated review.");
                    }
                }
            }
            conceptKey = FoodProductCanonicalKeyRules.validateIdentityToken(firstText(row, "food_concept_key", "foodconceptkey"));
            variantKey = FoodProductCanonicalKeyRules.validateIdentityToken(firstText(row, "composition_variant_key", "compositionvariantkey"));
            if ((conceptKey == null) != (variantKey == null)) {
                throw new IllegalArgumentException("Concept and composition variant must be supplied together.");
            }
            if (conceptKey != null && importMode != FoodProductImportMode.CURATED_ADMIN) {
                throw new IllegalArgumentException("Concept assignment requires a reviewed CURATED_ADMIN import.");
            }
            if (product != null && product.getFoodConceptKey() != null) {
                if ((conceptKey != null && (!conceptKey.equals(product.getFoodConceptKey())
                        || !variantKey.equals(product.getCompositionVariantKey())))
                        || catalogType != product.getCatalogType() || preparationState != product.getPreparationState()) {
                    throw new IllegalArgumentException("Existing reviewed identity cannot be reassigned by import; use an audited compatibility review.");
                }
                conceptKey = product.getFoodConceptKey();
                variantKey = product.getCompositionVariantKey();
            }
            if (product != null && product.getSourceDataType() != null && sourceDataType != null
                    && !product.getSourceDataType().equals(sourceDataType)) {
                throw new IllegalArgumentException("Existing source subtype conflicts with import evidence.");
            }
            // Validate the proposed identity before mutating a managed catalog record.
            if (conceptKey != null) {
                FoodItemEntity identity = new FoodItemEntity();
                identity.setFoodConceptKey(conceptKey);
                identity.setCompositionVariantKey(variantKey);
                identity.setCatalogType(catalogType);
                identity.setPreparationState(preparationState);
                FoodProductCanonicalKeyRules.resolve(identity);
            }
        } catch (IllegalArgumentException exception) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), sourceKey, exception.getMessage()));
        }
        if (importMode == FoodProductImportMode.SOURCE_REFRESH) {
            try {
                return applySourceRefresh(row, product, sourceKey, normalizedBarcode, referenceUnit, sourceFormat);
            } catch (IllegalArgumentException exception) {
                return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), sourceKey, exception.getMessage()));
            }
        }
        if (!inserted && importMode == FoodProductImportMode.RAW_EXTERNAL && isCuratedProduct(product)) {
            return new RowResult(product, false, null);
        }
        if (!inserted && product.getNutritionReferenceUnit() != null
                && product.getNutritionReferenceUnit() != referenceUnit) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), sourceKey,
                    "Nutrition reference unit cannot be relabelled by import. Use a source-verified conversion/review operation."));
        }
        if (inserted) {
            product = new FoodItemEntity();
            product.setUsageCount(0L);
            product.setIsCustom(false);
        }

        product.setBarcode(normalizedBarcode);
        product.setNormalizedBarcode(normalizedBarcode);
        product.setSourceKey(sourceKey);
        product.setName(name);
        ProductDisplayNames displayNames = resolveProductDisplayNames(name, catalogType, preparationState, row, sourceFormat, reviewedNaming);
        product.setDisplayName(displayNames.displayName());
        product.setShortDisplayName(displayNames.shortDisplayName());
        String dishFamilyKey = resolveLocalDishIdentityKey(row, catalogType, "dishfamilykey", "dish_family_key");
        String dishVariantKey = resolveLocalDishIdentityKey(row, catalogType, "dishvariantkey", "dish_variant_key");
        product.setDishFamilyKey(dishFamilyKey);
        product.setDishVariantKey(dishVariantKey);
        product.setFoodConceptKey(conceptKey);
        product.setCompositionVariantKey(variantKey);
        product.setSourceDataType(sourceDataType);
        String brand = firstText(row, "brand", "brands", "manufacturer", "producer");
        if (isUsdaRow(row)) {
            var reviewedBrand = com.grun.calorietracker.service.support.UsdaNamedBrandRules.match(sourceKey, rawName, sourceDataType);
            if (reviewedBrand.isPresent()) brand = reviewedBrand.get().canonicalBrand();
        }
        if (brand != null) {
            product.setBrand(FoodProductNormalizationRules.normalizeBrandDisplayName(brand));
        }
        product.setCatalogType(catalogType);
        product.setPreparationState(preparationState);
        product.setNutritionBasis(resolveNutritionBasis(row, catalogType));
        product.setNutritionReferenceUnit(referenceUnit);
        Set<String> sourceCategoryTags = resolveSourceCategoryTags(row);
        if (!sourceCategoryTags.isEmpty()) {
            product.setSourceCategoryTags(sourceCategoryTags);
        }
        mergeMarketAvailability(product, row, regionResolution.region());
        product.setCanonicalFoodKey(FoodProductCanonicalKeyRules.resolve(product));
        applyImportMetadata(product, row, importedBy, importMode, sourceFormat);

        setIfPresent(row, product::setImageUrl, "imageurl", "image_url", "image_front_url");
        setIfPresent(row, "externalimageurl", product::setExternalImageUrl);
        setIfPresent(row, "external_image_url", product::setExternalImageUrl);
        if (importMode == FoodProductImportMode.CURATED_ADMIN) {
            setIfPresent(row, "displayimageurl", product::setDisplayImageUrl);
            setIfPresent(row, "display_image_url", product::setDisplayImageUrl);
        }
        setIfPresent(row, product::setAllergens, "allergens", "allergens_tags");
        setIfPresent(row, product::setNutriScore, "nutriscore", "nutri_score", "nutrition_grade_fr", "nutrition_grade_uk");

        product.setCalories(NutritionValueNormalizer.calories(parseDouble(product.getCalories(), row, "calories", "energy_kcal_100g", "energy_kcal", "kcal_100g")));
        product.setProtein(NutritionValueNormalizer.macro(parseDouble(product.getProtein(), row, "protein", "proteins_100g", "protein_100g")));
        product.setFat(NutritionValueNormalizer.macro(parseDouble(product.getFat(), row, "fat", "fat_100g")));
        product.setCarbs(NutritionValueNormalizer.macro(parseDouble(product.getCarbs(), row, "carbs", "carbohydrates_100g", "carbohydrate_100g")));
        product.setFiber(NutritionValueNormalizer.macro(parseDouble(product.getFiber(), row, "fiber", "fiber_100g")));
        product.setSugar(NutritionValueNormalizer.macro(parseDouble(product.getSugar(), row, "sugar", "sugars_100g")));
        product.setSodium(NutritionValueNormalizer.sodium(parseDouble(product.getSodium(), row, "sodium", "sodium_100g")));
        product.setPotassium(NutritionValueNormalizer.micronutrient(parseDouble(product.getPotassium(), row, "potassium", "potassium_100g")));
        product.setCholesterol(NutritionValueNormalizer.micronutrient(parseDouble(product.getCholesterol(), row, "cholesterol", "cholesterol_100g")));
        product.setCalcium(NutritionValueNormalizer.micronutrient(parseDouble(product.getCalcium(), row, "calcium", "calcium_100g")));
        product.setIron(NutritionValueNormalizer.micronutrient(parseDouble(product.getIron(), row, "iron", "iron_100g")));
        product.setMagnesium(NutritionValueNormalizer.micronutrient(parseDouble(product.getMagnesium(), row, "magnesium", "magnesium_100g")));
        product.setZinc(NutritionValueNormalizer.micronutrient(parseDouble(product.getZinc(), row, "zinc", "zinc_100g")));
        product.setVitaminA(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminA(), row, "vitamin_a", "vitamina", "vitamin_a_100g", "vitamina_100g")));
        product.setVitaminC(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminC(), row, "vitamin_c", "vitaminc", "vitamin_c_100g", "vitaminc_100g")));
        product.setVitaminD(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminD(), row, "vitamin_d", "vitamind", "vitamin_d_100g", "vitamind_100g")));
        product.setVitaminE(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminE(), row, "vitamin_e", "vitamine", "vitamin_e_100g", "vitamine_100g")));
        product.setVitaminB12(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminB12(), row, "vitamin_b12", "vitaminb12", "vitamin_b12_100g", "vitaminb12_100g")));
        product.setSaturatedFat(NutritionValueNormalizer.macro(parseDouble(product.getSaturatedFat(), row, "saturated_fat", "saturatedfat", "saturated_fat_100g", "saturatedfat_100g")));
        product.setTransFat(NutritionValueNormalizer.macro(parseDouble(product.getTransFat(), row, "trans_fat", "transfat", "trans_fat_100g", "transfat_100g")));
        product.setSugarAlcohol(NutritionValueNormalizer.macro(parseDouble(product.getSugarAlcohol(), row, "sugar_alcohol", "sugaralcohol", "sugar_alcohol_100g", "sugaralcohol_100g")));
        product.setServingSizeGrams(parseServingSizeGrams(row, product.getServingSizeGrams()));
        String servingUnit = resolveServingUnit(row);
        if (servingUnit != null) {
            product.setServingUnit(servingUnit);
        } else if (FoodLiquidUnitClassifier.classify(product.getSourceCategoryTags(), referenceUnit)
                == FoodLiquidUnitClassifier.Decision.ML_SOURCE_SUPPORTED) {
            product.setServingUnit("MILLILITER");
        }

        updateQualityMetadata(product, importMode);
        if (FoodProductQualityRules.hasCriticalIssue(product) || !knownReferenceUnit
                || FoodCatalogNamingRules.requiresManualDisplayReview(product.getDisplayName())
                || FoodCatalogNamingRules.requiresManualDisplayReview(product.getShortDisplayName())
                || hasUnsafeLocalizedDisplay(row)
                || hasUnreviewedNamingOverride(row, reviewedNaming)
                || (isUsdaRow(row) && catalogType == FoodCatalogType.GENERIC_INGREDIENT
                    && FoodCatalogNamingRules.sourcePreparationConflict(rawName, preparationState))
                || (reviewedNaming != null && reviewedNaming.servingReviewRequired())
                || (isUsdaRow(row) && (sourceDataType == null
                    || "Experimental".equals(sourceDataType)
                    || (catalogType == FoodCatalogType.BRANDED_PRODUCT && normalizedBarcode == null)))) {
            if (importMode == FoodProductImportMode.CURATED_ADMIN) {
                product.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
            }
            product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
            FoodProductQualityRules.updateQualityAndReviewPriority(product);
            product.setAutoApprovedForCatalog(false);
        }
        return new RowResult(product, inserted, null);
    }

    private RowResult applySourceRefresh(
            CsvRow row,
            FoodItemEntity product,
            String sourceKey,
            String normalizedBarcode,
            FoodNutritionReferenceUnit referenceUnit,
            FoodProductImportFormat sourceFormat
    ) {
        if (product == null) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH can only update an existing source key."));
        }
        String explicitSourceKey = firstText(row, "sourcekey", "source_key");
        if (explicitSourceKey == null || !explicitSourceKey.equals(product.getSourceKey())) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH requires the exact existing source_key."));
        }
        if (sourceFormat != FoodProductImportFormat.OPEN_FOOD_FACTS_EXPORT) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH currently accepts only an Open Food Facts export."));
        }
        if (normalizedBarcode == null || !normalizedBarcode.equals(product.getNormalizedBarcode())) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH barcode must match the existing normalized barcode."));
        }
        if (!sourceKey.equals("barcode:" + normalizedBarcode)) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH requires a barcode-bound source key."));
        }
        if (firstText(row, "nutrition_data_per") == null) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(), sourceKey, "SOURCE_REFRESH requires OFF nutrition_data_per evidence."));
        }

        product.setNutritionReferenceUnit(referenceUnit);
        product.setCalories(NutritionValueNormalizer.calories(parseDouble(product.getCalories(), row, "calories", "energy_kcal_100g", "energy_kcal", "kcal_100g")));
        product.setProtein(NutritionValueNormalizer.macro(parseDouble(product.getProtein(), row, "protein", "proteins_100g", "protein_100g")));
        product.setFat(NutritionValueNormalizer.macro(parseDouble(product.getFat(), row, "fat", "fat_100g")));
        product.setCarbs(NutritionValueNormalizer.macro(parseDouble(product.getCarbs(), row, "carbs", "carbohydrates_100g", "carbohydrate_100g")));
        product.setFiber(NutritionValueNormalizer.macro(parseDouble(product.getFiber(), row, "fiber", "fiber_100g")));
        product.setSugar(NutritionValueNormalizer.macro(parseDouble(product.getSugar(), row, "sugar", "sugars_100g")));
        product.setSodium(NutritionValueNormalizer.sodium(parseDouble(product.getSodium(), row, "sodium", "sodium_100g")));
        product.setPotassium(NutritionValueNormalizer.micronutrient(parseDouble(product.getPotassium(), row, "potassium", "potassium_100g")));
        product.setCholesterol(NutritionValueNormalizer.micronutrient(parseDouble(product.getCholesterol(), row, "cholesterol", "cholesterol_100g")));
        product.setCalcium(NutritionValueNormalizer.micronutrient(parseDouble(product.getCalcium(), row, "calcium", "calcium_100g")));
        product.setIron(NutritionValueNormalizer.micronutrient(parseDouble(product.getIron(), row, "iron", "iron_100g")));
        product.setMagnesium(NutritionValueNormalizer.micronutrient(parseDouble(product.getMagnesium(), row, "magnesium", "magnesium_100g")));
        product.setZinc(NutritionValueNormalizer.micronutrient(parseDouble(product.getZinc(), row, "zinc", "zinc_100g")));
        product.setVitaminA(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminA(), row, "vitamin_a", "vitamina", "vitamin_a_100g", "vitamina_100g")));
        product.setVitaminC(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminC(), row, "vitamin_c", "vitaminc", "vitamin_c_100g", "vitaminc_100g")));
        product.setVitaminD(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminD(), row, "vitamin_d", "vitamind", "vitamin_d_100g", "vitamind_100g")));
        product.setVitaminE(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminE(), row, "vitamin_e", "vitamine", "vitamin_e_100g", "vitamine_100g")));
        product.setVitaminB12(NutritionValueNormalizer.micronutrient(parseDouble(product.getVitaminB12(), row, "vitamin_b12", "vitaminb12", "vitamin_b12_100g", "vitaminb12_100g")));
        product.setSaturatedFat(NutritionValueNormalizer.macro(parseDouble(product.getSaturatedFat(), row, "saturated_fat", "saturatedfat", "saturated_fat_100g", "saturatedfat_100g")));
        product.setTransFat(NutritionValueNormalizer.macro(parseDouble(product.getTransFat(), row, "trans_fat", "transfat", "trans_fat_100g", "transfat_100g")));
        product.setSugarAlcohol(NutritionValueNormalizer.macro(parseDouble(product.getSugarAlcohol(), row, "sugar_alcohol", "sugaralcohol", "sugar_alcohol_100g", "sugaralcohol_100g")));

        String servingUnit = resolveServingUnit(row);
        if (referenceUnit == FoodNutritionReferenceUnit.PER_100ML) {
            product.setServingUnit("MILLILITER");
            product.setServingSizeGrams(null);
        } else if (servingUnit != null) {
            product.setServingUnit(servingUnit);
            product.setServingSizeGrams(parseServingSizeGrams(row, product.getServingSizeGrams()));
        }
        FoodNutritionUnitContract.validate(product.getNutritionReferenceUnit(), product.getServingUnit());
        return new RowResult(product, false, null);
    }

    private ProductDisplayNames resolveProductDisplayNames(
            String sourceName,
            FoodCatalogType catalogType,
            FoodPreparationState preparationState,
            CsvRow row,
            FoodProductImportFormat sourceFormat,
            FoodSourceNameRules.Decision reviewedNaming
    ) {
        String explicitDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, "display_name", "displayname"), displayLanguage(row));
        String explicitShortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, "short_display_name", "shortdisplayname"), displayLanguage(row));
        if (explicitDisplayName != null || explicitShortDisplayName != null) {
            String displayName = explicitDisplayName != null ? explicitDisplayName : sourceName;
            String shortDisplayName = explicitShortDisplayName != null ? explicitShortDisplayName : displayName;
            return new ProductDisplayNames(displayName, shortDisplayName);
        }

        if (reviewedNaming != null) {
            return new ProductDisplayNames(reviewedNaming.displayNameEn(), reviewedNaming.displayNameEn());
        }

        boolean usdaGeneric = sourceFormat == FoodProductImportFormat.USDA_FOODDATA
                || firstText(row, "fdc_id", "fdcid", "fdc") != null;
        if (usdaGeneric && catalogType == FoodCatalogType.GENERIC_INGREDIENT) {
            return buildUsdaGenericDisplayNames(sourceName, preparationState);
        }

        String displayName = FoodProductNormalizationRules.normalizeProductDisplayName(sourceName, displayLanguage(row));
        return new ProductDisplayNames(displayName, displayName);
    }

    private PreferredLanguage displayLanguage(CsvRow row) {
        String language = firstText(row, "source_language", "name_language", "language", "lang");
        return "TR".equalsIgnoreCase(language) ? PreferredLanguage.TR : PreferredLanguage.EN;
    }

    private boolean hasUnsafeLocalizedDisplay(CsvRow row) {
        for (String column : List.of("display_name_en", "displayname_en", "english_display_name", "short_display_name_en", "shortdisplayname_en", "english_short_display_name",
                "display_name_tr", "displayname_tr", "turkish_display_name", "short_display_name_tr", "shortdisplayname_tr", "turkish_short_display_name")) {
            String value = firstText(row, column);
            if (value != null && FoodCatalogNamingRules.requiresManualDisplayReview(value)) return true;
        }
        return false;
    }

    private boolean hasUnreviewedNamingOverride(CsvRow row, FoodSourceNameRules.Decision decision) {
        if (decision == null) return false;
        for (String column : List.of("display_name", "displayname", "short_display_name", "shortdisplayname",
                "display_name_en", "displayname_en", "english_display_name", "short_display_name_en", "shortdisplayname_en", "english_short_display_name",
                "display_name_tr", "displayname_tr", "turkish_display_name", "short_display_name_tr", "shortdisplayname_tr", "turkish_short_display_name")) {
            String value = firstText(row, column);
            PreferredLanguage language = column.endsWith("_tr") || column.startsWith("turkish_") ? PreferredLanguage.TR : PreferredLanguage.EN;
            if (value != null && !decision.displayName(language).equals(FoodProductNormalizationRules.normalizeProductDisplayName(value, language))) return true;
        }
        return false;
    }

    private ProductDisplayNames buildUsdaGenericDisplayNames(String sourceName, FoodPreparationState preparationState) {
        List<String> parts = List.of(sourceName.split(","));
        String base = parts.isEmpty() ? sourceName : parts.get(0).trim();
        StringBuilder descriptors = new StringBuilder();
        for (int index = 1; index < parts.size(); index++) {
            String descriptor = parts.get(index).trim().toLowerCase(Locale.ROOT);
            if (descriptor.isBlank() || isRedundantPreparationDescriptor(descriptor, preparationState)) {
                continue;
            }
            if (!descriptors.isEmpty()) {
                descriptors.append(", ");
            }
            descriptors.append(descriptor);
        }

        base = singularizeSimpleFoodName(base);
        String displayName = buildUsdaDisplayPhrase(base, descriptors.toString());
        displayName = FoodProductNormalizationRules.normalizeProductDisplayName(displayName.toLowerCase(Locale.ROOT));
        String shortDisplayName = displayName;
        String preparationPrefix = preparationDisplayPrefix(preparationState);
        if (preparationPrefix != null && displayName != null && shouldPrefixPreparation(displayName) && !displayName.toLowerCase(Locale.ROOT).startsWith(preparationPrefix.toLowerCase(Locale.ROOT) + " ")) {
            shortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(preparationPrefix + " " + displayName);
        }
        return new ProductDisplayNames(displayName, shortDisplayName);
    }

    private String buildUsdaDisplayPhrase(String base, String descriptors) {
        String normalizedBase = base == null ? "" : base.trim();
        String normalizedDescriptors = descriptors == null ? "" : descriptors.trim();
        if (normalizedDescriptors.isBlank()) {
            return normalizedBase;
        }
        if (normalizedBase.equalsIgnoreCase("fish")) {
            String[] parts = normalizedDescriptors.split(", ", 2);
            return parts[0] + (parts.length == 2 ? " (" + parts[1] + ")" : "");
        }
        if (normalizedBase.equalsIgnoreCase("flour")) {
            return normalizedDescriptors + " flour";
        }
        return normalizedBase + " (" + normalizedDescriptors + ")";
    }

    private boolean isPreparationDescriptor(String descriptor) {
        return descriptor.equals("raw")
                || descriptor.equals("cooked")
                || descriptor.equals("boiled")
                || descriptor.equals("grilled")
                || descriptor.equals("baked")
                || descriptor.equals("fried")
                || descriptor.equals("roasted")
                || descriptor.equals("steamed")
                || descriptor.equals("prepared");
    }

    private boolean isRedundantPreparationDescriptor(String descriptor, FoodPreparationState state) {
        if (!isPreparationDescriptor(descriptor) || state == null || state == FoodPreparationState.UNSPECIFIED) return false;
        if (descriptor.equals(state.name().toLowerCase(Locale.ROOT))) return true;
        return descriptor.equals("cooked") && Set.of(FoodPreparationState.BOILED, FoodPreparationState.ROASTED,
                FoodPreparationState.GRILLED, FoodPreparationState.FRIED, FoodPreparationState.BAKED, FoodPreparationState.STEAMED).contains(state);
    }

    private String singularizeSimpleFoodName(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.length() < 4) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") || lower.endsWith("ss") || lower.endsWith("us")) {
            return normalized;
        }
        if (lower.endsWith("s")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean shouldPrefixPreparation(String displayName) {
        String normalized = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        return !normalized.contains(" flour")
                && !normalized.endsWith("flour")
                && !normalized.contains(" oil")
                && !normalized.endsWith("oil")
                && !normalized.contains(" sauce")
                && !normalized.endsWith("sauce");
    }


    private String preparationDisplayPrefix(FoodPreparationState preparationState) {
        if (preparationState == null) {
            return null;
        }
        return switch (preparationState) {
            case RAW -> "Raw";
            case COOKED -> "Cooked";
            case BOILED -> "Boiled";
            case GRILLED -> "Grilled";
            case FRIED -> "Fried";
            case BAKED -> "Baked";
            case ROASTED -> "Roasted";
            case STEAMED -> "Steamed";
            case PREPARED -> "Prepared";
            case UNSPECIFIED -> null;
        };
    }


    private FoodProductImportMode normalizeImportMode(FoodProductImportMode importMode) {
        return importMode == null ? FoodProductImportMode.CURATED_ADMIN : importMode;
    }

    private boolean isCuratedProduct(FoodItemEntity product) {
        return product.getVerificationStatus() == VerificationStatus.VERIFIED
                || product.getDataSource() == FoodDataSource.ADMIN_IMPORT
                || product.getDataSource() == FoodDataSource.MANUAL;
    }

    private void registerExistingProduct(Map<String, FoodItemEntity> productsByKey, FoodItemEntity product) {
        if (product == null) {
            return;
        }
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(product.getNormalizedBarcode());
        if (normalizedBarcode != null) {
            productsByKey.putIfAbsent("barcode:" + normalizedBarcode, product);
            productsByKey.putIfAbsent(normalizedBarcode, product);
        }
        String sourceKey = FoodProductNormalizationRules.normalizeText(product.getSourceKey());
        if (sourceKey != null) {
            productsByKey.putIfAbsent(sourceKey, product);
        }
    }

    private String resolveInputKey(CsvRow row, MarketRegion marketRegion) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(firstText(row, "barcode", "code", "gtin", "ean", "upc"));
        String name = firstText(row, "name", "productname", "product_name", "description", "food_description", "lowercase_description");
        String explicitKey = firstText(row, "sourcekey", "source_key", "externalid", "external_id");
        if (explicitKey != null) {
            return explicitKey;
        }
        String fdcId = firstText(row, "fdc_id", "fdcid", "fdc");
        if (fdcId != null) {
            return "USDA_FOODDATA:fdc:" + fdcId;
        }
        if (normalizedBarcode != null) {
            return "barcode:" + normalizedBarcode;
        }
        FoodCatalogType catalogType = resolveCatalogType(row, normalizedBarcode);
        FoodPreparationState preparationState = resolvePreparationState(row);
        return resolveSourceKey(row, normalizedBarcode, name, catalogType, marketRegion, preparationState);
    }

    private FoodCatalogType resolveCatalogType(CsvRow row, String normalizedBarcode) {
        FoodCatalogType declared = declaredCatalogType(row);
        if (isUsdaRow(row)) {
            return com.grun.calorietracker.service.support.UsdaCatalogClassificationRules.resolve(
                    firstText(row, "source_data_type", "data_type", "datatype"), declared, normalizedBarcode != null,
                    usdaSourceIdentity(row), firstText(row, "name", "product_name", "description"));
        }
        return declared == null ? FoodCatalogType.BRANDED_PRODUCT : declared;
    }

    private String usdaSourceIdentity(CsvRow row) {
        String key = firstText(row, "source_key", "sourcekey", "externalid", "external_id");
        String fdc = firstText(row, "fdc_id", "fdcid", "fdc");
        return key != null ? key : fdc != null ? "USDA_FOODDATA:fdc:" + fdc : null;
    }

    private boolean isUsdaRow(CsvRow row) {
        String source = firstText(row, "data_source", "datasource");
        String key = firstText(row, "source_key", "sourcekey");
        return firstText(row, "fdc_id", "fdcid", "fdc") != null
                || (source != null && (source.equalsIgnoreCase("USDA") || source.equalsIgnoreCase("USDA_FOODDATA")))
                || (key != null && key.toUpperCase(Locale.ROOT).startsWith("USDA"));
    }

    private FoodCatalogType declaredCatalogType(CsvRow row) {
        String value = firstText(row, "catalogtype", "catalog_type", "producttype", "product_type", "type");
        if (value != null) {
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if ("INGREDIENT".equals(normalized) || "GENERIC".equals(normalized)) {
                normalized = FoodCatalogType.GENERIC_INGREDIENT.name();
            } else if ("DISH".equals(normalized) || "RECIPE".equals(normalized) || "LOCAL_RECIPE".equals(normalized)) {
                normalized = FoodCatalogType.LOCAL_DISH.name();
            } else if ("BRANDED".equals(normalized) || "PRODUCT".equals(normalized) || "PACKAGED_PRODUCT".equals(normalized)) {
                normalized = FoodCatalogType.BRANDED_PRODUCT.name();
            }
            try {
                return FoodCatalogType.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported catalog_type: " + value);
            }
        }
        return null;
    }


    private FoodPreparationState resolvePreparationState(CsvRow row) {
        String value = firstText(row, "preparationstate", "preparation_state", "prepstate", "prep_state", "cookingstate", "cooking_state", "state");
        if (value == null) {
            return FoodPreparationState.UNSPECIFIED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("UNCOOKED".equals(normalized) || "FRESH".equals(normalized)) {
            normalized = FoodPreparationState.RAW.name();
        } else if ("COOK".equals(normalized)) {
            normalized = FoodPreparationState.COOKED.name();
        } else if ("MIXED".equals(normalized) || "DISH".equals(normalized) || "RECIPE".equals(normalized)) {
            normalized = FoodPreparationState.PREPARED.name();
        }
        try {
            return FoodPreparationState.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return FoodPreparationState.UNSPECIFIED;
        }
    }

    private FoodNutritionBasis resolveNutritionBasis(CsvRow row, FoodCatalogType catalogType) {
        String value = firstText(row, "nutritionbasis", "nutrition_basis", "nutritionvaluebasis", "nutrition_value_basis");
        if (value == null) {
            return catalogType == FoodCatalogType.LOCAL_DISH
                    ? FoodNutritionBasis.ESTIMATED
                    : FoodNutritionBasis.SOURCE_REPORTED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return FoodNutritionBasis.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return catalogType == FoodCatalogType.LOCAL_DISH
                    ? FoodNutritionBasis.ESTIMATED
                    : FoodNutritionBasis.SOURCE_REPORTED;
        }
    }

    private FoodNutritionReferenceUnit resolveNutritionReferenceUnit(CsvRow row) {
        String value = firstText(
                row,
                "nutrition_reference_unit",
                "nutritionreferenceunit",
                "nutrition_unit",
                "nutritionunit",
                "nutrition_data_per"
        );
        if (value == null) {
            return FoodNutritionReferenceUnit.PER_100G;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("100G".equals(normalized) || "PER100G".equals(normalized)) {
            normalized = FoodNutritionReferenceUnit.PER_100G.name();
        } else if ("100ML".equals(normalized) || "PER100ML".equals(normalized)) {
            normalized = FoodNutritionReferenceUnit.PER_100ML.name();
        } else if ("SERVING".equals(normalized) || "PERSERVING".equals(normalized)) {
            normalized = FoodNutritionReferenceUnit.PER_SERVING.name();
        }
        try {
            return FoodNutritionReferenceUnit.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported nutrition reference unit: " + value
                    + ". Supply PER_100G, PER_100ML, or PER_SERVING from the nutrition source.");
        }
    }

    private String resolveLocalDishIdentityKey(CsvRow row, FoodCatalogType catalogType, String... columns) {
        if (catalogType != FoodCatalogType.LOCAL_DISH) {
            return null;
        }
        return FoodProductCanonicalKeyRules.normalizeLocalDishIdentityKey(firstText(row, columns));
    }
    private void addPotentialCanonicalDuplicateWarnings(
            Map<String, Integer> warningCounts,
            List<FoodProductImportWarningDto> warnings,
            List<ProductImportContext> contexts
    ) {
        List<String> canonicalKeys = contexts.stream()
                .map(ProductImportContext::product)
                .map(FoodItemEntity::getCanonicalFoodKey)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (canonicalKeys.isEmpty()) {
            return;
        }

        Map<String, List<FoodItemEntity>> existingByCanonicalKey = BatchQuerySupport.loadInChunks(
                        canonicalKeys,
                        keys -> foodItemRepository.findByCanonicalFoodKeyIn(keys, Sort.by("id").ascending())
                ).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        FoodItemEntity::getCanonicalFoodKey,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        Map<String, String> firstBatchSourceByCanonicalKey = new HashMap<>();
        for (ProductImportContext context : contexts) {
            FoodItemEntity product = context.product();
            String canonicalKey = product.getCanonicalFoodKey();
            if (canonicalKey == null) {
                continue;
            }

            String sourceKey = FoodProductNormalizationRules.normalizeText(product.getSourceKey());
            String firstBatchSource = firstBatchSourceByCanonicalKey.putIfAbsent(canonicalKey, sourceKey);
            boolean duplicatesBatchProduct = firstBatchSource != null
                    && !java.util.Objects.equals(firstBatchSource, sourceKey);
            boolean duplicatesStoredProduct = existingByCanonicalKey
                    .getOrDefault(canonicalKey, List.of())
                    .stream()
                    .anyMatch(existing -> !sameCatalogRecord(existing, product));

            if (duplicatesBatchProduct || duplicatesStoredProduct) {
                addWarning(
                        warningCounts,
                        warnings,
                        context.row(),
                        resolveWarningIdentifier(context.row(), product),
                        "POTENTIAL_GENERIC_DUPLICATE",
                        "Generic ingredient matches an existing canonical food identity from another source. Review nutrition quality before merge."
                );
            }
        }
    }

    private boolean sameCatalogRecord(FoodItemEntity left, FoodItemEntity right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return java.util.Objects.equals(
                FoodProductNormalizationRules.normalizeText(left.getSourceKey()),
                FoodProductNormalizationRules.normalizeText(right.getSourceKey())
        );
    }
    private String resolveSourceKey(
            CsvRow row,
            String normalizedBarcode,
            String name,
            FoodCatalogType catalogType,
            MarketRegion marketRegion,
            FoodPreparationState preparationState
    ) {
        String explicitSourceKey = firstText(row, "sourcekey", "source_key", "externalid", "external_id");
        if (explicitSourceKey != null) {
            return explicitSourceKey;
        }
        String fdcId = firstText(row, "fdc_id", "fdcid", "fdc");
        if (fdcId != null) {
            return "USDA_FOODDATA:fdc:" + fdcId;
        }
        if (normalizedBarcode != null) {
            return "barcode:" + normalizedBarcode;
        }
        if (catalogType == FoodCatalogType.BRANDED_PRODUCT || name == null) {
            return null;
        }
        MarketRegion effectiveRegion = marketRegion == null ? MarketRegion.GLOBAL : marketRegion;
        FoodPreparationState effectivePreparationState = preparationState == null ? FoodPreparationState.UNSPECIFIED : preparationState;
        return effectiveRegion.name() + ":" + catalogType.name() + ":" + effectivePreparationState.name() + ":" + slug(name);
    }

    private String slug(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            return "unnamed";
        }
        return slug.length() <= 160 ? slug : slug.substring(0, 160);
    }

    private void applyImportMetadata(
            FoodItemEntity product,
            CsvRow row,
            String importedBy,
            FoodProductImportMode importMode,
            FoodProductImportFormat sourceFormat
    ) {
        if (importMode == FoodProductImportMode.RAW_EXTERNAL) {
            FoodDataSource dataSource = resolveDataSource(row, product.getDataSource() != null
                    ? product.getDataSource() : defaultDataSource(importMode, sourceFormat));
            product.setDataSource(dataSource);
            product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
            product.setImageSource(dataSource == FoodDataSource.OPEN_FOOD_FACTS ? ImageSource.OPEN_FOOD_FACTS : ImageSource.ADMIN_UPLOAD);
            product.setImageStatus(resolvePassiveImageStatus(row));
            product.setReviewedBy(null);
            product.setLastReviewedAt(null);
            copyExternalImageIfMissing(product, row);
            return;
        }

        product.setDataSource(resolveDataSource(row, product.getDataSource() != null
                ? product.getDataSource() : defaultDataSource(importMode, sourceFormat)));
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setImageSource(resolveImageSource(row));
        product.setImageStatus(resolveImageStatus(row));
        product.setLastReviewedAt(LocalDateTime.now());
        product.setReviewedBy(importedBy);
    }

    private void copyExternalImageIfMissing(FoodItemEntity product, CsvRow row) {
        if (FoodProductNormalizationRules.normalizeText(product.getExternalImageUrl()) != null) {
            return;
        }

        String externalImageUrl = firstText(row, "externalimageurl", "external_image_url", "imageurl", "image_url");
        if (externalImageUrl != null) {
            product.setExternalImageUrl(externalImageUrl);
        }
    }

    private void updateQualityMetadata(FoodItemEntity product, FoodProductImportMode importMode) {
        if (importMode == FoodProductImportMode.RAW_EXTERNAL) {
            FoodProductQualityRules.markExternalImport(product);
            return;
        }
        FoodProductQualityRules.markReviewed(product);
    }

    private boolean requiresReview(FoodItemEntity product) {
        if (product.getPublicationStatus() == CatalogPublicationStatus.INTERNAL_REVIEW) {
            return true;
        }
        if (product.getVerificationStatus() == VerificationStatus.NEEDS_REVIEW) {
            return true;
        }
        return product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED
                && !Boolean.TRUE.equals(product.getAutoApprovedForCatalog());
    }

    private void addQualityWarnings(
            Map<String, Integer> warningCounts,
            List<FoodProductImportWarningDto> warnings,
            CsvRow row,
            FoodItemEntity product,
            RegionResolution regionResolution
    ) {
        String identifier = resolveWarningIdentifier(row, product);
        if (isUsdaRow(row) && product.getSourceDataType() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_USDA_DATA_TYPE",
                    "FDC id/provider alone cannot prove classification; source subtype is required before release.");
        }
        if (firstText(row, "nutrition_reference_unit", "nutritionreferenceunit", "nutrition_unit",
                "nutritionunit", "nutrition_data_per") == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_NUTRITION_REFERENCE_UNIT",
                    "Nutrition reference unit was not supplied. Existing basis was retained, or legacy PER_100G used for a new row; verify against the source before publication.");
        }
        if (regionResolution.missing()) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_REGION", "Product has no explicit market region and was imported as GLOBAL.");
        }
        if (regionResolution.unsupported()) {
            addWarning(warningCounts, warnings, row, identifier, "UNSUPPORTED_REGION", "Product has an unsupported market region and was imported with fallback region.");
        }
        if (product.getCalories() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_CALORIES", "Product has no calories value.");
        }
        if (product.getProtein() == null || product.getFat() == null || product.getCarbs() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_MACROS", "Product is missing at least one protein, fat, or carbohydrate value; unknown is not zero.");
        }
        if (product.getNutritionReferenceUnit() != FoodNutritionReferenceUnit.PER_SERVING
                && product.getServingSizeGrams() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_SERVING_SIZE", "Product has no serving size value.");
        }
        String servingOptionsJson = firstText(row, "serving_options_json", "servingoptionsjson", "portion_options_json");
        ServingOptionsParseResult servingOptions = parseServingOptions(row, product.getNutritionReferenceUnit());
        if ((servingOptionsJson != null && !servingOptions.valid())
                || (product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_SERVING
                    && (servingOptionsJson == null || servingOptions.options().isEmpty()))) {
            addWarning(warningCounts, warnings, row, identifier, "INVALID_SERVING_OPTIONS",
                    "Serving options JSON is invalid, duplicated, has an invalid default, or lacks the conversion required by its nutrition reference.");
        }
        if (product.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT
                && (product.getPreparationState() == null || product.getPreparationState() == FoodPreparationState.UNSPECIFIED)) {
            addWarning(warningCounts, warnings, row, identifier, "GENERIC_MISSING_PREPARATION_STATE", "Generic ingredient has no preparation state. Raw/cooked/prepared differences must be explicit.");
        }
        if (hasSuspiciousUserFacingName(product)) {
            addWarning(warningCounts, warnings, row, identifier, "SUSPICIOUS_DISPLAY_NAME", "Product display name looks too long, source-like, or unsuitable for mobile search results.");
        }
        if (isUsdaRow(row) && product.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT
                && FoodCatalogNamingRules.sourcePreparationConflict(product.getName(), product.getPreparationState())) {
            addWarning(warningCounts, warnings, row, identifier, "SOURCE_PREPARATION_NAME_CONFLICT",
                    "Explicit source preparation descriptors disagree with the declared state; review instead of hiding the qualifier or inferring a new state.");
        }
        if (FoodCatalogNamingRules.requiresManualDisplayReview(product.getDisplayName())
                || FoodCatalogNamingRules.requiresManualDisplayReview(product.getShortDisplayName()) || hasUnsafeLocalizedDisplay(row)) {
            addWarning(warningCounts, warnings, row, identifier, "DISPLAY_TEXT_REQUIRES_REVIEW",
                    "Display text contains markup, encoding/control characters, invalid parentheses or exceeds the display limit.");
        }
        FoodSourceNameRules.match(product.getSourceKey(),
                firstText(row, "name", "productname", "product_name", "description", "food_description", "lowercase_description"),
                product.getSourceDataType()).filter(FoodSourceNameRules.Decision::servingReviewRequired).ifPresent(decision ->
                addWarning(warningCounts, warnings, row, identifier, "SERVING_REVIEW_AFTER_NAMING_CORRECTION",
                        "Source naming/preparation correction requires serving and alias revalidation before publication."));
        FoodSourceNameRules.match(product.getSourceKey(),
                firstText(row, "name", "productname", "product_name", "description", "food_description", "lowercase_description"),
                product.getSourceDataType()).filter(decision -> hasUnreviewedNamingOverride(row, decision)).ifPresent(decision ->
                addWarning(warningCounts, warnings, row, identifier, "SOURCE_NAME_OVERRIDE_REQUIRES_REVIEW",
                        "Explicit name differs from the reviewed source naming profile; independent approval is required."));
        String rawBarcode = firstText(row, "barcode", "code", "gtin", "ean", "upc");
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(rawBarcode);
        if (normalizedBarcode != null && !normalizedBarcode.matches("\\d{6,18}")) {
            addWarning(warningCounts, warnings, row, identifier, "INVALID_BARCODE_FORMAT", "Product barcode is not a numeric value between 6 and 18 digits.");
        }
    }

    private boolean hasSuspiciousUserFacingName(FoodItemEntity product) {
        String displayName = FoodProductNormalizationRules.normalizeText(product.getDisplayName());
        String shortDisplayName = FoodProductNormalizationRules.normalizeText(product.getShortDisplayName());
        String userFacingName = shortDisplayName != null ? shortDisplayName : displayName;
        if (userFacingName == null) {
            return true;
        }
        String normalized = userFacingName.toLowerCase(Locale.ROOT);
        if (userFacingName.length() > 80) {
            return true;
        }
        if (normalized.equals("unknown") || normalized.equals("product") || normalized.equals("food")) {
            return true;
        }
        if (product.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT
                && (userFacingName.contains(";") || (userFacingName.contains(",") && !userFacingName.contains("(")))) {
            return true;
        }
        return false;
    }
    private void syncSearchAliases(
            List<FoodItemEntity> savedProducts,
            List<ProductImportContext> productImportContexts
    ) {
        if (productImportContexts.isEmpty()) {
            return;
        }

        List<Long> productIds = savedProducts.stream()
                .map(FoodItemEntity::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Map<String, FoodItemSearchAliasEntity>> existingAliasesByProductId = new HashMap<>();
        BatchQuerySupport.loadInChunks(
                productIds,
                foodItemSearchAliasRepository::findByFoodItemIdInOrderByFoodItemIdAscActiveDescLanguageAscAliasAsc
        ).forEach(alias -> existingAliasesByProductId
                .computeIfAbsent(alias.getFoodItem().getId(), ignored -> new LinkedHashMap<>())
                .put(aliasKey(alias.getLanguage(), alias.getNormalizedAlias()), alias));

        List<FoodItemSearchAliasEntity> aliasesToSave = new ArrayList<>();
        for (int index = 0; index < productImportContexts.size(); index++) {
            ProductImportContext context = productImportContexts.get(index);
            FoodItemEntity product = index < savedProducts.size() ? savedProducts.get(index) : context.product();
            Map<String, FoodItemSearchAliasEntity> existingAliases = product == null || product.getId() == null
                    ? new LinkedHashMap<>()
                    : existingAliasesByProductId.computeIfAbsent(product.getId(), ignored -> new LinkedHashMap<>());
            aliasesToSave.addAll(resolveSearchAliases(product, context.row(), existingAliases));
        }

        if (!aliasesToSave.isEmpty()) {
            foodItemSearchAliasRepository.saveAll(aliasesToSave);
        }
    }

    private List<FoodItemSearchAliasEntity> resolveSearchAliases(
            FoodItemEntity product,
            CsvRow row,
            Map<String, FoodItemSearchAliasEntity> existingAliases
    ) {
        if (product == null || row == null) {
            return List.of();
        }

        List<FoodItemSearchAliasEntity> aliasesToSave = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();
        FoodSearchAliasType explicitAliasType = resolveAliasType(row, FoodSearchAliasType.ADMIN_MANUAL);
        collectAliases(aliasesToSave, seenAliases, existingAliases, product, row, PreferredLanguage.TR, explicitAliasType,
                "alias_tr", "aliases_tr", "search_alias_tr", "search_aliases_tr", "turkish_alias", "turkish_aliases");
        collectAliases(aliasesToSave, seenAliases, existingAliases, product, row, PreferredLanguage.EN, explicitAliasType,
                "alias_en", "aliases_en", "search_alias_en", "search_aliases_en", "english_alias", "english_aliases");

        PreferredLanguage defaultLanguage = product.getMarketRegion() == MarketRegion.TR ? PreferredLanguage.TR : PreferredLanguage.EN;
        collectAliases(aliasesToSave, seenAliases, existingAliases, product, row, defaultLanguage, FoodSearchAliasType.COMMON_NAME,
                "alias", "aliases", "search_alias", "search_aliases");
        return aliasesToSave;
    }
    private void collectAliases(
            List<FoodItemSearchAliasEntity> aliasesToSave,
            Set<String> seenAliases,
            Map<String, FoodItemSearchAliasEntity> existingAliases,
            FoodItemEntity product,
            CsvRow row,
            PreferredLanguage language,
            FoodSearchAliasType aliasType,
            String... columns
    ) {
        for (String column : columns) {
            String rawAliases = firstText(row, column);
            if (rawAliases != null) {
                addAliasesFromText(aliasesToSave, seenAliases, existingAliases, product, language, aliasType, rawAliases);
            }
        }
    }

    private void addAliasesFromText(
            List<FoodItemSearchAliasEntity> aliasesToSave,
            Set<String> seenAliases,
            Map<String, FoodItemSearchAliasEntity> existingAliases,
            FoodItemEntity product,
            PreferredLanguage language,
            FoodSearchAliasType aliasType,
            String rawAliases
    ) {
        String normalizedProductName = FoodProductNormalizationRules.normalizeSearchAlias(product.getName());
        for (String candidate : parseAliasEntries(rawAliases)) {
            String aliasText = FoodProductNormalizationRules.normalizeText(candidate);
            String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(aliasText);
            if (aliasText == null || normalizedAlias == null || normalizedAlias.equals(normalizedProductName)) {
                continue;
            }

            String key = aliasKey(language, normalizedAlias);
            if (!seenAliases.add(key)) {
                continue;
            }

            FoodItemSearchAliasEntity alias = existingAliases.getOrDefault(key, new FoodItemSearchAliasEntity());
            alias.setFoodItem(product);
            alias.setAlias(aliasText);
            alias.setNormalizedAlias(normalizedAlias);
            alias.setLanguage(language);
            alias.setAliasType(aliasType);
            alias.setSource("admin-import");
            alias.setActive(true);
            aliasesToSave.add(alias);
        }
    }

    private List<String> parseAliasEntries(String input) {
        List<String> candidates = new ArrayList<>();
        if (input.stripLeading().startsWith("[")) {
            try {
                var array = objectMapper.readTree(input);
                if (!array.isArray()) throw new IllegalArgumentException("Aliases must be a JSON string array.");
                for (var value : array) {
                    if (!value.isTextual()) throw new IllegalArgumentException("Alias array entries must be strings.");
                    candidates.add(value.textValue());
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid JSON alias array.");
            }
        } else {
            // Legacy delimiters separate aliases only outside preparation/variant qualifiers.
            StringBuilder current = new StringBuilder();
            int depth = 0;
            for (char character : input.toCharArray()) {
                if (character == '(') depth++;
                if (character == ')' && --depth < 0) throw new IllegalArgumentException("Unbalanced alias qualifiers.");
                if (depth == 0 && (character == ',' || character == ';' || character == '|')) {
                    candidates.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            if (depth != 0) throw new IllegalArgumentException("Unbalanced alias qualifiers.");
            candidates.add(current.toString());
        }
        if (candidates.size() > 100) throw new IllegalArgumentException("At most 100 aliases per input field.");
        for (String alias : candidates) {
            if (alias.length() > 255 || alias.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT)) {
                throw new IllegalArgumentException("Alias exceeds length or contains control characters.");
            }
            int depth = 0;
            for (char character : alias.toCharArray()) {
                if (character == '(') depth++;
                if (character == ')' && --depth < 0) throw new IllegalArgumentException("Unbalanced alias qualifiers.");
            }
            if (depth != 0) throw new IllegalArgumentException("Unbalanced alias qualifiers.");
        }
        return candidates;
    }

    private FoodSearchAliasType resolveAliasType(CsvRow row, FoodSearchAliasType fallback) {
        String value = firstText(row, "aliastype", "alias_type", "search_alias_type");
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return FoodSearchAliasType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String aliasKey(PreferredLanguage language, String normalizedAlias) {
        return language.name() + ":" + normalizedAlias;
    }
    private void syncLocalizations(
            List<FoodItemEntity> savedProducts,
            List<ProductImportContext> productImportContexts
    ) {
        if (productImportContexts.isEmpty()) {
            return;
        }

        List<Long> productIds = savedProducts.stream()
                .map(FoodItemEntity::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> existingByProductId = new HashMap<>();
        BatchQuerySupport.loadInChunks(
                productIds,
                chunk -> foodItemLocalizationRepository.findByFoodItemIdInAndLanguageIn(
                        chunk,
                        Set.of(PreferredLanguage.EN, PreferredLanguage.TR)
                )
        ).forEach(localization -> existingByProductId
                .computeIfAbsent(localization.getFoodItem().getId(), ignored -> new HashMap<>())
                .put(localization.getLanguage(), localization));

        List<FoodItemLocalizationEntity> localizationsToSave = new ArrayList<>();
        for (int index = 0; index < productImportContexts.size(); index++) {
            ProductImportContext context = productImportContexts.get(index);
            FoodItemEntity product = index < savedProducts.size() ? savedProducts.get(index) : context.product();
            Map<PreferredLanguage, FoodItemLocalizationEntity> existingLocalizations =
                    product == null || product.getId() == null
                            ? new HashMap<>()
                            : existingByProductId.computeIfAbsent(product.getId(), ignored -> new HashMap<>());
            collectLocalization(localizationsToSave, existingLocalizations, product, context.row(), PreferredLanguage.EN,
                    new String[]{"display_name_en", "displayname_en", "english_display_name"},
                    new String[]{"short_display_name_en", "shortdisplayname_en", "english_short_display_name"});
            collectLocalization(localizationsToSave, existingLocalizations, product, context.row(), PreferredLanguage.TR,
                    new String[]{"display_name_tr", "displayname_tr", "turkish_display_name"},
                    new String[]{"short_display_name_tr", "shortdisplayname_tr", "turkish_short_display_name"});
        }

        if (!localizationsToSave.isEmpty()) {
            foodItemLocalizationRepository.saveAll(localizationsToSave);
        }
    }

    private void collectLocalization(
            List<FoodItemLocalizationEntity> localizationsToSave,
            Map<PreferredLanguage, FoodItemLocalizationEntity> existingLocalizations,
            FoodItemEntity product,
            CsvRow row,
            PreferredLanguage language,
            String[] displayNameColumns,
            String[] shortDisplayNameColumns
    ) {
        if (product == null || product.getId() == null || row == null) {
            return;
        }

        String displayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, displayNameColumns), language);
        String shortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, shortDisplayNameColumns), language);
        boolean reviewedFallback = displayName == null && shortDisplayName == null;
        if (reviewedFallback && !existingLocalizations.containsKey(language)) {
            var decision = FoodSourceNameRules.match(product.getSourceKey(),
                    firstText(row, "name", "productname", "product_name", "description", "food_description", "lowercase_description"),
                    product.getSourceDataType()).orElse(null);
            if (decision != null) displayName = decision.displayName(language);
        }
        if (displayName == null) {
            displayName = shortDisplayName;
        }
        if (displayName == null) {
            return;
        }

        FoodItemLocalizationEntity localization = existingLocalizations
                .getOrDefault(language, new FoodItemLocalizationEntity());
        LocalDateTime now = LocalDateTime.now();
        localization.setFoodItem(product);
        localization.setLanguage(language);
        localization.setDisplayName(displayName);
        localization.setShortDisplayName(shortDisplayName == null ? displayName : shortDisplayName);
        localization.setSource(reviewedFallback ? FoodSourceNameRules.VERSION : "admin-import");
        localization.setActive(true);
        if (localization.getCreatedAt() == null) {
            localization.setCreatedAt(now);
        }
        localization.setUpdatedAt(now);
        existingLocalizations.put(language, localization);
        localizationsToSave.add(localization);
    }
    private void syncServingOptions(
            List<FoodItemEntity> savedProducts,
            List<ProductImportContext> productImportContexts,
            FoodProductImportMode importMode
    ) {
        List<Long> productIds = savedProducts.stream()
                .map(FoodItemEntity::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, List<FoodItemServingOptionEntity>> existingOptionsByProductId = new HashMap<>();
        BatchQuerySupport.loadInChunks(
                productIds,
                foodItemServingOptionRepository::findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc
        ).forEach(option -> existingOptionsByProductId
                .computeIfAbsent(option.getFoodItem().getId(), ignored -> new ArrayList<>())
                .add(option));

        List<ServingOptionSyncContext> servingContexts = new ArrayList<>();
        List<FoodItemServingOptionEntity> optionsToSave = new ArrayList<>();
        for (int index = 0; index < productImportContexts.size(); index++) {
            ProductImportContext context = productImportContexts.get(index);
            FoodItemEntity product = index < savedProducts.size() ? savedProducts.get(index) : context.product();
            if (product == null || product.getId() == null) {
                continue;
            }

            ServingOptionsParseResult parsed = parseServingOptions(context.row(), product.getNutritionReferenceUnit());
            String explicitServingOptions = firstText(
                    context.row(), "serving_options_json", "servingoptionsjson", "portion_options_json");
            if (!parsed.valid() || explicitServingOptions == null) {
                continue;
            }

            List<FoodItemServingOptionEntity> existingOptions =
                    existingOptionsByProductId.getOrDefault(product.getId(), List.of());
            boolean replaceExisting = importMode == FoodProductImportMode.CURATED_ADMIN;
            if (replaceExisting) {
                Set<String> requestedKeys = parsed.options().stream()
                        .map(option -> servingOptionKey(option.label()))
                        .collect(java.util.stream.Collectors.toSet());
                List<FoodItemServingOptionEntity> obsoleteOptions = existingOptions.stream()
                        .filter(option -> !requestedKeys.contains(servingOptionKey(option.getLabel())))
                        .toList();
                if (!obsoleteOptions.isEmpty()) {
                    List<Long> obsoleteIds = obsoleteOptions.stream()
                            .map(FoodItemServingOptionEntity::getId)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    if (!obsoleteIds.isEmpty()) {
                        List<FoodItemServingOptionLocalizationEntity> obsoleteLocalizations =
                                foodItemServingOptionLocalizationRepository.findByServingOptionIdIn(obsoleteIds);
                        if (!obsoleteLocalizations.isEmpty()) {
                            foodItemServingOptionLocalizationRepository.deleteAll(obsoleteLocalizations);
                        }
                    }
                    foodItemServingOptionRepository.deleteAll(obsoleteOptions);
                }
            }
            if (parsed.options().isEmpty()) {
                continue;
            }
            Map<String, FoodItemServingOptionEntity> optionsByLabel = new LinkedHashMap<>();
            existingOptions.forEach(option -> optionsByLabel.put(servingOptionKey(option.getLabel()), option));

            boolean requestedDefault = parsed.options().stream()
                    .anyMatch(option -> Boolean.TRUE.equals(option.defaultOption()));
            if (requestedDefault) {
                existingOptions.forEach(option -> option.setIsDefault(false));
            }
            boolean hasDefault = !requestedDefault && existingOptions.stream()
                    .anyMatch(option -> Boolean.TRUE.equals(option.getIsDefault()));

            for (int optionIndex = 0; optionIndex < parsed.options().size(); optionIndex++) {
                ServingOptionImport definition = parsed.options().get(optionIndex);
                String key = servingOptionKey(definition.label());
                FoodItemServingOptionEntity option =
                        optionsByLabel.getOrDefault(key, new FoodItemServingOptionEntity());
                option.setFoodItem(product);
                option.setLabel(FoodProductNormalizationRules.normalizeText(definition.label()));
                option.setUnitType(parseServingOptionUnit(definition.unitType()));
                option.setQuantity(definition.quantity() == null ? 1.0 : definition.quantity());
                option.setGramWeight(positiveOrNull(definition.gramWeight()));
                option.setMlVolume(positiveOrNull(definition.mlVolume()));

                boolean isDefault = requestedDefault
                        ? Boolean.TRUE.equals(definition.defaultOption())
                        : Boolean.TRUE.equals(option.getIsDefault()) || (!hasDefault && optionIndex == 0);
                option.setIsDefault(isDefault);
                if (isDefault) {
                    hasDefault = true;
                }
                option.setSource(product.getDataSource() == FoodDataSource.OPEN_FOOD_FACTS
                        ? FoodServingOptionSource.OPEN_FOOD_FACTS
                        : FoodServingOptionSource.ADMIN);
                option.setQualityStatus(product.getVerificationStatus() == VerificationStatus.VERIFIED
                        ? FoodServingOptionQualityStatus.VERIFIED
                        : FoodServingOptionQualityStatus.NEEDS_REVIEW);
                optionsByLabel.put(key, option);
            }

            if (replaceExisting) {
                parsed.options().stream()
                        .map(option -> optionsByLabel.get(servingOptionKey(option.label())))
                        .filter(java.util.Objects::nonNull)
                        .forEach(optionsToSave::add);
            } else {
                optionsToSave.addAll(optionsByLabel.values());
            }
            servingContexts.add(new ServingOptionSyncContext(product.getId(), parsed.options()));
        }

        if (optionsToSave.isEmpty()) {
            return;
        }
        List<FoodItemServingOptionEntity> savedOptions = foodItemServingOptionRepository.saveAll(optionsToSave);
        syncServingOptionLocalizations(servingContexts, savedOptions);
    }

    private void syncServingOptionLocalizations(
            List<ServingOptionSyncContext> servingContexts,
            List<FoodItemServingOptionEntity> savedOptions
    ) {
        Map<String, FoodItemServingOptionEntity> savedOptionsByProductAndLabel = new HashMap<>();
        savedOptions.stream()
                .filter(option -> option.getId() != null && option.getFoodItem() != null)
                .forEach(option -> savedOptionsByProductAndLabel.put(
                        savedServingOptionKey(option.getFoodItem().getId(), option.getLabel()),
                        option
                ));
        List<Long> optionIds = savedOptionsByProductAndLabel.values().stream()
                .map(FoodItemServingOptionEntity::getId)
                .toList();
        if (optionIds.isEmpty()) {
            return;
        }

        Map<String, FoodItemServingOptionLocalizationEntity> existingByOptionAndLanguage = new HashMap<>();
        BatchQuerySupport.loadInChunks(
                optionIds,
                foodItemServingOptionLocalizationRepository::findByServingOptionIdIn
        ).forEach(localization -> existingByOptionAndLanguage.put(
                servingLocalizationKey(localization.getServingOption().getId(), localization.getLanguage()),
                localization
        ));

        List<FoodItemServingOptionLocalizationEntity> localizationsToSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ServingOptionSyncContext context : servingContexts) {
            for (ServingOptionImport definition : context.definitions()) {
                FoodItemServingOptionEntity savedOption = savedOptionsByProductAndLabel.get(
                        savedServingOptionKey(context.productId(), definition.label())
                );
                if (savedOption == null || definition.labels() == null) {
                    continue;
                }
                for (Map.Entry<String, String> entry : definition.labels().entrySet()) {
                    PreferredLanguage language = parsePreferredLanguage(entry.getKey());
                    String label = FoodProductNormalizationRules.normalizeText(entry.getValue());
                    String key = servingLocalizationKey(savedOption.getId(), language);
                    FoodItemServingOptionLocalizationEntity localization = existingByOptionAndLanguage
                            .getOrDefault(key, new FoodItemServingOptionLocalizationEntity());
                    localization.setServingOption(savedOption);
                    localization.setLanguage(language);
                    localization.setLabel(label);
                    localization.setSource("admin-import");
                    localization.setActive(true);
                    if (localization.getCreatedAt() == null) {
                        localization.setCreatedAt(now);
                    }
                    localization.setUpdatedAt(now);
                    existingByOptionAndLanguage.put(key, localization);
                    localizationsToSave.add(localization);
                }
            }
        }
        if (!localizationsToSave.isEmpty()) {
            foodItemServingOptionLocalizationRepository.saveAll(localizationsToSave);
        }
    }

    private String savedServingOptionKey(Long productId, String label) {
        return productId + ":" + servingOptionKey(label);
    }

    private String servingLocalizationKey(Long servingOptionId, PreferredLanguage language) {
        return servingOptionId + ":" + language.name();
    }

    private PreferredLanguage parsePreferredLanguage(String value) {
        String normalized = FoodProductNormalizationRules.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return PreferredLanguage.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
    private ServingOptionsParseResult parseServingOptions(
            CsvRow row,
            FoodNutritionReferenceUnit nutritionReferenceUnit
    ) {
        String rawJson = firstText(row, "serving_options_json", "servingoptionsjson", "portion_options_json");
        if (rawJson == null) {
            return new ServingOptionsParseResult(List.of(), true);
        }

        try {
            List<ServingOptionImport> options = objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<ServingOptionImport>>() {}
            );
            // An explicit empty array is meaningful for CURATED_ADMIN imports:
            // it removes previously stored ready-portion options.
            if (options == null || options.size() > 20) {
                return new ServingOptionsParseResult(List.of(), false);
            }

            Set<String> labels = new HashSet<>();
            int defaultCount = 0;
            for (ServingOptionImport option : options) {
                String label = FoodProductNormalizationRules.normalizeText(option.label());
                FoodServingOptionUnit unit = parseServingOptionUnit(option.unitType());
                double quantity = option.quantity() == null ? 1.0 : option.quantity();
                boolean hasWeight = positiveOrNull(option.gramWeight()) != null
                        || positiveOrNull(option.mlVolume()) != null;
                boolean nativeServing = nutritionReferenceUnit == FoodNutritionReferenceUnit.PER_SERVING
                        && (unit == FoodServingOptionUnit.SERVING
                            || unit == FoodServingOptionUnit.PORTION
                            || unit == FoodServingOptionUnit.PIECE);
                if (label == null || label.length() > 120 || unit == null
                        || !Double.isFinite(quantity) || quantity <= 0 || (!hasWeight && !nativeServing)
                        || (option.gramWeight() != null && positiveOrNull(option.gramWeight()) == null)
                        || (option.mlVolume() != null && positiveOrNull(option.mlVolume()) == null)
                        || !labels.add(servingOptionKey(label))) {
                    return new ServingOptionsParseResult(List.of(), false);
                }
                if (option.labels() != null) {
                    for (Map.Entry<String, String> entry : option.labels().entrySet()) {
                        String localizedLabel = FoodProductNormalizationRules.normalizeText(entry.getValue());
                        if (parsePreferredLanguage(entry.getKey()) == null
                                || localizedLabel == null
                                || localizedLabel.length() > 120) {
                            return new ServingOptionsParseResult(List.of(), false);
                        }
                    }
                }
                if (Boolean.TRUE.equals(option.defaultOption())) {
                    defaultCount++;
                }
            }
            if (defaultCount > 1
                    || (nutritionReferenceUnit == FoodNutritionReferenceUnit.PER_SERVING
                        && !options.isEmpty() && defaultCount != 1)) {
                return new ServingOptionsParseResult(List.of(), false);
            }
            return new ServingOptionsParseResult(options, true);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return new ServingOptionsParseResult(List.of(), false);
        }
    }

    private FoodServingOptionUnit parseServingOptionUnit(String value) {
        String normalized = FoodProductNormalizationRules.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return FoodServingOptionUnit.valueOf(
                    normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Double positiveOrNull(Double value) {
        return value != null && Double.isFinite(value) && value > 0 ? value : null;
    }

    private String servingOptionKey(String label) {
        String normalized = FoodProductNormalizationRules.normalizeSearchAlias(label);
        return normalized == null ? "" : normalized;
    }
    private void syncQualityIssues(
            List<FoodItemEntity> savedProducts,
            List<ProductImportContext> productImportContexts,
            String importedBy
    ) {
        if (productImportContexts.isEmpty()) {
            return;
        }

        List<FoodProductQualityIssueTracker.ImportIssueContext> issueContexts = new ArrayList<>();
        for (int index = 0; index < productImportContexts.size(); index++) {
            ProductImportContext context = productImportContexts.get(index);
            FoodItemEntity product = index < savedProducts.size() ? savedProducts.get(index) : context.product();
            issueContexts.add(new FoodProductQualityIssueTracker.ImportIssueContext(
                    product,
                    context.regionResolution().missing(),
                    context.regionResolution().unsupported()
            ));
        }
        foodProductQualityIssueTracker.syncImportIssues(issueContexts, importedBy);
    }

    private void addWarning(
            Map<String, Integer> warningCounts,
            List<FoodProductImportWarningDto> warnings,
            CsvRow row,
            String identifier,
            String code,
            String reason
    ) {
        warningCounts.merge(code, 1, Integer::sum);
        if (warnings.size() < MAX_WARNING_DETAILS) {
            warnings.add(new FoodProductImportWarningDto(row.rowNumber(), identifier, code, reason));
        }
    }

    private String resolveWarningIdentifier(CsvRow row, FoodItemEntity product) {
        String rawIdentifier = firstText(row, "barcode", "code", "sourcekey", "source_key", "fdc_id", "fdcid");
        if (rawIdentifier != null) {
            return rawIdentifier;
        }
        if (FoodProductNormalizationRules.normalizeText(product.getSourceKey()) != null) {
            return product.getSourceKey();
        }
        return product.getName();
    }

    private int calculateImportQualityScore(int totalRows, int skippedRows, Map<String, Integer> warningCounts) {
        if (totalRows <= 0) {
            return 100;
        }

        int warningTotal = warningCounts.values().stream().mapToInt(Integer::intValue).sum();
        double skippedPenalty = ((double) skippedRows / totalRows) * 60.0;
        double warningPenalty = ((double) warningTotal / totalRows) * 8.0;
        int score = (int) Math.round(100.0 - skippedPenalty - warningPenalty);
        return Math.max(0, Math.min(100, score));
    }

    private void setIfPresent(CsvRow row, String column, Consumer<String> setter) {
        String value = FoodProductNormalizationRules.normalizeText(row.value(column));
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIfPresent(CsvRow row, Consumer<String> setter, String... columns) {
        String value = firstText(row, columns);
        if (value != null) {
            setter.accept(value);
        }
    }

    private Double parseDouble(Double fallback, CsvRow row, String... columns) {
        for (String column : columns) {
            String value = FoodProductNormalizationRules.normalizeText(row.value(column));
            if (value == null) {
                continue;
            }
            try {
                double parsed = Double.parseDouble(value.replace(',', '.'));
                if (!Double.isFinite(parsed) || parsed < 0.0) {
                    throw new IllegalArgumentException("Finite, non-negative number required for field: " + column);
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid numeric value for field: " + column);
            }
        }
        return fallback;
    }

    private ImageSource resolveImageSource(CsvRow row) {
        String value = FoodProductNormalizationRules.normalizeText(row.value("imagesource"));
        if (value == null) {
            value = FoodProductNormalizationRules.normalizeText(row.value("image_source"));
        }
        if (value != null) {
            try {
                return ImageSource.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ImageSource.ADMIN_UPLOAD;
            }
        }
        return ImageSource.ADMIN_UPLOAD;
    }

    private ImageStatus resolveImageStatus(CsvRow row) {
        String explicitStatus = FoodProductNormalizationRules.normalizeText(row.value("imagestatus"));
        if (explicitStatus == null) {
            explicitStatus = FoodProductNormalizationRules.normalizeText(row.value("image_status"));
        }
        if (explicitStatus != null) {
            try {
                return ImageStatus.valueOf(explicitStatus.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ImageStatus.NEEDS_REVIEW;
            }
        }

        boolean hasDisplayImage = firstText(row, "displayimageurl", "display_image_url", "imageurl", "image_url") != null;
        return hasDisplayImage ? ImageStatus.APPROVED : ImageStatus.NEEDS_REVIEW;
    }

    private ImageStatus resolvePassiveImageStatus(CsvRow row) {
        String explicitStatus = FoodProductNormalizationRules.normalizeText(row.value("imagestatus"));
        if (explicitStatus == null) {
            explicitStatus = FoodProductNormalizationRules.normalizeText(row.value("image_status"));
        }
        if (explicitStatus != null) {
            try {
                return ImageStatus.valueOf(explicitStatus.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ImageStatus.RAW;
            }
        }

        boolean hasExternalImage = firstText(row, "displayimageurl", "display_image_url", "externalimageurl", "external_image_url", "imageurl", "image_url", "image_front_url") != null;
        return hasExternalImage ? ImageStatus.APPROVED : ImageStatus.RAW;
    }

    private void mergeMarketAvailability(FoodItemEntity product, CsvRow row, MarketRegion resolvedRegion) {
        Set<MarketRegion> marketRegions = product.getMarketRegions();
        if (marketRegions == null) {
            marketRegions = new HashSet<>();
            product.setMarketRegions(marketRegions);
        }
        if (product.getMarketRegion() != null) {
            marketRegions.add(product.getMarketRegion());
        }
        if (resolvedRegion != null) {
            marketRegions.add(resolvedRegion);
        }

        String explicitRegions = firstText(row, "marketregions", "market_regions", "availablemarkets", "available_markets");
        if (explicitRegions != null) {
            for (String token : explicitRegions.split("[,;|]")) {
                MarketRegion parsedRegion = parseMarketRegion(token);
                if (parsedRegion != null) {
                    marketRegions.add(parsedRegion);
                }
            }
        }

        if (product.getMarketRegion() == null) {
            product.setMarketRegion(resolvedRegion == null ? MarketRegion.GLOBAL : resolvedRegion);
        }
    }

    private RegionResolution resolveMarketRegion(CsvRow row, MarketRegion fallback) {
        String value = firstText(row, "marketregion", "market_region", "region", "country");
        if (value == null) {
            return new RegionResolution(fallback == null ? MarketRegion.GLOBAL : fallback, true, false);
        }

        MarketRegion parsedRegion = parseMarketRegion(value);
        if (parsedRegion == null) {
            return new RegionResolution(fallback == null ? MarketRegion.GLOBAL : fallback, false, true);
        }
        return new RegionResolution(parsedRegion, false, false);
    }

    private MarketRegion parseMarketRegion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("UK".equals(normalized)
                || "GB".equals(normalized)
                || "GBR".equals(normalized)
                || "UNITED KINGDOM".equals(normalized)
                || "IRELAND".equals(normalized)
                || "IRL".equals(normalized)
                || "IE".equals(normalized)) {
            normalized = "UK_IE";
        } else if ("TURKEY".equals(normalized) || "TURKIYE".equals(normalized) || "TÜRKİYE".equals(normalized)) {
            normalized = "TR";
        } else if ("EUROPE".equals(normalized) || "EUROPEAN UNION".equals(normalized)) {
            normalized = "EU";
        }

        try {
            return MarketRegion.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
    private void addError(List<FoodProductImportErrorDto> errors, FoodProductImportErrorDto error) {
        if (errors.size() < MAX_ERROR_DETAILS) {
            errors.add(error);
        }
    }

    private String firstText(CsvRow row, String... columns) {
        for (String column : columns) {
            String value = FoodProductNormalizationRules.normalizeText(row.value(column));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private FoodProductImportFormat normalizeImportFormat(FoodProductImportFormat importFormat) {
        return importFormat == null ? FoodProductImportFormat.AUTO : importFormat;
    }

    private FoodProductImportFormat detectSourceFormat(Map<String, Integer> headers, FoodProductImportFormat requestedFormat) {
        if (requestedFormat != FoodProductImportFormat.AUTO) {
            return requestedFormat;
        }
        if (headers.containsKey("code") && headers.containsKey("product_name") && headers.containsKey("energy_kcal_100g")) {
            return FoodProductImportFormat.OPEN_FOOD_FACTS_EXPORT;
        }
        if ((headers.containsKey("fdc_id") || headers.containsKey("fdcid")) && headers.containsKey("description")) {
            return FoodProductImportFormat.USDA_FOODDATA;
        }
        return FoodProductImportFormat.GRUN_STANDARD;
    }

    private FoodDataSource defaultDataSource(FoodProductImportMode importMode, FoodProductImportFormat sourceFormat) {
        if (sourceFormat == FoodProductImportFormat.OPEN_FOOD_FACTS_EXPORT) {
            return FoodDataSource.OPEN_FOOD_FACTS;
        }
        if (sourceFormat == FoodProductImportFormat.USDA_FOODDATA) {
            return FoodDataSource.USDA_FOODDATA;
        }
        return importMode == FoodProductImportMode.RAW_EXTERNAL ? FoodDataSource.OPEN_FOOD_FACTS : FoodDataSource.ADMIN_IMPORT;
    }

    private Double parseServingSizeGrams(CsvRow row, Double fallback) {
        Double directValue = parseDouble(null, row, "servingsizegrams", "serving_size_grams");
        if (directValue != null) {
            Double rounded = NutritionValueNormalizer.servingSize(directValue);
            if (rounded <= 0.0) {
                throw new IllegalArgumentException("Positive mass quantity required after rounding for field: serving_size_grams");
            }
            return rounded;
        }

        Double servingQuantity = parseDouble(null, row, "serving_quantity");
        if (servingQuantity != null) {
            String unit = resolveServingUnit(row);
            if ("ml".equalsIgnoreCase(unit) || "MILLILITER".equalsIgnoreCase(unit)) {
                return fallback;
            }
            if ("g".equalsIgnoreCase(unit) || "GRAM".equalsIgnoreCase(unit)) {
                return NutritionValueNormalizer.servingSize(servingQuantity);
            }
        }

        String servingSize = firstText(row, "serving_size");
        if (servingSize == null) {
            return fallback;
        }

        // A volume label is not a mass conversion. Named volume options carry mlVolume separately.
        String servingUnit = resolveServingUnit(row);
        if (!"g".equalsIgnoreCase(servingUnit) && !"GRAM".equalsIgnoreCase(servingUnit)) {
            return fallback;
        }
        if (servingSize.stripLeading().startsWith("-")) {
            throw new IllegalArgumentException("Positive serving size required for field: serving_size");
        }

        var quantity = java.util.regex.Pattern.compile(
                "^\\s*([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(?:g|grams?)(?:\\s*\\(.*\\))?\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(servingSize.replace(',', '.'));
        if (!quantity.matches()) {
            throw new IllegalArgumentException("Invalid mass quantity for field: serving_size");
        }
        double amount = Double.parseDouble(quantity.group(1));
        Double rounded = NutritionValueNormalizer.servingSize(amount);
        if (!Double.isFinite(amount) || amount <= 0.0 || rounded <= 0.0) {
            throw new IllegalArgumentException("Positive, finite mass quantity required for field: serving_size");
        }
        return rounded;
    }

    private String resolveServingUnit(CsvRow row) {
        String explicitUnit = firstText(row, "servingunit", "serving_unit", "serving_quantity_unit");
        if (explicitUnit != null) {
            return explicitUnit;
        }
        String servingSize = firstText(row, "serving_size");
        if (servingSize == null) {
            return null;
        }
        String normalized = servingSize.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(" ml")) {
            return "ml";
        }
        if (normalized.endsWith(" g")) {
            return "g";
        }
        return null;
    }

    private Set<String> resolveSourceCategoryTags(CsvRow row) {
        String raw = firstText(row, "source_categories", "source_category_tags", "categories_tags");
        if (raw == null) {
            return Set.of();
        }
        return java.util.Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalizeHeader(headers.get(i));
            if (normalized != null) {
                index.put(normalized, i);
            }
        }
        return index;
    }

    private void requireHeader(Map<String, Integer> headers, String header) {
        if (!headers.containsKey(header)) {
            throw new IllegalArgumentException("CSV header is required: " + header);
        }
    }

    private void requireAnyHeader(Map<String, Integer> headers, String... acceptedHeaders) {
        for (String header : acceptedHeaders) {
            if (headers.containsKey(header)) {
                return;
            }
        }
        throw new IllegalArgumentException("CSV header is required: name");
    }

    private String normalizeHeader(String header) {
        String value = FoodProductNormalizationRules.normalizeText(header);
        if (value == null) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }

    private char detectDelimiter(String headerLine) {
        return count(headerLine, '\t') > count(headerLine, ',') ? '\t' : ',';
    }

    private int count(String value, char expected) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == expected) {
                count++;
            }
        }
        return count;
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    private record ParsedCsv(List<CsvRow> rows, List<String> normalizedBarcodes, List<String> sourceKeys, String format, FoodProductImportFormat sourceFormat) {
    }

    private FoodDataSource resolveDataSource(CsvRow row, FoodDataSource fallback) {
        String value = firstText(row, "datasource", "data_source", "source", "provider");
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("OFF".equals(normalized) || "OPENFOODFACTS".equals(normalized) || "OPEN_FOOD_FACT".equals(normalized)) {
            normalized = FoodDataSource.OPEN_FOOD_FACTS.name();
        } else if ("USDA".equals(normalized) || "FOODDATA".equals(normalized) || "FOOD_DATA_CENTRAL".equals(normalized)) {
            normalized = FoodDataSource.USDA_FOODDATA.name();
        } else if ("CURATED".equals(normalized) || "LOCAL".equals(normalized) || "LOCAL_ADMIN".equals(normalized)) {
            normalized = FoodDataSource.LOCAL_CURATED.name();
        } else if ("ADMIN".equals(normalized)) {
            normalized = FoodDataSource.ADMIN_IMPORT.name();
        }

        try {
            return FoodDataSource.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private record CsvRow(int rowNumber, Map<String, Integer> headers, List<String> values) {
        private String value(String column) {
            Integer index = headers.get(column);
            if (index == null || index >= values.size()) {
                return null;
            }
            return values.get(index);
        }
    }

    private record ServingOptionSyncContext(
            Long productId,
            List<ServingOptionImport> definitions
    ) {
    }

    private record ServingOptionImport(
            String label,
            String unitType,
            Double quantity,
            Double gramWeight,
            Double mlVolume,
            Boolean defaultOption,
            Map<String, String> labels
    ) {
    }

    private record ServingOptionsParseResult(List<ServingOptionImport> options, boolean valid) {
    }
    private record ProductDisplayNames(String displayName, String shortDisplayName) {}

    private record RowResult(FoodItemEntity product, boolean inserted, FoodProductImportErrorDto error) {
        private static RowResult error(FoodProductImportErrorDto error) {
            return new RowResult(null, false, error);
        }
    }

    private record RegionResolution(MarketRegion region, boolean missing, boolean unsupported) {
    }

    private record ProductImportContext(FoodItemEntity product, RegionResolution regionResolution, CsvRow row, boolean evidenceBasisKnown) {
    }
}



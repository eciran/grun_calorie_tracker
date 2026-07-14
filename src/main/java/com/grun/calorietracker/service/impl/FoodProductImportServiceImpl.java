package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules;
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
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.FoodPreparationState;
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
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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
public class FoodProductImportServiceImpl implements FoodProductImportService {

    private static final int MAX_ERROR_DETAILS = 50;
    private static final int MAX_WARNING_DETAILS = 50;

    private final FoodItemRepository foodItemRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;
    private final FoodProductEvidenceService foodProductEvidenceService;
    private final ObjectMapper objectMapper;

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy) {
        return importCsv(file, importedBy, FoodProductImportMode.CURATED_ADMIN, FoodProductImportFormat.AUTO);
    }

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode) {
        return importCsv(file, importedBy, importMode, FoodProductImportFormat.AUTO);
    }

    @Override
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode, FoodProductImportFormat importFormat) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }

        ParsedCsv parsedCsv = parseCsv(file, normalizeImportFormat(importFormat));
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
            String inputSourceKey = resolveInputKey(row, regionResolution.region());
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
            productImportContexts.add(new ProductImportContext(rowResult.product(), regionResolution, row));
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
        foodProductEvidenceService.recordImportEvidence(
                savedProducts,
                FoodEvidenceBasis.PER_100_G,
                LocalDateTime.now(),
                parsedCsv.sourceFormat().name(),
                importedBy
        );
        syncQualityIssues(savedProducts, productImportContexts, importedBy);
        syncSearchAliases(savedProducts, productImportContexts);
        syncLocalizations(savedProducts, productImportContexts);
        syncServingOptions(savedProducts, productImportContexts);
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
                String sourceKey = resolveInputKey(row, resolveMarketRegion(row, null).region());
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
        String name = FoodProductNormalizationRules.normalizeProductDisplayName(rawName);
        if (name == null) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), firstText(row, "barcode", "code", "fdc_id"), "Product name is required."));
        }

        FoodCatalogType catalogType = resolveCatalogType(row, normalizedBarcode);
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
        if (!inserted && importMode == FoodProductImportMode.RAW_EXTERNAL && isCuratedProduct(product)) {
            return new RowResult(product, false, null);
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
        ProductDisplayNames displayNames = resolveProductDisplayNames(name, catalogType, preparationState, row, sourceFormat);
        product.setDisplayName(displayNames.displayName());
        product.setShortDisplayName(displayNames.shortDisplayName());
        product.setCanonicalFoodKey(resolveCanonicalFoodKey(
                catalogType,
                regionResolution.region(),
                preparationState,
                displayNames.displayName()
        ));
        String brand = firstText(row, "brand", "brands", "manufacturer", "producer");
        if (brand != null) {
            product.setBrand(FoodProductNormalizationRules.normalizeBrandDisplayName(brand));
        }
        product.setCatalogType(catalogType);
        product.setPreparationState(preparationState);
        product.setMarketRegion(regionResolution.region());
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
        }

        updateQualityMetadata(product, importMode);
        return new RowResult(product, inserted, null);
    }

    private ProductDisplayNames resolveProductDisplayNames(
            String sourceName,
            FoodCatalogType catalogType,
            FoodPreparationState preparationState,
            CsvRow row,
            FoodProductImportFormat sourceFormat
    ) {
        String explicitDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, "display_name", "displayname"));
        String explicitShortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, "short_display_name", "shortdisplayname"));
        if (explicitDisplayName != null || explicitShortDisplayName != null) {
            String displayName = explicitDisplayName != null ? explicitDisplayName : sourceName;
            String shortDisplayName = explicitShortDisplayName != null ? explicitShortDisplayName : displayName;
            return new ProductDisplayNames(displayName, shortDisplayName);
        }

        boolean usdaGeneric = sourceFormat == FoodProductImportFormat.USDA_FOODDATA
                || firstText(row, "fdc_id", "fdcid", "fdc") != null;
        if (usdaGeneric && catalogType == FoodCatalogType.GENERIC_INGREDIENT) {
            return buildUsdaGenericDisplayNames(sourceName, preparationState);
        }

        String displayName = FoodProductNormalizationRules.normalizeProductDisplayName(sourceName);
        return new ProductDisplayNames(displayName, displayName);
    }

    private ProductDisplayNames buildUsdaGenericDisplayNames(String sourceName, FoodPreparationState preparationState) {
        List<String> parts = List.of(sourceName.split(","));
        String base = parts.isEmpty() ? sourceName : parts.get(0).trim();
        StringBuilder descriptors = new StringBuilder();
        for (int index = 1; index < parts.size(); index++) {
            String descriptor = parts.get(index).trim().toLowerCase(Locale.ROOT);
            if (descriptor.isBlank() || isUsdaDescriptorNoise(descriptor) || isPreparationDescriptor(descriptor)) {
                continue;
            }
            if (!descriptors.isEmpty()) {
                descriptors.append(' ');
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
            return normalizedDescriptors;
        }
        if (normalizedBase.equalsIgnoreCase("flour")) {
            return normalizedDescriptors + " flour";
        }
        return normalizedDescriptors + " " + normalizedBase;
    }
    private boolean isUsdaDescriptorNoise(String descriptor) {
        return descriptor.contains("broiler or fryers")
                || descriptor.contains("separable lean")
                || descriptor.contains("trimmed to")
                || descriptor.contains("choice")
                || descriptor.contains("select")
                || descriptor.contains("includes")
                || descriptor.contains("drained solids")
                || descriptor.contains("meat only")
                || descriptor.contains("boneless")
                || descriptor.contains("skinless")
                || descriptor.contains("unprepared")
                || descriptor.contains("slightly ripe")
                || descriptor.contains("overripe")
                || descriptor.contains("dry heat")
                || descriptor.contains("moist heat")
                || descriptor.contains("cooked as purchased")
                || descriptor.contains("unheated");
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
        FoodCatalogType catalogType = resolveCatalogType(row, normalizedBarcode);
        FoodPreparationState preparationState = resolvePreparationState(row);
        return resolveSourceKey(row, normalizedBarcode, name, catalogType, marketRegion, preparationState);
    }

    private FoodCatalogType resolveCatalogType(CsvRow row, String normalizedBarcode) {
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
            } catch (IllegalArgumentException ignored) {
                return FoodCatalogType.BRANDED_PRODUCT;
            }
        }
        String fdcId = firstText(row, "fdc_id", "fdcid", "fdc");
        if (fdcId != null && normalizedBarcode == null) {
            return FoodCatalogType.GENERIC_INGREDIENT;
        }
        return FoodCatalogType.BRANDED_PRODUCT;
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
    private String resolveCanonicalFoodKey(
            FoodCatalogType catalogType,
            MarketRegion marketRegion,
            FoodPreparationState preparationState,
            String displayName
    ) {
        return FoodProductCanonicalKeyRules.resolve(catalogType, marketRegion, preparationState, displayName);
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
            FoodDataSource dataSource = resolveDataSource(row, defaultDataSource(importMode, sourceFormat));
            product.setDataSource(dataSource);
            product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
            product.setImageSource(dataSource == FoodDataSource.OPEN_FOOD_FACTS ? ImageSource.OPEN_FOOD_FACTS : ImageSource.ADMIN_UPLOAD);
            product.setImageStatus(resolvePassiveImageStatus(row));
            product.setReviewedBy(null);
            product.setLastReviewedAt(null);
            copyExternalImageIfMissing(product, row);
            return;
        }

        product.setDataSource(resolveDataSource(row, defaultDataSource(importMode, sourceFormat)));
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
        if (regionResolution.missing()) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_REGION", "Product has no explicit market region and was imported as GLOBAL.");
        }
        if (regionResolution.unsupported()) {
            addWarning(warningCounts, warnings, row, identifier, "UNSUPPORTED_REGION", "Product has an unsupported market region and was imported with fallback region.");
        }
        if (product.getCalories() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_CALORIES", "Product has no calories value.");
        }
        if (product.getProtein() == null && product.getFat() == null && product.getCarbs() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_MACROS", "Product has no protein, fat, or carbohydrate values.");
        }
        if (product.getServingSizeGrams() == null) {
            addWarning(warningCounts, warnings, row, identifier, "MISSING_SERVING_SIZE", "Product has no serving size value.");
        }
        String servingOptionsJson = firstText(row, "serving_options_json", "servingoptionsjson", "portion_options_json");
        if (servingOptionsJson != null && !parseServingOptions(row).valid()) {
            addWarning(warningCounts, warnings, row, identifier, "INVALID_SERVING_OPTIONS",
                    "Serving options JSON is invalid, duplicated, has multiple defaults, or lacks a positive gram/ml conversion.");
        }
        if (product.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT
                && (product.getPreparationState() == null || product.getPreparationState() == FoodPreparationState.UNSPECIFIED)) {
            addWarning(warningCounts, warnings, row, identifier, "GENERIC_MISSING_PREPARATION_STATE", "Generic ingredient has no preparation state. Raw/cooked/prepared differences must be explicit.");
        }
        if (hasSuspiciousUserFacingName(product)) {
            addWarning(warningCounts, warnings, row, identifier, "SUSPICIOUS_DISPLAY_NAME", "Product display name looks too long, source-like, or unsuitable for mobile search results.");
        }
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
                && (userFacingName.contains(",") || userFacingName.contains(";"))) {
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
        for (String candidate : rawAliases.split("[|;,]")) {
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

        String displayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, displayNameColumns));
        String shortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(firstText(row, shortDisplayNameColumns));
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
        localization.setSource("admin-import");
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
            List<ProductImportContext> productImportContexts
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

            ServingOptionsParseResult parsed = parseServingOptions(context.row());
            if (!parsed.valid() || parsed.options().isEmpty()) {
                continue;
            }

            List<FoodItemServingOptionEntity> existingOptions =
                    existingOptionsByProductId.getOrDefault(product.getId(), List.of());
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

            optionsToSave.addAll(optionsByLabel.values());
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
    private ServingOptionsParseResult parseServingOptions(CsvRow row) {
        String rawJson = firstText(row, "serving_options_json", "servingoptionsjson", "portion_options_json");
        if (rawJson == null) {
            return new ServingOptionsParseResult(List.of(), true);
        }

        try {
            List<ServingOptionImport> options = objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<ServingOptionImport>>() {}
            );
            if (options == null || options.isEmpty() || options.size() > 20) {
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
                if (label == null || label.length() > 120 || unit == null
                        || !Double.isFinite(quantity) || quantity <= 0 || !hasWeight
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
            if (defaultCount > 1) {
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
                return Double.parseDouble(value.replace(',', '.'));
            } catch (NumberFormatException ignored) {
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

    private RegionResolution resolveMarketRegion(CsvRow row, MarketRegion fallback) {
        String value = firstText(row, "marketregion", "market_region", "region", "country");
        if (value == null) {
            return new RegionResolution(fallback == null ? MarketRegion.GLOBAL : fallback, true, false);
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
        } else if ("TURKEY".equals(normalized) || "TURKIYE".equals(normalized)) {
            normalized = "TR";
        } else if ("EUROPE".equals(normalized) || "EUROPEAN UNION".equals(normalized)) {
            normalized = "EU";
        }

        try {
            return new RegionResolution(MarketRegion.valueOf(normalized), false, false);
        } catch (IllegalArgumentException ex) {
            return new RegionResolution(fallback == null ? MarketRegion.GLOBAL : fallback, false, true);
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
            return NutritionValueNormalizer.servingSize(directValue);
        }

        Double servingQuantity = parseDouble(null, row, "serving_quantity");
        if (servingQuantity != null) {
            String unit = resolveServingUnit(row);
            if (unit == null || "g".equalsIgnoreCase(unit) || "ml".equalsIgnoreCase(unit)) {
                return NutritionValueNormalizer.servingSize(servingQuantity);
            }
        }

        String servingSize = firstText(row, "serving_size");
        if (servingSize == null) {
            return fallback;
        }

        String[] parts = servingSize.replace(',', '.').replaceAll("[^0-9.]+", " ").trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return fallback;
        }
        try {
            return NutritionValueNormalizer.servingSize(Double.parseDouble(parts[0]));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private record ProductImportContext(FoodItemEntity product, RegionResolution regionResolution, CsvRow row) {
    }
}



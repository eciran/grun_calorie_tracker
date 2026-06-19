package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductImportErrorDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodProductImportWarningDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodProductImportService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;
import lombok.RequiredArgsConstructor;
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
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy) {
        return importCsv(file, importedBy, FoodProductImportMode.CURATED_ADMIN, FoodProductImportFormat.AUTO);
    }

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode) {
        return importCsv(file, importedBy, importMode, FoodProductImportFormat.AUTO);
    }

    @Override
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
            if (inputSourceKey != null && !seenInputKeys.add(inputSourceKey)) {
                duplicateInputRows++;
                addWarning(qualityWarningCounts, warnings, row, inputSourceKey, "DUPLICATE_INPUT_KEY", "Input file contains duplicate barcode or source key. Later row may overwrite earlier mapped values.");
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

            if (regionResolution.missing()) {
                missingMarketRegionRows++;
            }
            if (regionResolution.unsupported()) {
                unsupportedMarketRegionRows++;
            }
            productsToSave.add(rowResult.product());
            productImportContexts.add(new ProductImportContext(rowResult.product(), regionResolution));
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

        List<FoodItemEntity> savedProducts = new ArrayList<>();
        foodItemRepository.saveAll(productsToSave).forEach(savedProducts::add);
        syncQualityIssues(savedProducts, productImportContexts, importedBy);
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
        if (!normalizedBarcodes.isEmpty()) {
            List<FoodItemEntity> products = foodItemRepository.findByNormalizedBarcodeIn(
                    normalizedBarcodes,
                    Sort.by(Sort.Order.asc("id"))
            );
            if (products != null) {
                products.forEach(product -> registerExistingProduct(byKey, product));
            }
        }
        if (!sourceKeys.isEmpty()) {
            List<FoodItemEntity> products = foodItemRepository.findBySourceKeyIn(
                    sourceKeys,
                    Sort.by(Sort.Order.asc("id"))
            );
            if (products != null) {
                products.forEach(product -> registerExistingProduct(byKey, product));
            }
        }
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
        String sourceKey = resolveSourceKey(row, normalizedBarcode, name, catalogType, regionResolution.region());
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
        String brand = firstText(row, "brand", "brands", "manufacturer", "producer");
        if (brand != null) {
            product.setBrand(FoodProductNormalizationRules.normalizeBrandDisplayName(brand));
        }
        product.setCatalogType(catalogType);
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
        return resolveSourceKey(row, normalizedBarcode, name, catalogType, marketRegion);
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
        return FoodCatalogType.BRANDED_PRODUCT;
    }

    private String resolveSourceKey(
            CsvRow row,
            String normalizedBarcode,
            String name,
            FoodCatalogType catalogType,
            MarketRegion marketRegion
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
        return effectiveRegion.name() + ":" + catalogType.name() + ":" + slug(name);
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
        String rawBarcode = firstText(row, "barcode", "code", "gtin", "ean", "upc");
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(rawBarcode);
        if (normalizedBarcode != null && !normalizedBarcode.matches("\\d{6,18}")) {
            addWarning(warningCounts, warnings, row, identifier, "INVALID_BARCODE_FORMAT", "Product barcode is not a numeric value between 6 and 18 digits.");
        }
    }

    private void syncQualityIssues(
            List<FoodItemEntity> savedProducts,
            List<ProductImportContext> productImportContexts,
            String importedBy
    ) {
        if (productImportContexts.isEmpty()) {
            return;
        }

        for (int index = 0; index < productImportContexts.size(); index++) {
            ProductImportContext context = productImportContexts.get(index);
            FoodItemEntity product = index < savedProducts.size() ? savedProducts.get(index) : context.product();
            foodProductQualityIssueTracker.syncImportIssues(
                    product,
                    context.regionResolution().missing(),
                    context.regionResolution().unsupported(),
                    importedBy
            );
        }
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

    private record RowResult(FoodItemEntity product, boolean inserted, FoodProductImportErrorDto error) {
        private static RowResult error(FoodProductImportErrorDto error) {
            return new RowResult(null, false, error);
        }
    }

    private record RegionResolution(MarketRegion region, boolean missing, boolean unsupported) {
    }

    private record ProductImportContext(FoodItemEntity product, RegionResolution regionResolution) {
    }
}

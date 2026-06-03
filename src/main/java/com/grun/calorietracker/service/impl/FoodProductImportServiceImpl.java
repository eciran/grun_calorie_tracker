package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductImportErrorDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodProductImportService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
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

    private final FoodItemRepository foodItemRepository;

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy) {
        return importCsv(file, importedBy, FoodProductImportMode.CURATED_ADMIN);
    }

    @Override
    public FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }

        ParsedCsv parsedCsv = parseCsv(file);
        Map<String, FoodItemEntity> existingProducts = loadExistingProducts(
                parsedCsv.normalizedBarcodes(),
                parsedCsv.sourceKeys()
        );

        List<FoodItemEntity> productsToSave = new ArrayList<>();
        List<FoodProductImportErrorDto> errors = new ArrayList<>();
        Set<String> seenInputKeys = new HashSet<>();
        int insertedRows = 0;
        int updatedRows = 0;
        int duplicateInputRows = 0;
        int missingMarketRegionRows = 0;
        int unsupportedMarketRegionRows = 0;
        Map<String, Integer> marketRegionCounts = new LinkedHashMap<>();

        for (CsvRow row : parsedCsv.rows()) {
            RegionResolution regionResolution = resolveMarketRegion(row, null);
            String inputSourceKey = resolveInputKey(row, regionResolution.region());
            if (inputSourceKey != null && !seenInputKeys.add(inputSourceKey)) {
                duplicateInputRows++;
            }
            FoodItemEntity existingProduct = existingProducts.get(inputSourceKey);
            if (existingProduct != null) {
                regionResolution = resolveMarketRegion(row, existingProduct.getMarketRegion());
            }
            RowResult rowResult = mapRow(row, existingProducts, importedBy, normalizeImportMode(importMode), regionResolution);
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
            String regionKey = rowResult.product().getMarketRegion() == null ? "UNSPECIFIED" : rowResult.product().getMarketRegion().name();
            marketRegionCounts.merge(regionKey, 1, Integer::sum);
            registerExistingProduct(existingProducts, rowResult.product());
            if (rowResult.inserted()) {
                insertedRows++;
            } else {
                updatedRows++;
            }
        }

        foodItemRepository.saveAll(productsToSave);
        int skippedRows = parsedCsv.rows().size() - productsToSave.size();
        int reviewRequiredRows = (int) productsToSave.stream()
                .filter(this::requiresReview)
                .count();

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
                parsedCsv.format(),
                errors
        );
    }

    private ParsedCsv parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV header row is required.");
            }

            char delimiter = detectDelimiter(headerLine);
            Map<String, Integer> headers = indexHeaders(parseDelimitedLine(headerLine, delimiter));
            requireAnyHeader(headers, "name", "productname", "product_name");

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
                String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(row.value("barcode"));
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
                    delimiter == '\t' ? "TSV" : "CSV"
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
            RegionResolution regionResolution
    ) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(row.value("barcode"));
        String name = firstText(row, "name", "productname", "product_name");
        if (name == null) {
            return RowResult.error(new FoodProductImportErrorDto(row.rowNumber(), row.value("barcode"), "Product name is required."));
        }

        FoodCatalogType catalogType = resolveCatalogType(row, normalizedBarcode);
        String sourceKey = resolveSourceKey(row, normalizedBarcode, name, catalogType, regionResolution.region());
        if (sourceKey == null) {
            return RowResult.error(new FoodProductImportErrorDto(
                    row.rowNumber(),
                    row.value("barcode"),
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
        product.setCatalogType(catalogType);
        product.setMarketRegion(regionResolution.region());
        applyImportMetadata(product, row, importedBy, importMode);

        setIfPresent(row, "imageurl", product::setImageUrl);
        setIfPresent(row, "image_url", product::setImageUrl);
        setIfPresent(row, "externalimageurl", product::setExternalImageUrl);
        setIfPresent(row, "external_image_url", product::setExternalImageUrl);
        if (importMode == FoodProductImportMode.CURATED_ADMIN) {
            setIfPresent(row, "displayimageurl", product::setDisplayImageUrl);
            setIfPresent(row, "display_image_url", product::setDisplayImageUrl);
        }
        setIfPresent(row, "allergens", product::setAllergens);
        setIfPresent(row, "nutriscore", product::setNutriScore);
        setIfPresent(row, "nutri_score", product::setNutriScore);
        setIfPresent(row, "servingunit", product::setServingUnit);
        setIfPresent(row, "serving_unit", product::setServingUnit);

        product.setCalories(parseDouble(row, "calories", product.getCalories()));
        product.setProtein(parseDouble(row, "protein", product.getProtein()));
        product.setFat(parseDouble(row, "fat", product.getFat()));
        product.setCarbs(parseDouble(row, "carbs", product.getCarbs()));
        product.setFiber(parseDouble(row, "fiber", product.getFiber()));
        product.setSugar(parseDouble(row, "sugar", product.getSugar()));
        product.setSodium(parseDouble(row, "sodium", product.getSodium()));
        product.setServingSizeGrams(parseDouble(row, "servingsizegrams", product.getServingSizeGrams()));
        product.setServingSizeGrams(parseDouble(row, "serving_size_grams", product.getServingSizeGrams()));

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
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(row.value("barcode"));
        String name = firstText(row, "name", "productname", "product_name");
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
            FoodProductImportMode importMode
    ) {
        if (importMode == FoodProductImportMode.RAW_EXTERNAL) {
            product.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
            product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
            product.setImageSource(ImageSource.OPEN_FOOD_FACTS);
            product.setImageStatus(ImageStatus.NEEDS_REVIEW);
            product.setReviewedBy(null);
            product.setLastReviewedAt(null);
            copyExternalImageIfMissing(product, row);
            return;
        }

        product.setDataSource(FoodDataSource.ADMIN_IMPORT);
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
        return product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED
                || product.getVerificationStatus() == VerificationStatus.NEEDS_REVIEW
                || product.getImageStatus() == ImageStatus.NEEDS_REVIEW
                || product.getImageStatus() == ImageStatus.RAW;
    }

    private void setIfPresent(CsvRow row, String column, Consumer<String> setter) {
        String value = FoodProductNormalizationRules.normalizeText(row.value(column));
        if (value != null) {
            setter.accept(value);
        }
    }

    private Double parseDouble(CsvRow row, String column, Double fallback) {
        String value = FoodProductNormalizationRules.normalizeText(row.value(column));
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
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

    private record ParsedCsv(List<CsvRow> rows, List<String> normalizedBarcodes, List<String> sourceKeys, String format) {
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
}

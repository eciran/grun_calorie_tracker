package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseSummaryDto;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.service.support.BrandedDuplicateFingerprint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BrandedProductDuplicateAnalysisService {

    private static final String IDENTITY_CTE = """
            WITH branded_identity AS (
                SELECT item.id,
                       coalesce(brand.canonical_name, nullif(trim(item.brand), ''), '<missing-brand>') brand_name,
                       coalesce('brand:' || item.brand_id::text,
                                'legacy:' || regexp_replace(lower(coalesce(item.brand, '')), '[^[:alnum:]]+', '', 'g')) brand_key,
                       coalesce(item.display_name, item.name) product_name,
                       regexp_replace(lower(coalesce(item.display_name, item.name, '')), '[^[:alnum:]]+', '', 'g') name_key,
                       nullif(trim(item.normalized_barcode), '') barcode,
                       nullif(trim(item.source_key), '') source_key,
                       item.market_region, item.preparation_state,
                       item.serving_size_grams, item.serving_unit,
                       item.calories, item.protein, item.carbs, item.fat,
                       item.quality_score, item.verification_status, item.data_source
                FROM food_items item
                LEFT JOIN food_brands brand ON brand.id = item.brand_id
                WHERE item.catalog_type = 'BRANDED_PRODUCT'
                  AND coalesce(item.is_custom, false) = false
                  AND item.publication_status = 'PUBLISHED'
                  AND coalesce(item.display_name, item.name) IS NOT NULL
            ), duplicate_groups AS (
                SELECT brand_key, name_key,
                       min(brand_name) brand_name,
                       min(product_name) representative_name,
                       count(*) product_count,
                       count(DISTINCT barcode) FILTER (WHERE barcode IS NOT NULL) barcode_count,
                       count(*) FILTER (WHERE barcode IS NULL) missing_barcode_count,
                       count(DISTINCT source_key) FILTER (WHERE source_key IS NOT NULL) source_key_count,
                       count(DISTINCT market_region) market_count,
                       count(DISTINCT preparation_state) FILTER (WHERE preparation_state IS NOT NULL) preparation_count,
                       count(DISTINCT concat_ws('|', serving_size_grams::text, serving_unit)) serving_count,
                       count(*) FILTER (WHERE serving_size_grams IS NULL OR serving_unit IS NULL) missing_serving_count,
                       count(DISTINCT concat_ws('|', round(calories::numeric, 1), round(protein::numeric, 1),
                                                round(carbs::numeric, 1), round(fat::numeric, 1))) nutrition_count,
                       count(*) FILTER (WHERE calories IS NULL OR protein IS NULL OR carbs IS NULL OR fat IS NULL) missing_nutrition_count
                FROM branded_identity
                WHERE name_key <> '' AND brand_key NOT IN ('legacy:', 'legacy:<missingbrand>')
                GROUP BY brand_key, name_key
                HAVING count(*) > 1
            ), classified AS (
                SELECT duplicate_groups.*,
                       CASE
                         WHEN barcode_count = 1 AND missing_barcode_count = 0 THEN 'AUTO_SAFE_SAME_GTIN'
                         WHEN source_key_count = 1 AND missing_barcode_count = product_count THEN 'AUTO_SAFE_SAME_SOURCE_KEY'
                         WHEN barcode_count > 1 THEN 'REVIEW_DIFFERENT_GTIN'
                         WHEN missing_barcode_count > 0 THEN 'REVIEW_MISSING_GTIN'
                         ELSE 'REVIEW_IDENTITY'
                       END decision,
                       (market_count > 1 OR preparation_count > 1 OR serving_count > 1 OR nutrition_count > 1) variant_signal
                FROM duplicate_groups
            )
            """;

    private static final String FILTERED_CTE = """
            , filtered_groups AS (
                SELECT * FROM classified
                WHERE (CAST(? AS text) IS NULL OR lower(brand_name) LIKE CAST(? AS text)
                       OR lower(representative_name) LIKE CAST(? AS text))
                  AND (CAST(? AS boolean) IS NULL OR variant_signal = CAST(? AS boolean))
            )
            """;

    private static final String COUNT_SQL = IDENTITY_CTE + FILTERED_CTE +
            "SELECT count(*) FROM filtered_groups";

    private static final String PAGE_SQL = IDENTITY_CTE + FILTERED_CTE + """
            , paged_groups AS (
                SELECT *
                FROM filtered_groups
                ORDER BY variant_signal ASC, missing_serving_count ASC, missing_nutrition_count ASC,
                         product_count DESC, brand_name, representative_name
                LIMIT ? OFFSET ?
            )
            SELECT group_data.*, item.id, item.product_name, item.barcode, item.source_key,
                   item.market_region::text, item.preparation_state::text,
                   item.serving_size_grams, item.serving_unit,
                   item.calories, item.protein, item.carbs, item.fat,
                   item.quality_score, item.verification_status::text, item.data_source::text,
                   resolution.id resolution_id, resolution.decision::text resolution_decision,
                   resolution.survivor_food_item_id resolution_survivor_id,
                   resolution.candidate_fingerprint resolution_fingerprint,
                   resolution.reason resolution_reason, resolution.reviewed_by resolution_reviewed_by,
                   resolution.reviewed_at resolution_reviewed_at, resolution.version resolution_version,
                   collapse_state.id collapse_id, collapse_state.survivor_food_item_id collapse_survivor_id,
                   collapse_state.active collapse_active,
                   collapse_state.candidate_fingerprint collapse_fingerprint,
                   collapse_state.applied_by collapse_applied_by, collapse_state.applied_at collapse_applied_at,
                   collapse_state.reverted_by collapse_reverted_by, collapse_state.reverted_at collapse_reverted_at
            FROM paged_groups group_data
            JOIN branded_identity item
              ON item.brand_key = group_data.brand_key AND item.name_key = group_data.name_key
            LEFT JOIN food_branded_duplicate_decisions resolution
              ON resolution.brand_key = group_data.brand_key AND resolution.name_key = group_data.name_key
            LEFT JOIN food_branded_duplicate_search_collapses collapse_state
              ON collapse_state.decision_id = resolution.id
            ORDER BY group_data.variant_signal ASC, group_data.missing_serving_count ASC,
                     group_data.missing_nutrition_count ASC, group_data.product_count DESC,
                     group_data.brand_name, group_data.representative_name,
                     item.quality_score DESC NULLS LAST, item.id
            """;

    private static final String EXACT_GROUP_SQL = IDENTITY_CTE + """
            SELECT group_data.*, item.id, item.product_name, item.barcode, item.source_key,
                   item.market_region::text, item.preparation_state::text,
                   item.serving_size_grams, item.serving_unit,
                   item.calories, item.protein, item.carbs, item.fat,
                   item.quality_score, item.verification_status::text, item.data_source::text,
                   resolution.id resolution_id, resolution.decision::text resolution_decision,
                   resolution.survivor_food_item_id resolution_survivor_id,
                   resolution.candidate_fingerprint resolution_fingerprint,
                   resolution.reason resolution_reason, resolution.reviewed_by resolution_reviewed_by,
                   resolution.reviewed_at resolution_reviewed_at, resolution.version resolution_version,
                   collapse_state.id collapse_id, collapse_state.survivor_food_item_id collapse_survivor_id,
                   collapse_state.active collapse_active,
                   collapse_state.candidate_fingerprint collapse_fingerprint,
                   collapse_state.applied_by collapse_applied_by, collapse_state.applied_at collapse_applied_at,
                   collapse_state.reverted_by collapse_reverted_by, collapse_state.reverted_at collapse_reverted_at
            FROM classified group_data
            JOIN branded_identity item
              ON item.brand_key = group_data.brand_key AND item.name_key = group_data.name_key
            LEFT JOIN food_branded_duplicate_decisions resolution
              ON resolution.brand_key = group_data.brand_key AND resolution.name_key = group_data.name_key
            LEFT JOIN food_branded_duplicate_search_collapses collapse_state
              ON collapse_state.decision_id = resolution.id
            WHERE group_data.brand_key = ? AND group_data.name_key = ?
            ORDER BY item.quality_score DESC NULLS LAST, item.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public BrandedProductDuplicateAnalysisService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public BrandedProductDuplicateGroupPageDto getCandidates(
            int requestedPage,
            int requestedSize,
            String requestedQuery,
            Boolean variantSignal
    ) {
        int page = Math.max(requestedPage, 0);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        String query = requestedQuery == null || requestedQuery.isBlank()
                ? null
                : "%" + requestedQuery.trim().toLowerCase(Locale.ROOT) + "%";
        long total = jdbcTemplate.queryForObject(
                COUNT_SQL,
                Long.class,
                query, query, query, variantSignal, variantSignal
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        jdbcTemplate.query(PAGE_SQL, resultSet -> {
            String brandKey = resultSet.getString("brand_key");
            String nameKey = resultSet.getString("name_key");
            String key = brandKey + "\u0000" + nameKey;
            GroupBuilder group = groups.get(key);
            if (group == null) {
                group = new GroupBuilder(
                        brandKey,
                        nameKey,
                        resultSet.getString("brand_name"),
                        resultSet.getString("representative_name"),
                        resultSet.getInt("product_count"),
                        resultSet.getInt("barcode_count"),
                        resultSet.getInt("missing_barcode_count"),
                        resultSet.getInt("market_count"),
                        resultSet.getInt("preparation_count"),
                        resultSet.getInt("serving_count"),
                        resultSet.getInt("missing_serving_count"),
                        resultSet.getInt("nutrition_count"),
                        resultSet.getInt("missing_nutrition_count"),
                        resultSet.getString("decision"),
                        resultSet.getBoolean("variant_signal"),
                        resolutionFromRow(resultSet, brandKey, nameKey),
                        collapseFromRow(resultSet)
                );
                groups.put(key, group);
            }
            group.candidates.add(new BrandedProductDuplicateCandidateDto(
                    resultSet.getLong("id"),
                    resultSet.getString("product_name"),
                    resultSet.getString("barcode"),
                    resultSet.getString("source_key"),
                    resultSet.getString("market_region"),
                    resultSet.getString("preparation_state"),
                    nullableDouble(resultSet, "serving_size_grams"),
                    resultSet.getString("serving_unit"),
                    nullableDouble(resultSet, "calories"),
                    nullableDouble(resultSet, "protein"),
                    nullableDouble(resultSet, "carbs"),
                    nullableDouble(resultSet, "fat"),
                    nullableInteger(resultSet, "quality_score"),
                    resultSet.getString("verification_status"),
                    resultSet.getString("data_source")
            ));
        }, query, query, query, variantSignal, variantSignal, size, (long) page * size);

        List<BrandedProductDuplicateGroupDto> content = groups.values().stream().map(GroupBuilder::build).toList();
        return new BrandedProductDuplicateGroupPageDto(
                content, page, size, total, totalPages, page == 0, page >= totalPages - 1
        );
    }

    @Transactional(readOnly = true)
    public BrandedProductDuplicateGroupDto getExactGroup(String brandKey, String nameKey) {
        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        collectGroups(EXACT_GROUP_SQL, groups, brandKey, nameKey);
        return groups.values().stream()
                .findFirst()
                .map(GroupBuilder::build)
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate candidate group was not found."));
    }

    private void collectGroups(String sql, Map<String, GroupBuilder> groups, Object... arguments) {
        RowCallbackHandler handler = resultSet -> addCandidateRow(groups, resultSet);
        jdbcTemplate.query(sql, handler, arguments);
    }

    private void addCandidateRow(Map<String, GroupBuilder> groups, java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        String brandKey = resultSet.getString("brand_key");
        String nameKey = resultSet.getString("name_key");
        String key = brandKey + "\u0000" + nameKey;
        GroupBuilder group = groups.get(key);
        if (group == null) {
            group = new GroupBuilder(
                    brandKey, nameKey, resultSet.getString("brand_name"),
                    resultSet.getString("representative_name"), resultSet.getInt("product_count"),
                    resultSet.getInt("barcode_count"), resultSet.getInt("missing_barcode_count"),
                    resultSet.getInt("market_count"), resultSet.getInt("preparation_count"),
                    resultSet.getInt("serving_count"), resultSet.getInt("missing_serving_count"),
                    resultSet.getInt("nutrition_count"), resultSet.getInt("missing_nutrition_count"),
                    resultSet.getString("decision"), resultSet.getBoolean("variant_signal"),
                    resolutionFromRow(resultSet, brandKey, nameKey),
                    collapseFromRow(resultSet)
            );
            groups.put(key, group);
        }
        group.candidates.add(new BrandedProductDuplicateCandidateDto(
                resultSet.getLong("id"), resultSet.getString("product_name"),
                resultSet.getString("barcode"), resultSet.getString("source_key"),
                resultSet.getString("market_region"), resultSet.getString("preparation_state"),
                nullableDouble(resultSet, "serving_size_grams"), resultSet.getString("serving_unit"),
                nullableDouble(resultSet, "calories"), nullableDouble(resultSet, "protein"),
                nullableDouble(resultSet, "carbs"), nullableDouble(resultSet, "fat"),
                nullableInteger(resultSet, "quality_score"), resultSet.getString("verification_status"),
                resultSet.getString("data_source")
        ));
    }

    private static Double nullableDouble(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static BrandedProductDuplicateDecisionDto resolutionFromRow(
            java.sql.ResultSet resultSet,
            String brandKey,
            String nameKey
    ) throws java.sql.SQLException {
        Long id = nullableLong(resultSet, "resolution_id");
        if (id == null) return null;
        return new BrandedProductDuplicateDecisionDto(
                id,
                brandKey,
                nameKey,
                com.grun.calorietracker.enums.BrandedDuplicateDecision.valueOf(
                        resultSet.getString("resolution_decision")
                ),
                nullableLong(resultSet, "resolution_survivor_id"),
                resultSet.getString("resolution_fingerprint"),
                resultSet.getString("resolution_reason"),
                resultSet.getString("resolution_reviewed_by"),
                resultSet.getTimestamp("resolution_reviewed_at").toLocalDateTime().toString(),
                nullableLong(resultSet, "resolution_version")
        );
    }

    private static BrandedDuplicateSearchCollapseSummaryDto collapseFromRow(
            java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {
        Long id = resultSet.getObject("collapse_id", Long.class);
        if (id == null) return null;
        return new BrandedDuplicateSearchCollapseSummaryDto(
                id,
                resultSet.getObject("collapse_survivor_id", Long.class),
                resultSet.getBoolean("collapse_active"),
                resultSet.getString("collapse_fingerprint"),
                resultSet.getString("collapse_applied_by"),
                resultSet.getTimestamp("collapse_applied_at").toLocalDateTime().toString(),
                resultSet.getString("collapse_reverted_by"),
                resultSet.getTimestamp("collapse_reverted_at") == null
                        ? null : resultSet.getTimestamp("collapse_reverted_at").toLocalDateTime().toString()
        );
    }

    private static final class GroupBuilder {
        private final String brandKey;
        private final String nameKey;
        private final String brandName;
        private final String representativeName;
        private final int productCount;
        private final int barcodeCount;
        private final int missingBarcodeCount;
        private final int marketCount;
        private final int preparationCount;
        private final int servingCount;
        private final int missingServingCount;
        private final int nutritionCount;
        private final int missingNutritionCount;
        private final String decision;
        private final boolean variantSignal;
        private final BrandedProductDuplicateDecisionDto storedDecision;
        private final BrandedDuplicateSearchCollapseSummaryDto searchCollapse;
        private final List<BrandedProductDuplicateCandidateDto> candidates = new ArrayList<>();

        private GroupBuilder(String brandKey, String nameKey, String brandName, String representativeName,
                             int productCount, int barcodeCount, int missingBarcodeCount, int marketCount,
                             int preparationCount, int servingCount, int missingServingCount,
                             int nutritionCount, int missingNutritionCount, String decision, boolean variantSignal,
                             BrandedProductDuplicateDecisionDto storedDecision,
                             BrandedDuplicateSearchCollapseSummaryDto searchCollapse) {
            this.brandKey = brandKey;
            this.nameKey = nameKey;
            this.brandName = brandName;
            this.representativeName = representativeName;
            this.productCount = productCount;
            this.barcodeCount = barcodeCount;
            this.missingBarcodeCount = missingBarcodeCount;
            this.marketCount = marketCount;
            this.preparationCount = preparationCount;
            this.servingCount = servingCount;
            this.missingServingCount = missingServingCount;
            this.nutritionCount = nutritionCount;
            this.missingNutritionCount = missingNutritionCount;
            this.decision = decision;
            this.variantSignal = variantSignal;
            this.storedDecision = storedDecision;
            this.searchCollapse = searchCollapse;
        }

        private BrandedProductDuplicateGroupDto build() {
            String fingerprint = BrandedDuplicateFingerprint.fromCandidates(candidates);
            return new BrandedProductDuplicateGroupDto(
                    brandKey, nameKey, brandName, representativeName, productCount, barcodeCount,
                    missingBarcodeCount, marketCount, preparationCount, servingCount, missingServingCount,
                    nutritionCount, missingNutritionCount, decision, variantSignal, fingerprint,
                    storedDecision,
                    storedDecision != null && !fingerprint.equals(storedDecision.candidateFingerprint()),
                    searchCollapse,
                    List.copyOf(candidates)
            );
        }
    }
}

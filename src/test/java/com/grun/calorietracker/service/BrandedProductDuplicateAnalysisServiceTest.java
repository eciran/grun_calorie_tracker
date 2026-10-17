package com.grun.calorietracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandedProductDuplicateAnalysisServiceTest {

    @Test
    void duplicateHintNeverApprovesVariantOrPartialIdentity() {
        String sql = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "IDENTITY_CTE");
        assertThat(sql).contains("item.nutrition_reference_unit", "CANDIDATE_SAME_GTIN",
                "REVIEW_VARIANT_CONFLICT", "REVIEW_INCOMPLETE_PROFILE", "REVIEW_MISSING_SOURCE_KEY");
        assertThat(sql).doesNotContain("AUTO_SAFE", "round(calories::numeric, 1)");
        assertThat(sql.indexOf("REVIEW_VARIANT_CONFLICT")).isLessThan(sql.indexOf("CANDIDATE_SAME_GTIN"));
    }

    @Test
    void fullReviewProfilesAreComputedOnlyForSelectedCandidates() {
        String profile = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "REVIEW_PROFILE_SQL");
        String identity = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "IDENTITY_CTE");
        String count = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "COUNT_SQL");
        String page = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "PAGE_SQL");
        String exact = (String) ReflectionTestUtils.getField(BrandedProductDuplicateAnalysisService.class, "EXACT_GROUP_SQL");
        assertThat(profile).contains("sha256", "to_jsonb(food)", "food_item_market_regions",
                "food_item_serving_options", "food_item_serving_option_localizations", "food_product_source_evidence",
                "- 'usage_count' - 'search_selection_count'", "to_jsonb(brand_row)", "food_item_source_categories");
        assertThat(identity).doesNotContain("review_profile_fingerprint", "food_product_source_evidence");
        assertThat(count).doesNotContain("review_profile_fingerprint", "food_item_serving_options");
        assertThat(page).contains(profile);
        assertThat(exact).contains(profile);
    }

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsEmptyPageAndNormalizesPaginationBounds() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class),
                eq("%wispa%"), eq("%wispa%"), eq("%wispa%"), eq(false), eq(false)
        )).thenReturn(0L);
        doNothing().when(jdbcTemplate).query(
                anyString(),
                any(RowCallbackHandler.class),
                eq("%wispa%"),
                eq("%wispa%"),
                eq("%wispa%"),
                eq(false),
                eq(false),
                eq(100),
                eq(0L)
        );

        BrandedProductDuplicateAnalysisService service =
                new BrandedProductDuplicateAnalysisService(jdbcTemplate);

        var result = service.getCandidates(-5, 500, "  WISPA  ", false);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }
}

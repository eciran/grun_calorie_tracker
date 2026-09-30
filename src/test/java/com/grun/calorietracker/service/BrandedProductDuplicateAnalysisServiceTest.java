package com.grun.calorietracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandedProductDuplicateAnalysisServiceTest {

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

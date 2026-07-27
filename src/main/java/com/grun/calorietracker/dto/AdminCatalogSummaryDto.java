package com.grun.calorietracker.dto;

import java.util.List;

public record AdminCatalogSummaryDto(
        CatalogTypeSummary food,
        CatalogTypeSummary recipes,
        CatalogTypeSummary exercises,
        List<SourceSummary> sources
) {
    public record CatalogTypeSummary(
            long total,
            long approved,
            long pendingReview,
            long missingMedia,
            long staleSource,
            long overdueReview
    ) {
    }

    public record SourceSummary(
            String source,
            long itemCount,
            long staleCount,
            long missingLicenseCount
    ) {
    }
}

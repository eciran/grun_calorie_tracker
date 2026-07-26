package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductContributionEntity;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodProductContributionRepository extends JpaRepository<FoodProductContributionEntity, Long> {
    Page<FoodProductContributionEntity> findBySubmittedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select contribution from FoodProductContributionEntity contribution " +
            "where (:status is null or contribution.status = :status) " +
            "and (:marketRegion is null or contribution.marketRegion = :marketRegion) " +
            "order by contribution.createdAt asc, contribution.id asc")
    Page<FoodProductContributionEntity> findForReview(
            @Param("status") FoodProductContributionStatus status,
            @Param("marketRegion") MarketRegion marketRegion,
            Pageable pageable
    );

    boolean existsBySubmittedByIdAndNormalizedBarcodeAndEvidenceChecksum(Long userId, String normalizedBarcode, String evidenceChecksum);

    boolean existsByNormalizedBarcodeAndStatusAndIdNot(String normalizedBarcode, FoodProductContributionStatus status, Long id);

    List<FoodProductContributionEntity> findByStatusAndMarketRegionOrderByNormalizedBarcodeAsc(
            FoodProductContributionStatus status,
            MarketRegion marketRegion
    );
}

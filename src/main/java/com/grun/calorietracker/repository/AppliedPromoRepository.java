package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.PromoRedemptionStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppliedPromoRepository extends JpaRepository<AppliedPromoEntity, Long> {
    long deleteByUser(UserEntity user);
    Optional<AppliedPromoEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select count(redemption) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId)")
    long countForPromo(@Param("promoId") Long promoId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId) and redemption.status = :status")
    long countForPromoAndStatus(@Param("promoId") Long promoId, @Param("status") PromoRedemptionStatus status);

    @Query("select count(distinct redemption.user.id) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId)")
    long countUniqueUsers(@Param("promoId") Long promoId);

    @Query("""
            select coalesce(redemption.currency, 'UNKNOWN') as currency,
                   coalesce(sum(redemption.amountMinor), 0) as amountMinor
            from AppliedPromoEntity redemption
            where (:promoId is null or redemption.promoCode.id = :promoId)
              and redemption.status = com.grun.calorietracker.enums.PromoRedemptionStatus.CONVERTED
            group by coalesce(redemption.currency, 'UNKNOWN')
            order by coalesce(redemption.currency, 'UNKNOWN')
            """)
    List<CurrencyRevenueProjection> sumConvertedRevenueByCurrency(@Param("promoId") Long promoId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where redemption.user.id = :userId and redemption.promoCode.id = :promoId and redemption.status = com.grun.calorietracker.enums.PromoRedemptionStatus.CONVERTED")
    long countConvertedForUser(@Param("userId") Long userId, @Param("promoId") Long promoId);

    interface CurrencyRevenueProjection {
        String getCurrency();
        long getAmountMinor();
    }
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.PromoRedemptionStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppliedPromoRepository extends JpaRepository<AppliedPromoEntity, Long>, JpaSpecificationExecutor<AppliedPromoEntity> {
    long deleteByUser(UserEntity user);
    Optional<AppliedPromoEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<AppliedPromoEntity> findByProviderEventId(String providerEventId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId)")
    long countForPromo(@Param("promoId") Long promoId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId) and redemption.status = :status")
    long countForPromoAndStatus(@Param("promoId") Long promoId, @Param("status") PromoRedemptionStatus status);

    @Query("select count(distinct redemption.user.id) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId)")
    long countUniqueUsers(@Param("promoId") Long promoId);

    @Query("select coalesce(sum(redemption.duplicateHits), 0) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId)")
    long sumDuplicateHits(@Param("promoId") Long promoId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where (:promoId is null or redemption.promoCode.id = :promoId) and redemption.rejectionReason like 'LIMIT:%'")
    long countLimitRejections(@Param("promoId") Long promoId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where redemption.user.id = :userId and redemption.status = com.grun.calorietracker.enums.PromoRedemptionStatus.CONVERTED")
    long countConvertedForUser(@Param("userId") Long userId);

    @Query("select count(redemption) from AppliedPromoEntity redemption where redemption.user.id = :userId and redemption.promoCode.id = :promoId and redemption.status = com.grun.calorietracker.enums.PromoRedemptionStatus.CONVERTED")
    long countConvertedForUser(@Param("userId") Long userId, @Param("promoId") Long promoId);

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

    @Query("select redemption.status, count(redemption) from AppliedPromoEntity redemption group by redemption.status")
    List<Object[]> countGroupedByStatus();

    @Query(value = """
            select case
                     when rejection_reason like 'LIMIT:%' then 'LIMIT'
                     when rejection_reason like 'ELIGIBILITY:%' then 'ELIGIBILITY'
                     when rejection_reason like 'PROVIDER:%' then 'PROVIDER'
                     else 'OTHER'
                   end as rejection_category,
                   count(*) as rejection_count
            from applied_promos
            where status = 'REJECTED'
            group by rejection_category
            order by rejection_count desc
            """, nativeQuery = true)
    List<Object[]> countRejectedBySafeCategory();

    @Query(value = """
            select cast(applied_at as date) as applied_date,
                   count(*) as attempts,
                   sum(case when status = 'CONVERTED' then 1 else 0 end) as converted,
                   sum(case when status = 'REJECTED' then 1 else 0 end) as rejected,
                   coalesce(sum(duplicate_hits), 0) as duplicate_attempts
            from applied_promos
            where applied_at >= :fromInclusive
            group by cast(applied_at as date)
            order by applied_date
            """, nativeQuery = true)
    List<Object[]> countDailyOperations(@Param("fromInclusive") LocalDateTime fromInclusive);

    interface CurrencyRevenueProjection {
        String getCurrency();
        long getAmountMinor();
    }
}

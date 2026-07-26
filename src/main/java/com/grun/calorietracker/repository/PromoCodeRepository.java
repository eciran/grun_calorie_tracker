package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.PromoCodeEntity;
import com.grun.calorietracker.enums.PromoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, Long>, JpaSpecificationExecutor<PromoCodeEntity> {
    Optional<PromoCodeEntity> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    long countByStatus(PromoStatus status);

    @Query("select count(promo) from PromoCodeEntity promo where promo.status = com.grun.calorietracker.enums.PromoStatus.ACTIVE and promo.active = true and (promo.startAt is null or promo.startAt <= :now) and (promo.endAt is null or promo.endAt > :now)")
    long countCurrentlyActive(@Param("now") java.time.LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select promo from PromoCodeEntity promo where promo.id = :id")
    Optional<PromoCodeEntity> findByIdForUpdate(@Param("id") Long id);
}

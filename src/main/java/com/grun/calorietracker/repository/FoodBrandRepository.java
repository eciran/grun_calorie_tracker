package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandEntity;
import com.grun.calorietracker.enums.FoodBrandStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodBrandRepository extends JpaRepository<FoodBrandEntity, Long> {
    Optional<FoodBrandEntity> findByNormalizedKeyAndStatus(String normalizedKey, FoodBrandStatus status);

    @Query("""
            select b from FoodBrandEntity b
            where b.status = com.grun.calorietracker.enums.FoodBrandStatus.ACTIVE
              and (:query = '' or lower(b.canonicalName) like lower(concat('%', :query, '%'))
                   or b.normalizedKey like concat('%', :normalizedQuery, '%'))
            order by b.verified desc, b.canonicalName asc
            """)
    List<FoodBrandEntity> autocomplete(@Param("query") String query,
                                       @Param("normalizedQuery") String normalizedQuery,
                                       Pageable pageable);
}

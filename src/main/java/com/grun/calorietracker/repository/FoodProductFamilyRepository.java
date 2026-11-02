package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodProductFamilyRepository extends JpaRepository<FoodProductFamilyEntity, Long>, JpaSpecificationExecutor<FoodProductFamilyEntity> {
    java.util.Optional<FoodProductFamilyEntity> findFirstByFamilyKeyIgnoreCaseAndActiveTrue(String familyKey);

    @Query(value = """
            select distinct family from FoodProductFamilyEntity family
            left join FoodProductFamilyMemberEntity member on member.family = family
            left join member.foodItem product
            where (:active is null or family.active = :active)
              and (:query is null or lower(family.familyKey) like lower(concat('%', :query, '%'))
                or lower(family.displayName) like lower(concat('%', :query, '%'))
                or lower(family.brandKey) like lower(concat('%', :query, '%'))
                or lower(family.appliedBy) like lower(concat('%', :query, '%'))
                or lower(product.displayName) like lower(concat('%', :query, '%'))
                or product.normalizedBarcode like concat('%', :query, '%')
                or str(product.id) = :query)
            """,
            countQuery = """
            select count(distinct family.id) from FoodProductFamilyEntity family
            left join FoodProductFamilyMemberEntity member on member.family = family
            left join member.foodItem product
            where (:active is null or family.active = :active)
              and (:query is null or lower(family.familyKey) like lower(concat('%', :query, '%'))
                or lower(family.displayName) like lower(concat('%', :query, '%'))
                or lower(family.brandKey) like lower(concat('%', :query, '%'))
                or lower(family.appliedBy) like lower(concat('%', :query, '%'))
                or lower(product.displayName) like lower(concat('%', :query, '%'))
                or product.normalizedBarcode like concat('%', :query, '%')
                or str(product.id) = :query)
            """)
    Page<FoodProductFamilyEntity> search(@Param("query") String query, @Param("active") Boolean active, Pageable pageable);
}

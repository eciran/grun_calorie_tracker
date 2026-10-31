package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductFamilyMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodProductFamilyMemberRepository extends JpaRepository<FoodProductFamilyMemberEntity, Long> {
    @Query("""
            select distinct member from FoodProductFamilyMemberEntity member
            join fetch member.family family
            join fetch member.foodItem product
            where family.active = true
              and member.active = true
              and family.id in (
                select related.family.id from FoodProductFamilyMemberEntity related
                where related.foodItem.id in :productIds and related.active = true
              )
            order by family.id, member.sortOrder, member.packageQuantity, member.id
            """)
    List<FoodProductFamilyMemberEntity> findActiveFamilyMembersForProducts(
            @Param("productIds") Collection<Long> productIds
    );

    @Query("""
            select member from FoodProductFamilyMemberEntity member
            join fetch member.family family
            where member.active = true and member.foodItem.id in :productIds
            """)
    List<FoodProductFamilyMemberEntity> findActiveMembershipsForProducts(
            @Param("productIds") Collection<Long> productIds
    );

    List<FoodProductFamilyMemberEntity> findAllByFamilyIdOrderBySortOrderAscIdAsc(Long familyId);
}

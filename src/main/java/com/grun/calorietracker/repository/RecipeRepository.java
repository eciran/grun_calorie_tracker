package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<RecipeEntity, Long>, JpaSpecificationExecutor<RecipeEntity> {
    List<RecipeEntity> findByOwnerUserAndArchivedFalseOrderByUpdatedAtDesc(UserEntity ownerUser);

    List<RecipeEntity> findByOwnerUserAndArchivedFalseAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
            UserEntity ownerUser,
            String name
    );

    List<RecipeEntity> findByOwnerUserAndArchivedFalseAndMealTypeOrderByUpdatedAtDesc(
            UserEntity ownerUser,
            String mealType
    );

    List<RecipeEntity> findByOwnerUserAndArchivedFalseAndNameContainingIgnoreCaseAndMealTypeOrderByUpdatedAtDesc(
            UserEntity ownerUser,
            String name,
            String mealType
    );

    Optional<RecipeEntity> findByIdAndOwnerUserAndArchivedFalse(Long id, UserEntity ownerUser);

    List<RecipeEntity> findByVisibilityAndArchivedFalseOrderByUpdatedAtDesc(RecipeVisibility visibility);

    long countByVisibilityAndVerificationStatusAndArchivedFalse(
            RecipeVisibility visibility,
            VerificationStatus verificationStatus
    );

    @Query("""
            SELECT r
            FROM RecipeEntity r
            WHERE r.id = :id
              AND r.archived = false
              AND (
                    r.ownerUser = :user
                    OR r.visibility = com.grun.calorietracker.enums.RecipeVisibility.PUBLIC_ADMIN
                  )
            """)
    Optional<RecipeEntity> findAccessibleRecipe(@Param("id") Long id, @Param("user") UserEntity user);

    long countByArchivedFalse();
    long countByArchivedTrue();
    long countByVerificationStatusAndArchivedFalse(VerificationStatus status);

    long countByArchivedFalseAndVerificationStatusIn(Collection<VerificationStatus> statuses);

    long countByArchivedFalseAndVerificationStatusInAndCreatedAtGreaterThanEqual(
            Collection<VerificationStatus> statuses,
            LocalDateTime createdAt
    );

    long countByArchivedFalseAndVerificationStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<VerificationStatus> statuses,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );

    long countByArchivedFalseAndVerificationStatusInAndCreatedAtLessThan(
            Collection<VerificationStatus> statuses,
            LocalDateTime createdAt
    );

    @Query("""
            select count(r)
            from RecipeEntity r
            where r.archived = false
              and r.verificationStatus in :statuses
              and r.reviewDueAt < :cutoff
            """)
    long countOverdueReviewRecipes(@Param("statuses") Collection<VerificationStatus> statuses,
                                   @Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select count(r)
            from RecipeEntity r
            where r.archived = false
              and r.verificationStatus in :statuses
              and (r.reviewAssignee is null or trim(r.reviewAssignee) = '')
            """)
    long countUnassignedReviewRecipes(@Param("statuses") Collection<VerificationStatus> statuses);

    @Query("""
            select r.verificationStatus, count(r)
            from RecipeEntity r
            group by r.verificationStatus
            """)
    List<Object[]> countByVerificationStatus();

    @Query("""
            select r.visibility, count(r)
            from RecipeEntity r
            where r.archived = false
            group by r.visibility
            """)
    List<Object[]> countActiveByVisibility();

    @Query(value = """
            select cast(r.created_at as date) as created_date, count(*) as recipe_count
            from recipes r
            where r.created_at >= :fromInclusive
            group by cast(r.created_at as date)
            order by created_date
            """, nativeQuery = true)
    List<Object[]> countCreatedRecipesByDate(@Param("fromInclusive") LocalDateTime fromInclusive);

    @Query("select count(r) from RecipeEntity r where r.archived = false and (r.imageStatus is null or r.imageStatus <> com.grun.calorietracker.enums.ImageStatus.APPROVED)")
    long countMissingApprovedMedia();

    long countByReviewDueAtBeforeAndArchivedFalse(LocalDateTime cutoff);
}

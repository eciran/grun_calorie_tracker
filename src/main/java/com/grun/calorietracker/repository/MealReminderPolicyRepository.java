package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.MealReminderPolicyEntity;
import com.grun.calorietracker.enums.MealReminderPolicyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface MealReminderPolicyRepository extends JpaRepository<MealReminderPolicyEntity,Long> {
 Optional<MealReminderPolicyEntity> findFirstByStatus(MealReminderPolicyStatus status);
 List<MealReminderPolicyEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from MealReminderPolicyEntity p where p.id=:id") Optional<MealReminderPolicyEntity> findByIdForUpdate(@Param("id")Long id);
}

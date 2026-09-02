package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.MealReminderAdminTestSendEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
public interface MealReminderAdminTestSendRepository extends JpaRepository<MealReminderAdminTestSendEntity,Long> {
 long countByUserIdAndRequestedAtAfter(Long userId, Instant after);
 long deleteByRequestedAtBefore(Instant before);
}

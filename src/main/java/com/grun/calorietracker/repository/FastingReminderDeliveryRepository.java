package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.FastingReminderDeliveryEntity;
import com.grun.calorietracker.enums.FastingReminderDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FastingReminderDeliveryRepository extends JpaRepository<FastingReminderDeliveryEntity,Long> {
 Optional<FastingReminderDeliveryEntity> findByOccurrenceKey(String occurrenceKey);
 long countByStatus(FastingReminderDeliveryStatus status);
}

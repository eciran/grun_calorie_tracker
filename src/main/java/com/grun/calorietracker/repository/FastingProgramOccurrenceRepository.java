package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
public interface FastingProgramOccurrenceRepository extends JpaRepository<FastingProgramOccurrenceEntity,Long> {
 Optional<FastingProgramOccurrenceEntity> findByUserAndOccurrenceDate(UserEntity user,LocalDate date);
 Optional<FastingProgramOccurrenceEntity> findByIdAndUser(Long id,UserEntity user);
 long deleteByUser(UserEntity user);
}

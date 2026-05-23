package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodDiaryNoteEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FoodDiaryNoteRepository extends JpaRepository<FoodDiaryNoteEntity, Long> {
    Optional<FoodDiaryNoteEntity> findByUserAndDiaryDate(UserEntity user, LocalDate diaryDate);
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodDiaryNoteEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FoodDiaryNoteRepository extends JpaRepository<FoodDiaryNoteEntity, Long> {
    List<FoodDiaryNoteEntity> findByUserOrderByDiaryDateAsc(UserEntity user);
    Optional<FoodDiaryNoteEntity> findByUserAndDiaryDate(UserEntity user, LocalDate diaryDate);
    long deleteByUser(UserEntity user);
}

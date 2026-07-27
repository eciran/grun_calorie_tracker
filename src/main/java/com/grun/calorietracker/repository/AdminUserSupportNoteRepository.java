package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminUserSupportNoteEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserSupportNoteRepository extends JpaRepository<AdminUserSupportNoteEntity, Long> {
    List<AdminUserSupportNoteEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);
}

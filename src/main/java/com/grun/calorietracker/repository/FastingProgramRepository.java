package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.FastingProgramStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FastingProgramRepository extends JpaRepository<FastingProgramEntity, Long> {
    List<FastingProgramEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<FastingProgramEntity> findByIdAndUserId(Long id, Long userId);
    Optional<FastingProgramEntity> findFirstByUserIdAndStatus(Long userId, FastingProgramStatus status);
    void deleteByUser(UserEntity user);
}

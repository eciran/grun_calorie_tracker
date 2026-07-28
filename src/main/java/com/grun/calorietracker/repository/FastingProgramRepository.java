package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.FastingProgramStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface FastingProgramRepository extends JpaRepository<FastingProgramEntity, Long> {
    List<FastingProgramEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<FastingProgramEntity> findByIdAndUserId(Long id, Long userId);
    Optional<FastingProgramEntity> findFirstByUserIdAndStatus(Long userId, FastingProgramStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FastingProgramEntity p where p.user.id = :userId order by p.id")
    List<FastingProgramEntity> findAllByUserIdForUpdate(@Param("userId") Long userId);
    void deleteByUser(UserEntity user);
}

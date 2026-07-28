package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingProgramVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FastingProgramVersionRepository extends JpaRepository<FastingProgramVersionEntity, Long> {
    List<FastingProgramVersionEntity> findAllByProgramIdOrderByVersionNumberDesc(Long programId);
    Optional<FastingProgramVersionEntity> findByProgramIdAndVersionNumber(Long programId, Integer versionNumber);
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProgressLogDto;
import java.time.LocalDateTime;
import java.util.List;

public interface ProgressLogService {

    ProgressLogDto saveLog(ProgressLogDto log, String email);
    ProgressLogDto updateLog(Long id, ProgressLogDto log, String email);
    ProgressLogDto getLog(Long id, String email);
    void deleteLog(Long id, String email);
    List<ProgressLogDto> getUserLogs(String email);
    List<ProgressLogDto> getUserLogs(String email, LocalDateTime start, LocalDateTime end);
}

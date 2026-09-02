package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiRequestHistoryDetailDto;
import com.grun.calorietracker.dto.AiRequestHistoryPageDto;
import com.grun.calorietracker.dto.AiRequestRecoveryDto;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;

import java.util.List;

public interface AiRequestHistoryService {
    AiRequestRecoveryDto recoverByKey(String email, AiRequestType requestType, String idempotencyKey);

    AiRequestHistoryPageDto listHistoryPage(String email, List<AiRequestType> requestTypes,
                                          List<AiRequestStatus> statuses, Long beforeId, int limit);

    List<AiRequestHistoryDetailDto> listHistory(String email, AiRequestType requestType, AiRequestStatus status, int limit);

    AiRequestHistoryDetailDto getHistoryItem(String email, Long requestId);

    void acknowledgeCompletion(String email, Long requestId);
}

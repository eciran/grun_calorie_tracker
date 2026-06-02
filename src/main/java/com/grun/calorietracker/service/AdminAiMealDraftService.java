package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.enums.AiRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAiMealDraftService {
    Page<AdminAiRequestReviewDto> listRequests(AiRequestStatus status, boolean refundableOnly, Pageable pageable);
    AdminAiQuotaRefundResponseDto refundQuota(String adminEmail, Long requestId, AdminAiQuotaRefundRequestDto request);
}

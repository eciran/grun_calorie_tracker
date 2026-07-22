package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiRequestInspectionDto;
import com.grun.calorietracker.dto.AdminAiMonitoringSummaryDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRejectRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAiMealDraftService {
    Page<AdminAiRequestReviewDto> listRequests(AiRequestType requestType, AiRequestStatus status, boolean refundableOnly, Pageable pageable);
    AdminAiRequestInspectionDto inspectRequest(Long requestId);
    AdminAiMonitoringSummaryDto getMonitoringSummary(int windowHours);
    AdminAiQuotaRefundResponseDto refundQuota(String adminEmail, Long requestId, AdminAiQuotaRefundRequestDto request);
    AdminAiQuotaRefundResponseDto rejectQuotaRefund(String adminEmail, Long requestId, AdminAiQuotaRefundRejectRequestDto request);
}

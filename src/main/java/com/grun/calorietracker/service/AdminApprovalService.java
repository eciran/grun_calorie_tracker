package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.AdminApprovalStatus;
public interface AdminApprovalService {
 AdminApprovalRequestDto create(String maker, AdminApprovalCreateRequestDto request, String correlationId);
 AdminApprovalPageDto list(AdminApprovalStatus status,int page,int size);
 AdminApprovalRequestDto approve(Long id,String checker,String reauthToken,String reason,String correlationId);
 AdminApprovalRequestDto reject(Long id,String checker,String reauthToken,String reason,String correlationId);
}
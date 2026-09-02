package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
import java.util.*;
public interface AdminMealReminderAutomationService {
 AdminMealReminderPolicyDto config();
 AdminMealReminderPolicyDto createDraft(AdminMealReminderPolicyRequestDto request,String admin,String correlationId);
 AdminMealReminderPolicyDto updateDraft(Long id,AdminMealReminderPolicyRequestDto request,String admin,String correlationId);
 List<AdminMealReminderPolicyDto> history(int size);
 Map<String,Object> preview(AdminMealReminderPreviewRequestDto request);
 List<Map<String,Object>> decisions(int size);
 Map<String,Object> summary();
 AdminMealReminderPolicyDto emergencyStop(String reason,String admin,String correlationId);
 void publishApproved(Long id,String checker,String correlationId,boolean rollback);
 void publishDefinitionApproved(Long id,AdminNotificationDefinitionRequestDto definition,String checker,String correlationId);
 void reopenApproved(Long id,String checker,String correlationId);
 void testSendApproved(Long userId,String checker,String correlationId);
}

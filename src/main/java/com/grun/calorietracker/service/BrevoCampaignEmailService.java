package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminBrevoTemplateDto;
import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.entity.UserEntity;
import java.util.List;

public interface BrevoCampaignEmailService {
    List<AdminBrevoTemplateDto> templates();
    String send(NotificationCampaignEntity campaign, UserEntity user);
}

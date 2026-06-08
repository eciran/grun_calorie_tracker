package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminBrevoSenderDto;
import com.grun.calorietracker.dto.AdminBrevoSenderListDto;
import com.grun.calorietracker.dto.AdminBrevoSenderRequestDto;

public interface AdminBrevoSenderService {
    AdminBrevoSenderListDto getSenders();

    AdminBrevoSenderDto createSender(AdminBrevoSenderRequestDto request);

    AdminBrevoSenderDto updateSender(Long senderId, AdminBrevoSenderRequestDto request);
}

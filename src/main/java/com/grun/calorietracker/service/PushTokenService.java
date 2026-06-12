package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PushTokenDto;
import com.grun.calorietracker.dto.PushTokenRegisterRequestDto;

import java.util.List;

public interface PushTokenService {
    PushTokenDto register(String email, PushTokenRegisterRequestDto request);
    List<PushTokenDto> list(String email);
    PushTokenDto revoke(String email, Long tokenId);
}

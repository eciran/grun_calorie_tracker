package com.grun.calorietracker.service;
import com.grun.calorietracker.entity.UserEntity;
public interface AdminSecurityAlertService { void mfaFailure(UserEntity user); void recoveryCodeUsed(UserEntity user); }
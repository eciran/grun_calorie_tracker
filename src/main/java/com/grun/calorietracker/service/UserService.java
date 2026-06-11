package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.AdminUserStatusUpdateRequestDto;
import com.grun.calorietracker.dto.AdminUserPageDto;
import com.grun.calorietracker.dto.NotificationPreferenceDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserService {
        String loginUser(String email, String password);
        UserProfileDto registerUser(UserEntity user);
        Optional<UserEntity> findByEmail(String email);
        List<UserProfileDto> getAllUsers();
        AdminUserPageDto listUsersForAdmin(UserRole role, Boolean accountEnabled, Boolean accountLocked, int page, int size);
        Optional<UserProfileDto> getById(Long id);
        UserProfileDto updateUserStatus(Long userId, AdminUserStatusUpdateRequestDto request, String adminEmail);
        UserProfileDto getCurrentUser(String email);
        UserProfileDto updateCurrentUser(UserProfileDto updatedUserDto, String email);
        NotificationPreferenceDto getNotificationPreferences(String email);
        NotificationPreferenceDto updateNotificationPreferences(String email, NotificationPreferenceDto request);
        BodyFatResultDto calculateBodyFatAndBmi(BodyFatRequestDto req, UserEntity user);
    }

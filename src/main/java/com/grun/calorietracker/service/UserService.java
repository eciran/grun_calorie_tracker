package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminUserDto;
import com.grun.calorietracker.dto.AdminUserPageDto;
import com.grun.calorietracker.dto.AdminUserStatusUpdateRequestDto;
import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.MyProfileDto;
import com.grun.calorietracker.dto.MyProfileUpdateRequestDto;
import com.grun.calorietracker.dto.NotificationPreferenceDto;
import com.grun.calorietracker.dto.ProfileBodyDto;
import com.grun.calorietracker.dto.ProfileBodyUpdateRequestDto;
import com.grun.calorietracker.dto.ProfilePreferencesDto;
import com.grun.calorietracker.dto.ProfilePreferencesUpdateRequestDto;
import com.grun.calorietracker.dto.ProfileSecurityDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserService {
    String loginUser(String email, String password);
    UserProfileDto registerUser(UserEntity user);
    Optional<UserEntity> findByEmail(String email);

    List<AdminUserDto> getAllUsers();
    AdminUserPageDto listUsersForAdmin(UserRole role, Boolean accountEnabled, Boolean accountLocked, int page, int size);
    Optional<AdminUserDto> getById(Long id);
    AdminUserDto updateUserStatus(Long userId, AdminUserStatusUpdateRequestDto request, String adminEmail);

    MyProfileDto getMyProfile(String email);
    MyProfileDto updateMyProfile(MyProfileUpdateRequestDto request, String email);
    ProfileBodyDto getProfileBody(String email);
    MyProfileDto updateProfileBody(ProfileBodyUpdateRequestDto request, String email);
    ProfilePreferencesDto getProfilePreferences(String email);
    MyProfileDto updateProfilePreferences(ProfilePreferencesUpdateRequestDto request, String email);
    ProfileSecurityDto getProfileSecurity(String email);

    @Deprecated(forRemoval = false)
    UserProfileDto getCurrentUser(String email);

    @Deprecated(forRemoval = false)
    UserProfileDto updateCurrentUser(UserProfileDto updatedUserDto, String email);

    NotificationPreferenceDto getNotificationPreferences(String email);
    NotificationPreferenceDto updateNotificationPreferences(String email, NotificationPreferenceDto request);
    BodyFatResultDto calculateBodyFatAndBmi(BodyFatRequestDto req, UserEntity user);
}

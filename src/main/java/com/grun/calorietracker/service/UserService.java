package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserService {
        String loginUser(String email, String password);
        UserProfileDto registerUser(UserEntity user);
        Optional<UserEntity> findByEmail(String email);
        List<UserProfileDto> getAllUsers();
        Optional<UserProfileDto> getById(Long id);
        UserProfileDto getCurrentUser(String email);
        UserProfileDto updateCurrentUser(UserProfileDto updatedUserDto, String email);
        BodyFatResultDto calculateBodyFatAndBmi(BodyFatResultDto req, UserEntity user);
    }

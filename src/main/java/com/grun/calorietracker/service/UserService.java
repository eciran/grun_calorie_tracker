package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserService {
    String loginUser(String email, String password);
    UserEntity registerUser(UserEntity user);
    Optional<UserEntity> findByEmail(String email);
    List<UserEntity> getAllUsers();
    Optional<UserEntity> getById(Long id);
    Optional<UserEntity> getCurrentUser();
    Optional<UserEntity> updateCurrentUser(UserEntity updatedUser, String email);
}

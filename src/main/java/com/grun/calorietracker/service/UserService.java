package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserService {
    UserEntity registerUser(UserEntity user);
    Optional<UserEntity> findByEmail(String email);
    List<UserEntity> getAllUsers();
    Optional<UserEntity> getById(Long id);
}

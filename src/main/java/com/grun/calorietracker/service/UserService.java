package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;

import java.util.List;

public interface UserService {
    UserEntity createUser(UserEntity user);
    List<UserEntity> getAllUsers();
}

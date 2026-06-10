package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.UserProfileDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarService {
    UserProfileDto uploadAvatar(String email, MultipartFile file);

    UserProfileDto deleteAvatar(String email);

    Resource loadAvatar(String filename);
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;

public interface RecipeImageModerationService {
    Result moderate(String imageUrl, ImageSource source);

    record Result(ImageStatus status, String note) {
    }
}

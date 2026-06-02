package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiPhotoReferenceDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AiPhotoReferenceService {
    AiPhotoReferenceDto createReference(String email, MultipartFile file);

    Resource loadReference(String token);

    long cleanupExpiredReferences();
}

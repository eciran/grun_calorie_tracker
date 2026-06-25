package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.RecipeMediaProperties;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RecipeImageService;
import com.grun.calorietracker.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeImageServiceImpl implements RecipeImageService {

    private final RecipeMediaProperties properties;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;

    @Override
    @Transactional
    public RecipeDto uploadRecipeImage(String email, Long recipeId, MultipartFile file) {
        validate(file);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        RecipeEntity recipe = recipeRepository.findByIdAndOwnerUserAndArchivedFalse(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        String extension = extension(file.getOriginalFilename(), file.getContentType());
        String filename = "r" + recipe.getId() + "-u" + user.getId() + "-" + UUID.randomUUID() + extension;
        Path target = storageRoot().resolve(filename).normalize();
        ensureInsideStorage(target);

        try {
            Files.createDirectories(storageRoot());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Recipe image could not be stored.");
        }

        deleteExistingLocalRecipeImage(recipe.getImageUrl());
        recipe.setImageUrl(publicUrl(filename));
        recipe.setImageSource(ImageSource.USER_UPLOAD);
        recipe.setImageStatus(ImageStatus.NEEDS_REVIEW);
        recipe.setImageReviewNote(null);
        recipe.setImageReviewedAt(null);
        recipe.setImageReviewedBy(null);
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN) {
            recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
            recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        }
        recipeRepository.save(recipe);
        return recipeService.getRecipe(email, recipeId);
    }

    @Override
    public Resource loadRecipeImage(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Recipe image filename is invalid.");
        }
        Path target = storageRoot().resolve(filename).normalize();
        ensureInsideStorage(target);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ResourceNotFoundException("Recipe image not found");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Recipe image could not be loaded.");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Recipe image is required.");
        }
        if (file.getSize() > properties.getImage().getMaxUploadBytes()) {
            throw new IllegalArgumentException("Recipe image exceeds the configured upload limit.");
        }
        String contentType = file.getContentType();
        Set<String> allowedTypes = Arrays.stream(properties.getImage().getAllowedContentTypes().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Recipe image content type is not allowed.");
        }
    }

    private Path storageRoot() {
        return Path.of(properties.getImage().getStorageDirectory()).toAbsolutePath().normalize();
    }

    private void ensureInsideStorage(Path target) {
        if (!target.startsWith(storageRoot())) {
            throw new IllegalArgumentException("Recipe image path is invalid.");
        }
    }

    private String publicUrl(String filename) {
        String baseUrl = properties.getImage().getPublicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/api/v1/recipes/images/" + filename;
    }

    private void deleteExistingLocalRecipeImage(String imageUrl) {
        String filename = filenameFromRecipeImageUrl(imageUrl);
        if (filename == null) {
            return;
        }
        Path target = storageRoot().resolve(filename).normalize();
        try {
            ensureInsideStorage(target);
            Files.deleteIfExists(target);
        } catch (IOException | IllegalArgumentException ignored) {
            // Updating the recipe should not fail because an old local image is already gone.
        }
    }

    private String filenameFromRecipeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String marker = "/api/v1/recipes/images/";
        int markerIndex = imageUrl.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String filename = imageUrl.substring(markerIndex + marker.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        return filename;
    }

    private String extension(String originalFilename, String contentType) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        }
        if (Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            return extension;
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
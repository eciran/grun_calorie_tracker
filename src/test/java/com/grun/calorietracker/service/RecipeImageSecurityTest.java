package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.config.RecipeMediaProperties;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.RecipeImageServiceImpl;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeImageSecurityTest {
    @TempDir Path directory;
    RecipeMediaProperties properties;
    RecipeRepository recipes;
    RecipeEntity recipe;
    RecipeImageServiceImpl service;

    @BeforeEach void setUp() {
        properties = new RecipeMediaProperties();
        properties.getImage().setStorageDirectory(directory.toString());
        UserRepository users = mock(UserRepository.class);
        recipes = mock(RecipeRepository.class);
        UserEntity owner = new UserEntity();
        owner.setId(1L);
        recipe = new RecipeEntity();
        recipe.setId(2L);
        recipe.setOwnerUser(owner);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(recipes.findByIdAndOwnerUserAndArchivedFalse(2L, owner)).thenReturn(Optional.of(recipe));
        service = new RecipeImageServiceImpl(properties, users, recipes, mock(RecipeService.class),
                new FoodProductEvidenceImageInspector(new FoodContributionStorageProperties()));
    }

    @Test void rejectsMimeSpoofedHtmlBeforeStorageOrRecipeMutation() throws Exception {
        var file = new MockMultipartFile("file", "image.jpg", "image/jpeg", "<html>not an image</html>".getBytes());
        assertThatThrownBy(() -> service.uploadRecipeImage("owner@example.com", 2L, file)).isInstanceOf(IllegalArgumentException.class);
        verify(recipes, never()).save(any());
        assertThat(recipe.getImageUrl()).isNull();
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test void rejectsImageWithMismatchedDeclaredMime() throws Exception {
        var file = new MockMultipartFile("file", "image.jpg", "image/jpeg", png());
        assertThatThrownBy(() -> service.uploadRecipeImage("owner@example.com", 2L, file)).isInstanceOf(IllegalArgumentException.class);
        verify(recipes, never()).save(any());
    }

    @Test void rejectsExcessivePixelDimensions() throws Exception {
        properties.getImage().setMaxDecodedPixels(32);
        var file = new MockMultipartFile("file", "image.png", "image/png", png());
        assertThatThrownBy(() -> service.uploadRecipeImage("owner@example.com", 2L, file)).isInstanceOf(IllegalArgumentException.class);
        verify(recipes, never()).save(any());
    }

    @Test void rejectsExcessiveUploadBytes() throws Exception {
        properties.getImage().setMaxUploadBytes(16);
        var file = new MockMultipartFile("file", "image.png", "image/png", png());
        assertThatThrownBy(() -> service.uploadRecipeImage("owner@example.com", 2L, file)).isInstanceOf(IllegalArgumentException.class);
        verify(recipes, never()).save(any());
    }

    @Test void storesValidatedBytesWithContentDerivedExtension() throws Exception {
        byte[] content = png();
        var file = new MockMultipartFile("file", "../../wrong.jpg", "image/png", content);
        service.uploadRecipeImage("owner@example.com", 2L, file);
        assertThat(recipe.getImageUrl()).endsWith(".png");
        try (var files = Files.list(directory)) {
            Path stored = files.findFirst().orElseThrow();
            assertThat(stored.getFileName().toString()).startsWith("r2-u1-").endsWith(".png");
            assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        }
        verify(recipes).save(recipe);
    }

    @Test void otherAccountCannotUploadToRecipe() throws Exception {
        var file = new MockMultipartFile("file", "image.png", "image/png", png());
        assertThatThrownBy(() -> service.uploadRecipeImage("owner@example.com", 3L, file))
                .isInstanceOf(com.grun.calorietracker.exception.ResourceNotFoundException.class);
        verify(recipes, never()).save(any());
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test void rejectsTraversalInImageRead() {
        assertThatThrownBy(() -> service.loadRecipeImage("../outside.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.loadRecipeImage("..\\outside.png")).isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}

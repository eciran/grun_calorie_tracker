package com.grun.calorietracker.service;

import com.grun.calorietracker.config.MediaStorageProperties;
import com.grun.calorietracker.config.RecipeMediaProperties;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.service.impl.RecipeMediaCacheServiceImpl;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RecipeMediaCacheSecurityTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/image.png", "file:///etc/passwd", "https://user:password@8.8.8.8/image.png",
            "https://8.8.8.8:8080/image.png", "https://8.8.8.8/image.png#fragment",
            "https://127.0.0.1/image.png", "https://2130706433/image.png", "https://10.0.0.1/image.png",
            "https://169.254.169.254/latest/meta-data/", "https://[::1]/image.png",
            "https://[fd00:ec2::254]/image.png", "https://[::ffff:127.0.0.1]/image.png"
    })
    void unsafeSourcesCannotReachStorage(String url) {
        var storage = mock(MediaObjectStorage.class);
        var policy = mock(MediaStorageKeyPolicy.class);
        var inspector = new com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector(
                new com.grun.calorietracker.config.FoodContributionStorageProperties());
        var service = new RecipeMediaCacheServiceImpl(storage, policy, new MediaStorageProperties(), new RecipeMediaProperties(), inspector);
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(1L);
        recipe.setImageUrl(url);
        assertThatThrownBy(() -> service.cacheApprovedImage(recipe)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage, policy);
    }
}

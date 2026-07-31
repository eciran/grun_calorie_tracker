package com.grun.calorietracker.controller;

import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodProductReviewCaseService;
import com.grun.calorietracker.service.FoodProductReviewSubmissionService;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FoodProductReviewControllerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(FoodProductReviewCaseRepository.class, () -> mock(FoodProductReviewCaseRepository.class))
            .withBean(FoodProductReviewCaseService.class, () -> mock(FoodProductReviewCaseService.class))
            .withBean(ProductIntakeRolloutPolicy.class, () -> mock(ProductIntakeRolloutPolicy.class));

    @Test
    void localStorageLoadsReviewCaseControllerWithoutSubmissionService() {
        contextRunner
                .withPropertyValues("grun.food-contribution-storage.provider=LOCAL")
                .withUserConfiguration(FoodProductReviewCaseController.class, FoodProductReviewEvidenceController.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FoodProductReviewCaseController.class);
                    assertThat(context).doesNotHaveBean(FoodProductReviewEvidenceController.class);
                    assertThat(context).doesNotHaveBean(FoodProductReviewSubmissionService.class);
                });
    }

    @Test
    void s3StorageLoadsEvidenceControllerWhenSubmissionServiceExists() {
        contextRunner
                .withPropertyValues("grun.food-contribution-storage.provider=S3")
                .withBean(FoodProductReviewSubmissionService.class,
                        () -> mock(FoodProductReviewSubmissionService.class))
                .withUserConfiguration(FoodProductReviewEvidenceController.class)
                .run(context -> assertThat(context).hasSingleBean(FoodProductReviewEvidenceController.class));
    }
}
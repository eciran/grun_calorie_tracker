package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNutritionOcrPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsCloudFallbackToDisabledIndependentlyFromGeneralAi() {
        contextRunner.withPropertyValues("grun.ai.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            ProductNutritionOcrProperties properties = context.getBean(ProductNutritionOcrProperties.class);
            assertThat(properties.isCloudEnabled()).isFalse();
            assertThat(properties.getFallbackThreshold()).isEqualTo(0.92);
            assertThat(properties.getMaxAttempts()).isEqualTo(1);
            assertThat(properties.getRolloutStage()).isEqualTo("OFF");
            assertThat(properties.getRolloutPercentage()).isZero();
        });
    }

    @Test
    void rejectsUnknownRolloutStageAndPercentageOutsideRange() {
        contextRunner.withPropertyValues(
                "grun.product-ocr.rollout-stage=EVERYONE",
                "grun.product-ocr.rollout-percentage=101"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsafeAttemptAndThresholdValues() {
        contextRunner.withPropertyValues(
                "grun.product-ocr.cloud-enabled=true",
                "grun.product-ocr.fallback-threshold=1.1",
                "grun.product-ocr.max-attempts=3"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProductNutritionOcrProperties.class)
    static class TestConfiguration { }
}

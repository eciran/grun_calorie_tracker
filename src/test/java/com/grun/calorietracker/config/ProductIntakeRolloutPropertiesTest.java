package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.MarketRegion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIntakeRolloutPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RolloutConfiguration.class);

    @Test
    void bindsMultipleMarketsWithoutImplementationChanges() {
        contextRunner.withPropertyValues(
                "grun.product-intake.rollout.enabled=true",
                "grun.product-intake.rollout.percentage=10",
                "grun.product-intake.rollout.markets=EU,UK_IE"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            ProductIntakeRolloutProperties properties = context.getBean(ProductIntakeRolloutProperties.class);
            assertThat(properties.getMarkets()).isEqualTo(Set.of(MarketRegion.EU, MarketRegion.UK_IE));
        });
    }

    @Test
    void rejectsUnplannedPercentageStage() {
        contextRunner.withPropertyValues(
                "grun.product-intake.rollout.enabled=true",
                "grun.product-intake.rollout.percentage=27",
                "grun.product-intake.rollout.markets=EU"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProductIntakeRolloutProperties.class)
    static class RolloutConfiguration { }
}
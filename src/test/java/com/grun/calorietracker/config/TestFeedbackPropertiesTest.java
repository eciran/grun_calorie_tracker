package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedbackPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context ->
                assertThat(context.getBean(TestFeedbackProperties.class).isEnabledFor("preview")).isFalse());
    }

    @Test
    void permitsOnlyConfiguredTestEnvironmentsWhenEnabled() {
        runner.withPropertyValues("grun.test-feedback.enabled=true")
                .run(context -> {
                    TestFeedbackProperties properties = context.getBean(TestFeedbackProperties.class);
                    assertThat(properties.isEnabledFor("preview")).isTrue();
                    assertThat(properties.isEnabledFor("production")).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TestFeedbackProperties.class)
    static class TestConfiguration { }
}

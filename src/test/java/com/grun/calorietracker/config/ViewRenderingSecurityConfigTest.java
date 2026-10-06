package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.view.xslt.XsltView;
import org.springframework.web.servlet.view.xslt.XsltViewResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewRenderingSecurityConfigTest {
    @Test
    void applicationHandlersNeverUseImplicitViewRendering() throws Exception {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                org.springframework.stereotype.Controller.class));
        var controllers = scanner.findCandidateComponents("com.grun.calorietracker");
        assertThat(controllers.size()).isGreaterThan(100);
        int mappedMethods = 0;
        for (var definition : controllers) {
            var controller = Class.forName(definition.getBeanClassName(), false, getClass().getClassLoader());
            for (var method : controller.getMethods()) {
                if (org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(method,
                        org.springframework.web.bind.annotation.RequestMapping.class) == null) continue;
                mappedMethods++;
                boolean responseBody = org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
                        controller, org.springframework.web.bind.annotation.ResponseBody.class)
                        || org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
                        method, org.springframework.web.bind.annotation.ResponseBody.class);
                boolean explicitHttpEntity = org.springframework.http.HttpEntity.class.isAssignableFrom(method.getReturnType());
                assertThat(responseBody || explicitHttpEntity)
                        .as("%s must not use implicit MVC view rendering", method).isTrue();
            }
        }
        assertThat(mappedMethods).isGreaterThan(500);
    }

    @Test
    void rejectsXsltViewsAndResolversBeforeInitialization() {
        var guard = ViewRenderingSecurityConfig.rejectXsltViewRendering();
        assertThatThrownBy(() -> guard.postProcessBeforeInitialization(new XsltView(), "view"))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("XSLT view rendering is disabled");
        assertThatThrownBy(() -> guard.postProcessBeforeInitialization(new XsltViewResolver(), "resolver"))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("XSLT view rendering is disabled");
        Object ordinaryBean = new Object();
        assertThat(guard.postProcessBeforeInitialization(ordinaryBean, "ordinary")).isSameAs(ordinaryBean);
    }

    @Test
    void startupFailsIfXsltResolverIsAccidentallyConfigured() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ViewRenderingSecurityConfig.class);
            context.registerBean("unsafeResolver", XsltViewResolver.class);
            assertThatThrownBy(context::refresh).hasMessageContaining("XSLT view rendering is disabled");
        }
    }
}

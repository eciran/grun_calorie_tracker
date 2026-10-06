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

package com.grun.calorietracker.config;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.view.xslt.XsltView;
import org.springframework.web.servlet.view.xslt.XsltViewResolver;

@Configuration(proxyBeanMethods = false)
public class ViewRenderingSecurityConfig {
    @Bean
    static BeanPostProcessor rejectXsltViewRendering() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                // This API uses static pages, not stylesheet-driven MVC views.
                if (bean instanceof XsltView || bean instanceof XsltViewResolver) {
                    throw new BeanCreationException(beanName, "XSLT view rendering is disabled for this API.");
                }
                return bean;
            }
        };
    }
}

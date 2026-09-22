package com.grun.calorietracker.config;

import com.grun.calorietracker.security.OwnerErrorCaptureFilter;
import com.grun.calorietracker.service.OwnerErrorRecorder;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class OwnerErrorCaptureConfig {
    @Bean public FilterRegistrationBean<OwnerErrorCaptureFilter> ownerErrorCaptureFilter(OwnerErrorRecorder recorder,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mapping) {
        var filter = new OwnerErrorCaptureFilter(recorder, () -> mapping.getObject().getHandlerMethods().keySet().stream()
            .filter(info -> info.getPathPatternsCondition()!=null).flatMap(info -> info.getPathPatternsCondition().getPatterns().stream()).toList());
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE+10);
        registration.setDispatcherTypes(DispatcherType.REQUEST,DispatcherType.ASYNC,DispatcherType.ERROR);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        return registration;
    }
}

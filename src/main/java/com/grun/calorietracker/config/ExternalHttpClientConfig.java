package com.grun.calorietracker.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class ExternalHttpClientConfig {

    @Bean
    public RestClientCustomizer externalRestClientTimeoutCustomizer(ExternalHttpClientProperties properties) {
        return builder -> builder.requestFactory(requestFactory(properties));
    }

    private SimpleClientHttpRequestFactory requestFactory(ExternalHttpClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}

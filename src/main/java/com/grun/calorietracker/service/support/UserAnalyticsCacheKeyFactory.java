package com.grun.calorietracker.service.support;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class UserAnalyticsCacheKeyFactory {

    public String key(UserAnalyticsCacheIdentity identity, String variant, Object... dimensions) {
        String suffix = Arrays.stream(dimensions)
                .map(value -> value == null ? "-" : sanitize(value.toString()))
                .collect(Collectors.joining(":"));
        return identity.userId() + ":" + identity.revision() + ":" + sanitize(variant) + ":" + suffix;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}

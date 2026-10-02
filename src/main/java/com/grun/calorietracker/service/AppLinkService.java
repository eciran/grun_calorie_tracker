package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AppLinkService {

    private static final Set<String> DESTINATIONS = Set.of("plans", "notifications", "profile");
    private final String publicBaseUrl;

    public AppLinkService(@Value("${grun.app-links.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public String destination(String destination, PreferredLanguage language) {
        if (!DESTINATIONS.contains(destination)) {
            throw new IllegalArgumentException("Unsupported application link destination");
        }
        return publicBaseUrl + "/open/" + destination
                + (language == PreferredLanguage.TR ? "?lang=tr" : "");
    }
}

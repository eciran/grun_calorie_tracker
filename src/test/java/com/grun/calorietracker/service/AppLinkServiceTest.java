package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.PreferredLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppLinkServiceTest {

    private final AppLinkService service = new AppLinkService("https://api.gruncalorietracker.com/");

    @Test
    void buildsOnlyAllowlistedLocalizedDestinations() {
        assertThat(service.destination("plans", PreferredLanguage.EN))
                .isEqualTo("https://api.gruncalorietracker.com/open/plans");
        assertThat(service.destination("notifications", PreferredLanguage.TR))
                .isEqualTo("https://api.gruncalorietracker.com/open/notifications?lang=tr");
        assertThatThrownBy(() -> service.destination("https://attacker.example", PreferredLanguage.EN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

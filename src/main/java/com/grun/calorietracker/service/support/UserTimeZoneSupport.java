package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class UserTimeZoneSupport {

    public static final String DEFAULT_TIME_ZONE = "Europe/Dublin";

    public ZoneId zoneId(UserEntity user) {
        return zoneId(user == null ? null : user.getTimeZone());
    }

    public ZoneId zoneId(String timeZone) {
        String candidate = timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone.trim();
        try {
            return ZoneId.of(candidate);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid timeZone. Use a valid IANA time zone id such as Europe/Dublin.");
        }
    }

    public String normalize(String timeZone) {
        return zoneId(timeZone).getId();
    }

    public String normalizeOrDefault(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return DEFAULT_TIME_ZONE;
        }
        return normalize(timeZone);
    }

    public LocalDate today(UserEntity user) {
        return LocalDate.now(zoneId(user));
    }

    public LocalDateTime now(UserEntity user) {
        return LocalDateTime.now(zoneId(user));
    }

    public LocalTime currentTime(UserEntity user) {
        return LocalTime.now(zoneId(user));
    }
}

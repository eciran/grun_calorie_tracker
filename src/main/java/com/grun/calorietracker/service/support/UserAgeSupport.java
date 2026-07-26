package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

@Component
public class UserAgeSupport {

    private static final int MIN_AGE = 13;
    private static final int MAX_AGE = 100;

    public Integer resolveAge(LocalDate birthDate, Integer legacyAge, ZoneId zoneId) {
        if (birthDate != null) {
            return calculateAge(birthDate, LocalDate.now(zoneId));
        }
        if (legacyAge == null) {
            return null;
        }
        validateRange(legacyAge);
        return legacyAge;
    }

    public Integer resolveAge(UserEntity user, ZoneId zoneId) {
        return resolveAge(user.getBirthDate(), user.getAge(), zoneId);
    }

    public int calculateAge(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            throw new IllegalArgumentException("birthDate is required");
        }
        if (today == null) {
            throw new IllegalArgumentException("Reference date is required");
        }
        if (birthDate.isAfter(today)) {
            throw new IllegalArgumentException("birthDate cannot be in the future");
        }
        int age = Period.between(birthDate, today).getYears();
        validateRange(age);
        return age;
    }

    private void validateRange(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new IllegalArgumentException("User age must be between 13 and 100");
        }
    }
}

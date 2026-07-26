package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.UserAgeSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAgeSupportTest {

    private final UserAgeSupport support = new UserAgeSupport();

    @Test
    void calculateAge_handlesLeapDayBirthday() {
        LocalDate birthDate = LocalDate.of(2000, 2, 29);

        assertEquals(24, support.calculateAge(birthDate, LocalDate.of(2025, 2, 28)));
        assertEquals(25, support.calculateAge(birthDate, LocalDate.of(2025, 3, 1)));
    }

    @Test
    void resolveAge_usesTheDateInTheSuppliedTimeZone() {
        LocalDate birthDate = LocalDate.now(ZoneId.of("Pacific/Kiritimati")).minusYears(30);

        assertEquals(30, support.resolveAge(birthDate, 99, ZoneId.of("Pacific/Kiritimati")));
    }

    @Test
    void resolveAge_keepsLegacyAgeWhenBirthDateIsMissing() {
        assertEquals(32, support.resolveAge(null, 32, ZoneId.of("Europe/Dublin")));
        assertNull(support.resolveAge(null, null, ZoneId.of("Europe/Dublin")));
    }

    @Test
    void calculateAge_rejectsUnsupportedAgeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> support.calculateAge(LocalDate.now().minusYears(12), LocalDate.now()));
    }
}

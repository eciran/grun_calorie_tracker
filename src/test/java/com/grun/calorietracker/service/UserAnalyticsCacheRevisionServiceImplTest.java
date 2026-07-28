package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.UserAnalyticsCacheRevisionRepository;
import com.grun.calorietracker.service.impl.UserAnalyticsCacheRevisionServiceImpl;
import com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAnalyticsCacheRevisionServiceImplTest {

    private final UserAnalyticsCacheRevisionRepository repository = mock(UserAnalyticsCacheRevisionRepository.class);
    private final UserAnalyticsCacheRevisionServiceImpl service =
            new UserAnalyticsCacheRevisionServiceImpl(repository, new SimpleMeterRegistry());

    @Test
    void requireIdentity_returnsRevisionIdentity() {
        var identity = new UserAnalyticsCacheIdentity(9L, 3L, "Europe/Dublin");
        when(repository.findIdentityByEmail("user@grun.app")).thenReturn(Optional.of(identity));

        assertEquals(identity, service.requireIdentity("user@grun.app"));
    }

    @Test
    void requireIdentity_whenUserMissing_throwsSafeAuthenticationError() {
        when(repository.findIdentityByEmail("missing@grun.app")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.requireIdentity("missing@grun.app"));
    }

    @Test
    void bump_incrementsRevisionForInternalUserId() {
        service.bump(9L, AnalyticsMutationSource.FOOD_LOG);

        verify(repository).incrementRevision(9L);
    }
}

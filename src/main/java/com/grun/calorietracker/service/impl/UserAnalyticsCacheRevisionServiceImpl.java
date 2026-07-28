package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.UserAnalyticsCacheRevisionRepository;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAnalyticsCacheRevisionServiceImpl implements UserAnalyticsCacheRevisionService {

    private final UserAnalyticsCacheRevisionRepository repository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public UserAnalyticsCacheIdentity requireIdentity(String email) {
        return repository.findIdentityByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bump(Long userId, AnalyticsMutationSource source) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required for analytics cache invalidation.");
        }
        repository.incrementRevision(userId);
        meterRegistry.counter("grun.cache.revision.bump", "source", source.name()).increment();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bumpForEmail(String email, AnalyticsMutationSource source) {
        bump(requireIdentity(email).userId(), source);
    }
}


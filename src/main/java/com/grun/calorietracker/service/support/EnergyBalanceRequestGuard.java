package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class EnergyBalanceRequestGuard {

    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final EnergyBalancePolicy energyBalancePolicy;

    public RequestContext validate(String email, LocalDate startDate, LocalDate endDate) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_ANALYTICS);
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ZoneId zoneId = userTimeZoneSupport.zoneId(user);
        int dayCount = energyBalancePolicy.validateRange(
                startDate,
                endDate,
                LocalDate.now(zoneId)
        );
        return new RequestContext(user, startDate, endDate, dayCount, zoneId);
    }

    public record RequestContext(
            UserEntity user,
            LocalDate startDate,
            LocalDate endDate,
            int dayCount,
            ZoneId zoneId
    ) {
    }
}

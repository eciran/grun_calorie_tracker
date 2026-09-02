package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.enums.NotificationReleaseStage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationReleaseGateTest {

    private final NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    private final NotificationReleaseGate gate = new NotificationReleaseGate(properties);

    @Test
    void defaultsFailClosed() {
        assertFalse(gate.anyAudienceEnabled());
        assertEquals("DEPLOYMENT_GATE_OFF", gate.evaluate(42L).reasonCode());
    }

    @Test
    void dryRunNeverAllowsProviderDelivery() {
        properties.setEnabled(true);
        properties.setStage(NotificationReleaseStage.DRY_RUN);
        properties.setTestUserIds(Set.of(42L));
        assertFalse(gate.anyAudienceEnabled());
        assertEquals("DRY_RUN_NO_DELIVERY", gate.evaluate(42L).reasonCode());
    }

    @Test
    void testAndPilotStagesUseOnlyNamedCohorts() {
        properties.setEnabled(true);
        properties.setTestUserIds(Set.of(10L));
        properties.setPilotUserIds(Set.of(20L));
        properties.setStage(NotificationReleaseStage.TEST_ACCOUNTS);
        assertTrue(gate.evaluate(10L).allowed());
        assertFalse(gate.evaluate(20L).allowed());

        properties.setStage(NotificationReleaseStage.PILOT);
        assertTrue(gate.evaluate(10L).allowed());
        assertTrue(gate.evaluate(20L).allowed());
        assertFalse(gate.evaluate(30L).allowed());
    }

    @Test
    void liveUsesStablePercentageAndKeepsNamedAccounts() {
        properties.setEnabled(true);
        properties.setStage(NotificationReleaseStage.LIVE);
        properties.setLivePercentage(5);
        properties.setPilotUserIds(Set.of(99L));
        assertTrue(gate.evaluate(1L).allowed());
        assertFalse(gate.evaluate(6L).allowed());
        assertTrue(gate.evaluate(99L).allowed());
    }

    @Test
    void invalidIdsAndPercentagesFailClosedOrClampSafely() {
        properties.setEnabled(true);
        properties.setStage(NotificationReleaseStage.TEST_ACCOUNTS);
        properties.setTestUserIds(Set.of(-1L));
        assertFalse(gate.anyAudienceEnabled());
        properties.setStage(NotificationReleaseStage.LIVE);
        properties.setLivePercentage(-5);
        assertFalse(gate.anyAudienceEnabled());
        properties.setLivePercentage(150);
        assertTrue(gate.evaluate(500L).allowed());
    }
}

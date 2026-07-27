package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MicronutrientTotalsDto;
import com.grun.calorietracker.service.impl.DefaultMicronutrientReferenceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMicronutrientReferenceServiceTest {

    private final DefaultMicronutrientReferenceService service = new DefaultMicronutrientReferenceService();

    @Test
    void resolveTargets_forAdult_returnsVersionedEuropeanReferenceProfile() {
        MicronutrientTotalsDto targets = service.resolveTargets(32);

        assertEquals(2000.0, targets.getSodium());
        assertEquals(3500.0, targets.getPotassium());
        assertEquals(800.0, targets.getCalcium());
        assertEquals(14.0, targets.getIron());
        assertEquals(375.0, targets.getMagnesium());
        assertEquals(10.0, targets.getZinc());
        assertEquals(800.0, targets.getVitaminA());
        assertEquals(80.0, targets.getVitaminC());
        assertEquals(5.0, targets.getVitaminD());
        assertEquals(12.0, targets.getVitaminE());
        assertEquals(2.5, targets.getVitaminB12());
        assertNull(targets.getSugar());
        assertNull(targets.getCholesterol());
    }

    @Test
    void resolveTargets_forMinor_doesNotApplyAdultReferenceValues() {
        assertNull(service.resolveTargets(17));

        var quality = service.assessDataQuality(null, 17);
        assertFalse(quality.getTargetProfileApplicable());
        assertEquals("ADULT_PROFILE_NOT_APPLICABLE", quality.getTargetProfileUnavailableReason());
    }

    @Test
    void calculateRemaining_preservesUnknownConsumptionInsteadOfTreatingItAsZero() {
        MicronutrientTotalsDto consumed = new MicronutrientTotalsDto();
        consumed.setSodium(1500.0);
        consumed.setCalcium(null);

        MicronutrientTotalsDto remaining = service.calculateRemaining(consumed, service.resolveTargets(32));

        assertEquals(500.0, remaining.getSodium());
        assertNull(remaining.getCalcium());
        assertNull(remaining.getSugar());
        assertNull(remaining.getCholesterol());
    }

    @Test
    void assessDataQuality_reportsCoreFieldCoverageAndMissingNutrients() {
        MicronutrientTotalsDto consumed = new MicronutrientTotalsDto();
        consumed.setSodium(1500.0);
        consumed.setCalcium(600.0);
        consumed.setVitaminC(70.0);

        var quality = service.assessDataQuality(consumed, 32);

        assertEquals(3, quality.getAvailableNutrientCount());
        assertEquals(11, quality.getTrackedNutrientCount());
        assertEquals(27.27, quality.getCoveragePercent());
        assertEquals("PARTIAL", quality.getCoverageLevel());
        assertTrue(quality.getMissingNutrients().contains("POTASSIUM"));
        assertTrue(quality.getReferenceSources().contains("EFSA_SODIUM_2019"));
        assertTrue(quality.getTargetProfileApplicable());
        assertNull(quality.getTargetProfileUnavailableReason());
    }
}
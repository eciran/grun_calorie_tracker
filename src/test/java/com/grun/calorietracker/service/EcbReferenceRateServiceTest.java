package com.grun.calorietracker.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class EcbReferenceRateServiceTest {
    @Test void fallsBackWhenPrimaryFailsAndCachesSuccess() {
        var calls = new java.util.ArrayList<String>();
        var date = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        var service = new EcbReferenceRateService(uri -> {
            calls.add(uri.getHost());
            if (uri.getHost().equals("www.ecb.europa.eu")) throw new java.io.IOException("unavailable");
            return ("{\"date\":\"" + date + "\",\"base\":\"USD\",\"quote\":\"EUR\",\"rate\":0.8}").getBytes(StandardCharsets.UTF_8);
        });
        assertEquals("Frankfurter (ECB)", service.latest().source());
        assertEquals(0.8, service.latest().usdToEur().doubleValue());
        assertEquals(2, calls.size());
    }
    @Test void failsClosedWhenBothProvidersFail() {
        var service = new EcbReferenceRateService(uri -> { throw new java.io.IOException("offline"); });
        assertThrows(org.springframework.web.server.ResponseStatusException.class, service::latest);
    }
    @Test void rejectsWrongPairFromBackup() {
        assertThrows(IllegalArgumentException.class, () -> EcbReferenceRateService.parseBackup("{\"date\":\"2026-09-18\",\"base\":\"EUR\",\"quote\":\"USD\",\"rate\":1.25}".getBytes(StandardCharsets.UTF_8), Instant.parse("2026-09-20T12:00:00Z")));
    }
    @Test void convertsUsdPerEuroInTheCorrectDirection() throws Exception {
        var rate = EcbReferenceRateService.parse("<Envelope><Cube><Cube time='2026-09-18'><Cube currency='USD' rate='1.25'/></Cube></Cube></Envelope>".getBytes(StandardCharsets.UTF_8), Instant.parse("2026-09-20T12:00:00Z"));
        assertEquals(0.8, rate.usdToEur().doubleValue(), 0.00000001);
        assertEquals("2026-09-18", rate.rateDate().toString());
    }
    @Test void rejectsMissingAndZeroRates() {
        for (String body : new String[]{"<Cube time='2026-09-18'/>", "<Cube time='2026-09-18'><Cube currency='USD' rate='0'/></Cube>"}) {
            assertThrows(IllegalArgumentException.class, () -> EcbReferenceRateService.parse(body.getBytes(StandardCharsets.UTF_8), Instant.parse("2026-09-20T12:00:00Z")));
        }
    }
}

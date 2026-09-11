package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRefundAuditMigrationContractTest {

    @Test
    void migrationsStoreRefundLifecycleCreditRevocationConsentAndEvidence() throws Exception {
        String refundAudit = normalized("V253__harden_subscription_refund_audit.sql");
        String consent = normalized("V254__add_apple_refund_consumption_consent.sql");
        String evidence = normalized("V255__add_subscription_refund_evidence_snapshot.sql");

        assertTrue(refundAudit.contains("cancel_reason"));
        assertTrue(refundAudit.contains("expiration_reason"));
        assertTrue(refundAudit.contains("revoked_by_event_id"));
        assertTrue(refundAudit.contains("revocation_reason"));
        assertTrue(consent.contains("apple_refund_consumption_sharing"));
        assertTrue(evidence.contains("apple_refund_consent_status"));
        assertTrue(evidence.contains("plan_used_snapshot"));
        assertTrue(evidence.contains("entitlement_delivered_snapshot"));
        assertTrue(evidence.contains("cancel_reason = 'customer_support'"));
        assertTrue(evidence.contains("event_type = 'refund_reversed'"));
    }

    private String normalized(String migration) throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration", migration))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatWebhookEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreSubscriptionOwnershipService {
    public record OwnershipEvidence(Long ownerUserId, boolean requiresReview) {}

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = IllegalArgumentException.class)
    public void assertOwner(Long userId, RevenueCatWebhookEventDto.Event event) {
        String store = required(event.getStore(), "store").toUpperCase(Locale.ROOT);
        String environment = required(event.getEnvironment(), "environment").toUpperCase(Locale.ROOT);
        if (!environment.equals("SANDBOX") && !environment.equals("PRODUCTION")) {
            throw new IllegalArgumentException("Subscription environment is invalid.");
        }
        String chain = required(event.getOriginalTransactionId(), "original transaction");
        String eventId = required(event.getId(), "event id");
        // The unique insert serializes first claims across different users, not just one user's lock.
        jdbc.update("""
                INSERT INTO store_subscription_ownership
                    (store, environment, original_transaction_id, owner_user_id, first_event_id)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, store, environment, chain, userId, eventId);
        Boolean allowed = jdbc.queryForObject("""
                SELECT owner_user_id = ? AND NOT requires_review
                FROM store_subscription_ownership
                WHERE store = ? AND environment = ? AND original_transaction_id = ?
                FOR UPDATE
                """, Boolean.class, userId, store, environment, chain);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new IllegalArgumentException("SUBSCRIPTION_OWNERSHIP_CONFLICT: store subscription belongs to another GRUN account or requires historical ownership review.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipEvidence> findOwnership(String store, String environment, String originalTransactionId) {
        if (store == null || store.isBlank() || environment == null || environment.isBlank()
                || originalTransactionId == null || originalTransactionId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT owner_user_id, requires_review
                        FROM store_subscription_ownership
                        WHERE store = ? AND environment = ? AND original_transaction_id = ?
                        """,
                (rs, row) -> new OwnershipEvidence(rs.getLong("owner_user_id"), rs.getBoolean("requires_review")),
                store.trim().toUpperCase(Locale.ROOT), environment.trim().toUpperCase(Locale.ROOT),
                originalTransactionId.trim()).stream().findFirst();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing subscription " + field + ".");
        return value.trim();
    }
}

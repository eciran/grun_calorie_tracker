package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.SubscriptionPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SubscriptionAllocationIdentity {
    private SubscriptionAllocationIdentity() { }
    public static String of(String environment, String store, SubscriptionPlan plan,
                            String product, String transaction, long purchasedAt) {
        String canonical = String.join("|", normalize(environment), normalize(store), plan.name(),
                normalize(product), normalize(transaction), String.valueOf(purchasedAt));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

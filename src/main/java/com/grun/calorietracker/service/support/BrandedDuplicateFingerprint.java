package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class BrandedDuplicateFingerprint {
    private BrandedDuplicateFingerprint() {
    }

    public static String fromCandidates(List<BrandedProductDuplicateCandidateDto> candidates) {
        String evidence = candidates.stream()
                .sorted(Comparator.comparing(BrandedProductDuplicateCandidateDto::productId))
                .map(candidate -> candidate.productId() + "|" + value(candidate.barcode()) + "|"
                        + value(candidate.sourceKey()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}

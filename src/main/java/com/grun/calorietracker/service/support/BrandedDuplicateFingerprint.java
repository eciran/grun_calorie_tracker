package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class BrandedDuplicateFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();
    private BrandedDuplicateFingerprint() {
    }

    public static String fromCandidates(List<BrandedProductDuplicateCandidateDto> candidates) {
        return hash(List.of("R6_DTO_ONLY_V2", ordered(candidates)));
    }

    public static String fromCandidates(List<BrandedProductDuplicateCandidateDto> candidates,
                                        Map<Long, String> reviewProfiles) {
        List<BrandedProductDuplicateCandidateDto> ordered = ordered(candidates);
        if (reviewProfiles == null || reviewProfiles.size() != ordered.size()
                || ordered.stream().anyMatch(candidate -> {
                    String profile = reviewProfiles.get(candidate.productId());
                    return profile == null || !profile.matches("[a-f0-9]{64}");
                })) {
            throw new IllegalArgumentException("Complete current duplicate review profiles are required.");
        }
        return hash(List.of("R6_FULL_PROFILE_V2", ordered, new TreeMap<>(reviewProfiles)));
    }

    private static List<BrandedProductDuplicateCandidateDto> ordered(
            List<BrandedProductDuplicateCandidateDto> candidates) {
        if (candidates == null || candidates.isEmpty()
                || candidates.stream().anyMatch(candidate -> candidate == null || candidate.productId() == null)
                || candidates.stream().map(BrandedProductDuplicateCandidateDto::productId).distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("Unique current candidate identities are required.");
        }
        return candidates.stream().sorted(Comparator.comparing(BrandedProductDuplicateCandidateDto::productId)).toList();
    }

    private static String hash(Object evidence) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(evidence)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Duplicate review fingerprint could not be computed.", exception);
        }
    }
}

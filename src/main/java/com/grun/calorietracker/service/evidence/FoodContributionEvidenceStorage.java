package com.grun.calorietracker.service.evidence;

public interface FoodContributionEvidenceStorage {
    StoredEvidence store(Long userId, String barcode, InspectedEvidence evidence);
    EvidenceContent load(String storageKey);
    void delete(String storageKey);

    record InspectedEvidence(byte[] bytes, String contentType, String extension, String checksum) {
    }

    record StoredEvidence(String storageKey, String checksum, String contentType, long sizeBytes) {
    }

    record EvidenceContent(byte[] bytes, String contentType) {
    }
}

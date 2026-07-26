package com.grun.calorietracker.service.evidence;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class S3FoodContributionEvidenceStorage implements FoodContributionEvidenceStorage {
    private final FoodContributionStorageProperties properties;
    private final S3Client s3Client;

    @Override
    public StoredEvidence store(Long userId, String barcode, InspectedEvidence evidence) {
        validateConfiguration();
        String key = prefix() + "/u" + userId + "/" + barcode + "-" + UUID.randomUUID() + evidence.extension();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .contentType(evidence.contentType())
                .contentLength((long) evidence.bytes().length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(Map.of("sha256", evidence.checksum(), "barcode", barcode))
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(evidence.bytes()));
        return new StoredEvidence(key, evidence.checksum(), evidence.contentType(), evidence.bytes().length);
    }

    @Override
    public EvidenceContent load(String storageKey) {
        validateConfiguration();
        ensureManagedKey(storageKey);
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.getS3().getBucket()).key(storageKey).build());
        return new EvidenceContent(response.asByteArray(), response.response().contentType());
    }

    @Override
    public void delete(String storageKey) {
        validateConfiguration();
        ensureManagedKey(storageKey);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getS3().getBucket()).key(storageKey).build());
    }

    private void validateConfiguration() {
        if (properties.getS3().getBucket() == null || properties.getS3().getBucket().isBlank()) {
            throw new IllegalStateException("Food contribution S3 bucket is not configured.");
        }
    }

    private String prefix() {
        String value = properties.getS3().getPrefix() == null ? "" : properties.getS3().getPrefix().trim();
        value = value.replaceAll("^/+|/+$", "");
        return value.isBlank() ? "product-contributions" : value;
    }

    private void ensureManagedKey(String storageKey) {
        if (storageKey == null || !storageKey.startsWith(prefix() + "/") || storageKey.contains("..")) {
            throw new IllegalArgumentException("Product label storage key is invalid.");
        }
    }
}

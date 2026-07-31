package com.grun.calorietracker.service.evidence;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class S3FoodProductDirectUploadStorage implements FoodProductDirectUploadStorage {
    private final FoodContributionStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Override
    public UploadAuthorization authorizeUpload(UploadObject object, Duration ttl) {
        ensureManagedKey(object.storageKey());
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket())
                .key(object.storageKey())
                .contentType(object.contentType())
                .contentLength(object.sizeBytes())
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(Map.of("sha256", object.sha256()))
                .build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build());
        Map<String, String> headers = signed.signedHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new UploadAuthorization(java.net.URI.create(signed.url().toString()), "PUT", Map.copyOf(headers), Instant.now().plus(ttl));
    }

    @Override
    public ReadAuthorization authorizeRead(String storageKey, Duration ttl) {
        ensureManagedKey(storageKey);
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket()).key(storageKey).build())
                .build());
        return new ReadAuthorization(java.net.URI.create(signed.url().toString()), Instant.now().plus(ttl));
    }

    @Override
    public StoredObject inspect(String storageKey) {
        ensureManagedKey(storageKey);
        var response = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(storageKey).build());
        return new StoredObject(
                storageKey,
                response.contentType(),
                response.contentLength(),
                response.metadata().get("sha256")
        );
    }

    @Override
    public byte[] readBounded(String storageKey, long maximumBytes) {
        ensureManagedKey(storageKey);
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("Maximum evidence read size must be positive.");
        }
        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket()).key(storageKey).build());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximumBytes) {
                    throw new IllegalArgumentException("Stored product evidence exceeds the configured read limit.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stored product evidence could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        ensureManagedKey(storageKey);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(storageKey).build());
    }

    private String bucket() {
        return properties.getS3().getBucket();
    }

    private void ensureManagedKey(String storageKey) {
        String prefix = normalizedPrefix();
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
                || storageKey.startsWith("/") || !storageKey.startsWith(prefix + "/")) {
            throw new IllegalArgumentException("Product evidence storage key is invalid.");
        }
    }

    private String normalizedPrefix() {
        String value = properties.getS3().getPrefix();
        if (value == null) {
            return "pending/product-intakes";
        }
        String normalized = value.trim().replaceAll("^/+|/+$", "");
        return normalized.isBlank() ? "pending/product-intakes" : normalized;
    }
}

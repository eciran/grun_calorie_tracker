package com.grun.calorietracker.service.media;

import com.grun.calorietracker.config.MediaStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "grun.media-storage", name = "provider", havingValue = "S3")
public class S3MediaObjectStorage implements MediaObjectStorage {
    private final MediaStorageProperties properties;
    private final MediaStorageKeyPolicy keyPolicy;
    private final S3Client s3Client;

    public S3MediaObjectStorage(MediaStorageProperties properties, MediaStorageKeyPolicy keyPolicy) {
        validate(properties);
        this.properties = properties;
        this.keyPolicy = keyPolicy;
        this.s3Client = buildClient(properties);
    }

    @Override
    public StoredMediaObject store(String storageKey, byte[] content, String contentType, String sha256) {
        keyPolicy.requireManaged(storageKey);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Media content is required.");
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .contentType(contentType)
                .contentLength((long) content.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(sha256 == null ? Map.of() : Map.of("sha256", sha256))
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        return new StoredMediaObject(storageKey, contentType, content.length, sha256);
    }

    @Override
    public StoredMediaObject inspect(String storageKey) {
        keyPolicy.requireManaged(storageKey);
        var response = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(storageKey).build());
        return new StoredMediaObject(storageKey, response.contentType(), response.contentLength(), response.metadata().get("sha256"));
    }

    @Override
    public byte[] readBounded(String storageKey, long maximumBytes) {
        keyPolicy.requireManaged(storageKey);
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("Maximum media read size must be positive.");
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
                    throw new IllegalArgumentException("Stored media exceeds the configured read limit.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media object could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        keyPolicy.requireManaged(storageKey);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(storageKey).build());
    }

    private S3Client buildClient(MediaStorageProperties value) {
        var builder = S3Client.builder()
                .region(Region.of(value.getS3().getRegion().trim()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(value.getS3().isPathStyleAccess())
                        .build());
        if (hasText(value.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(value.getS3().getEndpoint().trim()));
        }
        if (hasText(value.getS3().getAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    value.getS3().getAccessKey().trim(),
                    value.getS3().getSecretKey().trim()
            )));
        }
        return builder.build();
    }

    private void validate(MediaStorageProperties value) {
        if (!hasText(value.getS3().getBucket())) {
            throw new IllegalStateException("GRUN_MEDIA_S3_BUCKET is required when shared S3 media storage is enabled.");
        }
        if (!hasText(value.getS3().getRegion())) {
            throw new IllegalStateException("GRUN_MEDIA_S3_REGION is required when shared S3 media storage is enabled.");
        }
        boolean accessKey = hasText(value.getS3().getAccessKey());
        boolean secretKey = hasText(value.getS3().getSecretKey());
        if (accessKey != secretKey) {
            throw new IllegalStateException("Shared S3 media access key and secret key must be configured together.");
        }
    }

    private String bucket() {
        return properties.getS3().getBucket();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FoodProductEvidenceBulkWriter {

    private static final int INSERT_CHUNK_SIZE = 500;
    private static final String COLUMNS = "food_item_id, provider, external_id, field_name, numeric_value, basis, "
            + "confidence_score, observed_at, created_at, fingerprint, source_version, reviewer_identity";
    private static final String VALUE_PLACEHOLDERS = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public boolean supportsConflictSafeBulkInsert() {
        Boolean supported = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql"));
        return Boolean.TRUE.equals(supported);
    }

    public int insertIgnoringFingerprintConflicts(List<FoodProductSourceEvidenceEntity> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        LocalDateTime createdAt = LocalDateTime.now();
        for (int start = 0; start < evidence.size(); start += INSERT_CHUNK_SIZE) {
            List<FoodProductSourceEvidenceEntity> chunk = evidence.subList(
                    start,
                    Math.min(start + INSERT_CHUNK_SIZE, evidence.size())
            );
            String sql = "insert into food_product_source_evidence (" + COLUMNS + ") values "
                    + String.join(",", Collections.nCopies(chunk.size(), VALUE_PLACEHOLDERS))
                    + " on conflict (fingerprint) do nothing";
            List<Object> parameters = new ArrayList<>(chunk.size() * 12);
            for (FoodProductSourceEvidenceEntity value : chunk) {
                parameters.add(value.getFoodItem().getId());
                parameters.add(value.getProvider().name());
                parameters.add(value.getExternalId());
                parameters.add(value.getFieldName().name());
                parameters.add(value.getNumericValue());
                parameters.add(value.getBasis().name());
                parameters.add(value.getConfidenceScore());
                parameters.add(value.getObservedAt());
                parameters.add(value.getCreatedAt() == null ? createdAt : value.getCreatedAt());
                parameters.add(value.getFingerprint());
                parameters.add(value.getSourceVersion());
                parameters.add(value.getReviewerIdentity());
            }
            inserted += jdbcTemplate.update(sql, parameters.toArray());
        }
        return inserted;
    }
}
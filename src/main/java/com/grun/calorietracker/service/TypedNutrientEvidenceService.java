package com.grun.calorietracker.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.TypedNutrientEvidenceRequest;
import com.grun.calorietracker.dto.TypedSourceObservationRequest;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.service.support.FoodProductDependencySnapshot;
import com.grun.calorietracker.service.support.TypedSourceObservationContract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A reviewed source observation, not certification or replacement of legacy scalar units. */
@Service
public class TypedNutrientEvidenceService {
    private com.grun.calorietracker.service.support.ExternalFoodImportFence externalFoodImportFence;

    @org.springframework.beans.factory.annotation.Autowired
    void setExternalFoodImportFence(com.grun.calorietracker.service.support.ExternalFoodImportFence fence) {
        this.externalFoodImportFence = fence;
    }
    private static final String TRUST = "ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JsonNode contract;
    private final boolean writesEnabled;
    private final TypedSourceObservationContract observations;

    public TypedNutrientEvidenceService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${grun.product.typed-evidence-write-enabled:false}") boolean writesEnabled) {
        this.jdbc = jdbc;
        this.json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.writesEnabled = writesEnabled;
        this.observations = new TypedSourceObservationContract(json);
        try (var stream = getClass().getResourceAsStream("/catalog/usda-typed-nutrient-contract-r5-v1.json")) {
            contract = json.readTree(stream);
            require("usda-typed-nutrient-contract-r5-v1".equals(contract.path("version").asText())
                    && "PER_100G".equals(contract.path("basis").asText()), "Unsupported typed contract");
            contract.path("policy").forEach(value -> require(value.isBoolean() && !value.asBoolean(),
                    "Typed contract must not enable inference or live application"));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load typed nutrient contract", e);
        }
    }

    public record Comparison(String currentFingerprint, String sourceKey, String field,
            Integer nutrientId, String nutrientNumber, String sourceUnit, String semantic, String canonicalUnit,
            BigDecimal sourceAmount, String currentScalar, String scalarComparison,
            List<String> holds, boolean appendEnabled, boolean legacyUnitAttested,
            boolean nutritionWriteAllowed, String trustState, List<String> releaseHolds,
            String snapshotScope, FoodProductDependencySnapshot.Snapshot dependencies,
            String contractVersion, String provider, String sourceBasis, String sourceField,
            BigDecimal declaredSourceAmount, boolean observationOnly,
            String currentScalarUnit, String canonicalCurrentComparison) {}
    public record AppendResult(long evidenceId, boolean created, Comparison comparison) {}
    public record StoredComparison(long evidenceId, boolean currentBeforeImageMatches, Comparison comparison,
            String storedSnapshotScope, boolean dependencyRebaseRequired) {}

    @Transactional(readOnly = true, timeout = 15, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Comparison preview(long productId, TypedNutrientEvidenceRequest request) {
        return evaluate(productId, request, false).comparison();
    }

    @Transactional(readOnly = true, timeout = 15, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Comparison previewObservation(long productId, TypedSourceObservationRequest request) {
        require(request != null, "Source observation request required");
        return evaluate(productId, request.evidenceRequest(), false, observation(request)).comparison();
    }

    @Transactional(timeout = 15)
    public AppendResult appendObservation(long productId, TypedSourceObservationRequest request, String reviewer) {
        var fact = observation(request);
        return appendEvaluated(productId, request.evidenceRequest(), reviewer, fact);
    }

    @Transactional(timeout = 15)
    public AppendResult append(long productId, TypedNutrientEvidenceRequest request, String reviewer) {
        return appendEvaluated(productId, request, reviewer, null);
    }

    private AppendResult appendEvaluated(long productId, TypedNutrientEvidenceRequest request, String reviewer,
            TypedSourceObservationContract.Fact fact) {
        require(writesEnabled, "Typed evidence append is disabled; no catalog release is authorized");
        require(reviewer != null && !reviewer.isBlank() && reviewer.length() <= 255, "Authenticated reviewer required");
        require(request.approved() && request.approvalReason() != null
                && request.approvalReason().trim().length() >= 10 && request.approvalReason().length() <= 1000,
                "Explicit reviewed-source approval and reason required");
        // The parent FOR UPDATE lock serializes appenders and blocks concurrent FK evidence insertion.
        if (externalFoodImportFence != null) externalFoodImportFence.acquire();
        var evaluated = evaluate(productId, request, true, fact);
        var comparison = evaluated.comparison();
        if (!Objects.equals(request.expectedCurrentFingerprint(), comparison.currentFingerprint())) {
            throw new RequestConflictException("Current product, evidence or declared dependencies changed; preview again");
        }
        // V2 explicitly stores a source-only observation, not a decision about a differing legacy scalar.
        // Numeric review remains visible; identity, stale-source, dependency and prior-observation holds still block append.
        require(comparison.holds().stream().allMatch(hold -> fact != null && "CURRENT_NUMERIC_REVIEW_REQUIRED".equals(hold)),
                "Typed evidence blocked: " + String.join(",", comparison.holds()));
        String fingerprint = sha(encode(List.of(productId, comparison.field(), request.sourceSha256(),
                request.observedAt().toString(), comparison.currentFingerprint(), comparison.contractVersion())));
        int inserted;
        if (fact != null) {
            inserted = jdbc.update("""
                    INSERT INTO food_typed_nutrient_evidence
                    (food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,source_unit,semantic,
                     canonical_unit,basis,source_amount,source_json,source_sha256,observed_at,before_json,before_sha256,
                     fingerprint,reviewer_identity,approval_reason,contract_version,observation_only,declared_source_amount,source_field)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS timestamptz),?,?,?,?,?,?,true,?,?)
                    ON CONFLICT (fingerprint) DO NOTHING
                    """, productId, fact.provider(), fact.sourceKey(), fact.field(), fact.nutrientId(), fact.nutrientNumber(),
                    fact.sourceUnit(), fact.semantic(), fact.canonicalUnit(),
                    "PER_100ML".equals(fact.basis()) ? "PER_100_ML" : "PER_100_G", fact.canonicalAmount(),
                    request.sourceJson(), request.sourceSha256(), request.observedAt().toString(), evaluated.snapshot(),
                    comparison.currentFingerprint(), fingerprint, reviewer.trim(), request.approvalReason().trim(),
                    TypedSourceObservationContract.VERSION, fact.declaredAmount(), fact.sourceField());
        } else inserted = jdbc.update("""
                INSERT INTO food_typed_nutrient_evidence
                (food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,source_unit,semantic,
                 canonical_unit,basis,source_amount,source_json,source_sha256,observed_at,before_json,before_sha256,
                 fingerprint,reviewer_identity,approval_reason)
                VALUES (?,'USDA_FOODDATA',?,?,?,?,?,?,?,'PER_100_G',?,?,?,CAST(? AS timestamptz),?,?,?,?,?)
                ON CONFLICT (fingerprint) DO NOTHING
                """, productId, comparison.sourceKey(), comparison.field(), comparison.nutrientId(),
                comparison.nutrientNumber(), comparison.sourceUnit(), comparison.semantic(), comparison.canonicalUnit(),
                comparison.sourceAmount(), request.sourceJson(), request.sourceSha256(), request.observedAt().toString(),
                evaluated.snapshot(), comparison.currentFingerprint(), fingerprint, reviewer.trim(), request.approvalReason().trim());
        Long id = jdbc.queryForObject("SELECT id FROM food_typed_nutrient_evidence WHERE fingerprint = ?", Long.class, fingerprint);
        return new AppendResult(Objects.requireNonNull(id), inserted == 1, comparison);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(long productId) {
        // No source body or private reviewer/before-image is exposed in this bounded overview.
        return jdbc.queryForList("""
                SELECT id, field_name, nutrient_id, source_unit, semantic, canonical_unit, source_amount, provider, external_id,
                    basis, source_sha256, before_sha256, observed_at, created_at, trust_state, legacy_unit_attested,
                    contract_version, observation_only, declared_source_amount, source_field
                FROM food_typed_nutrient_evidence WHERE food_item_id = ? ORDER BY id DESC LIMIT 100
                """, productId);
    }

    @Transactional(readOnly = true, timeout = 15, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public StoredComparison compareStored(long productId, long evidenceId) {
        var rows = jdbc.queryForList("SELECT field_name,source_json,source_sha256,observed_at,before_sha256,before_json,contract_version,provider"
                + " FROM food_typed_nutrient_evidence WHERE id = ? AND food_item_id = ?", evidenceId, productId);
        if (rows.size() != 1) throw new ProductNotFoundException("Product evidence not found");
        var row = rows.get(0);
        var request = new TypedNutrientEvidenceRequest(row.get("field_name").toString(),
                row.get("source_json").toString(), row.get("source_sha256").toString(),
                ((java.sql.Timestamp) row.get("observed_at")).toInstant(), null, false, null);
        var version = row.get("contract_version");
        if (version != null && !TypedSourceObservationContract.VERSION.equals(version))
            throw new RequestConflictException("Unknown historical source contract; explicit review required");
        var fact = version == null ? null : observation(new TypedSourceObservationRequest(version.toString(),
                row.get("provider").toString(), request.field(), request.sourceJson(), request.sourceSha256(),
                request.observedAt(), null, false, null));
        var evaluated = evaluate(productId, request, false, fact);
        var comparison = evaluated.comparison();
        String storedScope = parse(row.get("before_json").toString()).path("scope").asText();
        boolean old = "PARENT_AND_LEGACY_EVIDENCE_V1".equals(storedScope);
        if (!old && !FoodProductDependencySnapshot.SCOPE.equals(storedScope))
            throw new RequestConflictException("Unknown historical evidence snapshot scope; explicit review required");
        boolean matches = (old ? evaluated.legacyFingerprint() : comparison.currentFingerprint()).equals(row.get("before_sha256"));
        return new StoredComparison(evidenceId, matches, comparison, storedScope,
                old || !matches || !comparison.dependencies().complete());
    }

    private record Evaluation(Comparison comparison, String snapshot, String legacyFingerprint) {}

    private Evaluation evaluate(long id, TypedNutrientEvidenceRequest request, boolean lock) {
        return evaluate(id, request, lock, null);
    }

    private TypedSourceObservationContract.Fact observation(TypedSourceObservationRequest request) {
        require(request != null && request.sourceJson() != null && request.sourceJson().length() <= 1000000,
                "Bounded source body required");
        require(request.sourceSha256() != null && request.sourceSha256().equals(sha(request.sourceJson())), "Source receipt/body hash mismatch");
        return observations.read(request.contractVersion(), request.provider(), request.field(), parse(request.sourceJson()));
    }

    private Evaluation evaluate(long id, TypedNutrientEvidenceRequest request, boolean lock, TypedSourceObservationContract.Fact fact) {
        require(request != null && request.sourceJson() != null && request.sourceJson().length() <= 1000000,
                "Bounded complete source body required");
        require(request.observedAt() != null && request.sourceSha256() != null
                && request.sourceSha256().equals(sha(request.sourceJson())), "Source receipt/body hash mismatch");
        JsonNode body = parse(request.sourceJson());
        JsonNode spec = contract.path("fields").path(request.field() == null ? "" : request.field());
        if (fact == null) {
          require(!spec.isMissingNode(), "Unsupported typed nutrient field");
          require(body.path("fdcId").isIntegralNumber() && body.path("fdcId").canConvertToLong() && body.path("fdcId").asLong() > 0
                && List.of("Foundation", "SR Legacy").contains(body.path("dataType").asText())
                && body.path("description").isTextual() && !body.path("description").asText().isBlank()
                && body.path("foodNutrients").isArray(), "Complete exact Foundation/SR Legacy food required");
        }
        String sourceKey = fact == null ? "USDA_FOODDATA:fdc:" + body.path("fdcId").asLong() : fact.sourceKey();
        String provider = fact == null ? "USDA_FOODDATA" : fact.provider();
        var rows = jdbc.queryForList("SELECT * FROM food_items WHERE id = ?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.size() != 1) throw new ProductNotFoundException("Product not found");
        var parent = new TreeMap<String, Object>(rows.get(0));
        // Mutable popularity counters are not nutrition/identity evidence and must not cause false rebases.
        parent.remove("usage_count");
        parent.remove("search_selection_count");
        var legacy = jdbc.queryForList("SELECT * FROM food_product_source_evidence WHERE food_item_id = ? ORDER BY id"
                + (lock ? " FOR SHARE" : ""), id);
        String legacySnapshot = encode(Map.of("scope", "PARENT_AND_LEGACY_EVIDENCE_V1", "food", stable(parent),
                "legacyEvidence", legacy.stream().map(this::stable).toList()));
        var dependencies = new FoodProductDependencySnapshot(jdbc).capture(id, lock);
        String snapshot = encode(Map.of("scope", FoodProductDependencySnapshot.SCOPE, "food", stable(parent),
                "legacyEvidence", legacy.stream().map(this::stable).toList(), "dependencies", dependencies));
        String before = sha(snapshot);
        List<String> holds = new ArrayList<>();
        if (!dependencies.complete()) holds.add("DEPENDENCY_REBASE_INCOMPLETE");
        if (!sourceKey.equals(parent.get("source_key")) || !provider.equals(parent.get("data_source")))
            holds.add("SOURCE_IDENTITY_MISMATCH");
        boolean usda = "USDA_FOODDATA".equals(provider);
        if (usda && nonblank(parent.get("source_data_type")) && !body.path("dataType").asText().equals(parent.get("source_data_type")))
            holds.add("SOURCE_DATA_TYPE_MISMATCH");
        boolean catalogValid = usda
                ? List.of("GENERIC_INGREDIENT", "LOCAL_DISH").contains(String.valueOf(parent.get("catalog_type")))
                    && !nonblank(parent.get("brand")) && !nonblank(parent.get("barcode")) && !nonblank(parent.get("normalized_barcode"))
                : "BRANDED_PRODUCT".equals(parent.get("catalog_type"));
        if (!usda) {
            String code = sourceKey.substring("barcode:".length());
            if (!code.equals(parent.get("normalized_barcode"))
                    || (nonblank(parent.get("barcode")) && !code.equals(parent.get("barcode")))) holds.add("SOURCE_BARCODE_MISMATCH");
        }
        if (!catalogValid || !"PUBLISHED".equals(parent.get("publication_status")) || !Boolean.FALSE.equals(parent.get("is_custom")))
            holds.add("CATALOG_IDENTITY_REVIEW_REQUIRED");
        String basis = fact == null ? "PER_100G" : fact.basis();
        if (!basis.equals(parent.get("nutrition_reference_unit"))
                || !"SOURCE_REPORTED".equals(parent.get("nutrition_basis"))) holds.add("NUTRITION_BASIS_REVIEW_REQUIRED");
        Instant now = Instant.now();
        if (request.observedAt().isAfter(now) || request.observedAt().isBefore(now.minus(Duration.ofHours(24))))
            holds.add("SOURCE_RECEIPT_EXPIRED_OR_FUTURE");
        BigDecimal amount;
        String unit, semantic, canonicalUnit, nutrientNumber;
        Integer nutrientId;
        if (fact == null) {
          List<JsonNode> nutrients = new ArrayList<>();
        body.path("foodNutrients").forEach(node -> {
            if (node.path("nutrient").path("id").isIntegralNumber() && node.path("nutrient").path("id").canConvertToInt()
                    && node.path("nutrient").path("id").asInt() == spec.path("nutrientId").asInt()) nutrients.add(node);
        });
        require(nutrients.size() == 1, "Exactly one source nutrient ID required; no IU/name/number fallback");
        var nutrient = nutrients.get(0);
        unit = nutrient.path("nutrient").path("unitName").asText().toLowerCase(java.util.Locale.ROOT)
                .replace('\u00b5', 'u').replace('\u03bc', 'u');
        require(spec.path("sourceUnit").asText().equals(unit)
                && spec.path("nutrientNumber").asText().equals(nutrient.path("nutrient").path("number").asText())
                && nutrient.path("amount").isNumber(), "Exact nutrient unit, number and numeric amount required");
        if ("vitamin_a".equals(request.field())) require("Vitamin A, RAE".equals(nutrient.path("nutrient").path("name").asText()),
                "Exact Vitamin A RAE form required");
        try { amount = new BigDecimal(nutrient.path("amount").asText()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Finite source amount required"); }
        require(amount.signum() >= 0, "Nonnegative source amount required");
        nutrientId = spec.path("nutrientId").asInt(); nutrientNumber = spec.path("nutrientNumber").asText();
        semantic = spec.path("semantic").asText(); canonicalUnit = spec.path("canonicalUnit").asText();
        } else {
            amount = fact.canonicalAmount(); unit = fact.sourceUnit(); nutrientId = fact.nutrientId();
            nutrientNumber = fact.nutrientNumber(); semantic = fact.semantic(); canonicalUnit = fact.canonicalUnit();
        }
        String scalar = Objects.toString(parent.get(request.field()), null);
        String state = scalar == null ? "CURRENT_UNKNOWN" : "DIFFERS";
        if (scalar != null) {
            try { if (amount.compareTo(new BigDecimal(scalar)) == 0) state = "MATCH_SCALAR_ONLY"; }
            catch (NumberFormatException ignored) { state = "CURRENT_INVALID"; }
        }
        if (!"MATCH_SCALAR_ONLY".equals(state)) holds.add("CURRENT_NUMERIC_REVIEW_REQUIRED");
        var existing = jdbc.queryForList("SELECT semantic, canonical_unit, source_amount, provider, external_id, basis FROM food_typed_nutrient_evidence"
                + " WHERE food_item_id = ? AND field_name = ?", id, request.field());
        String storedBasis = "PER_100ML".equals(basis) ? "PER_100_ML" : "PER_100_G";
        if (existing.stream().anyMatch(row -> !semantic.equals(row.get("semantic"))
                || !canonicalUnit.equals(row.get("canonical_unit"))
                || !provider.equals(row.get("provider")) || !sourceKey.equals(row.get("external_id"))
                || !storedBasis.equals(row.get("basis"))
                || amount.compareTo(new BigDecimal(row.get("source_amount").toString())) != 0))
            holds.add("PRIOR_TYPED_OBSERVATION_CONFLICT_REQUIRES_REVIEW");
        return new Evaluation(new Comparison(before, sourceKey, request.field(), nutrientId,
                nutrientNumber, unit, semantic, canonicalUnit,
                amount, scalar, state, List.copyOf(holds), writesEnabled, false, false, TRUST,
                List.of("FULL_NUTRITION_COMPARISON_REQUIRED", "ALIAS_SERVING_REFERENCE_REBASE_REQUIRED",
                        "CONSOLIDATED_RELEASE_APPROVAL_REQUIRED"), FoodProductDependencySnapshot.SCOPE, dependencies,
                fact == null ? contract.path("version").asText() : TypedSourceObservationContract.VERSION,
                provider, basis, fact == null ? null : fact.sourceField(), fact == null ? amount : fact.declaredAmount(), fact != null,
                "UNATTESTED_LEGACY_UNIT", "NOT_COMPARABLE_WITHOUT_LEGACY_UNIT_ATTESTATION"),
                snapshot, sha(legacySnapshot));
    }

    private boolean nonblank(Object value) { return value != null && !value.toString().isBlank(); }

    private Map<String, Object> stable(Map<String, Object> row) {
        var result = new TreeMap<String, Object>();
        row.forEach((key, value) -> result.put(key, value == null ? null : value.toString()));
        return result;
    }

    private JsonNode parse(String value) {
        try (var parser = json.createParser(value)) {
            JsonNode node = json.readTree(parser);
            require(node != null && node.isObject() && parser.nextToken() == null, "One complete source JSON object required");
            return node;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid source JSON"); }
    }

    private String encode(Object value) {
        try { return json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot fingerprint evidence", e); }
    }

    private static String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
}

package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exact source schemas; conversion here describes the source, never legacy DB units. */
public final class TypedSourceObservationContract {
    public static final String VERSION = "typed-source-observation-r5-v2";
    private final JsonNode contract;

    public TypedSourceObservationContract(ObjectMapper json) {
        try (var stream = getClass().getResourceAsStream("/catalog/typed-source-observation-r5-v2.json")) {
            contract = json.readTree(stream);
            require(VERSION.equals(contract.path("version").asText())
                    && "SOURCE_OBSERVATION_ONLY".equals(contract.path("mode").asText()), "Unknown observation contract");
            contract.path("policy").forEach(v -> require(v.isBoolean() && !v.asBoolean(), "Unsafe observation policy"));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load observation contract", e); }
    }

    public record Fact(String provider, String sourceKey, String field, Integer nutrientId, String nutrientNumber,
            String sourceField, String sourceUnit, String semantic, String canonicalUnit, String basis,
            BigDecimal declaredAmount, BigDecimal canonicalAmount) {}

    public Fact read(String version, String provider, String field, JsonNode body) {
        require(VERSION.equals(version), "Explicit supported source-only contract version required");
        JsonNode spec = contract.path("fields").path(provider == null ? "" : provider).path(field == null ? "" : field);
        require(spec.isObject(), "Unsupported provider/field observation contract");
        if ("USDA_FOODDATA".equals(provider)) {
            require(body.path("fdcId").isIntegralNumber() && body.path("fdcId").canConvertToLong()
                    && body.path("fdcId").asLong() > 0
                    && List.of("Foundation", "SR Legacy").contains(body.path("dataType").asText())
                    && body.path("description").isTextual() && !body.path("description").asText().isBlank()
                    && body.path("foodNutrients").isArray(), "Complete Foundation/SR Legacy food required");
            var found = new ArrayList<JsonNode>();
            body.path("foodNutrients").forEach(n -> {
                JsonNode id = n.path("nutrient").path("id");
                if (id.isIntegralNumber() && id.canConvertToInt() && id.asInt() == spec.path("nutrientId").asInt()) found.add(n);
            });
            require(found.size() == 1, "Exactly one integral nutrient ID required; no fallback");
            var n = found.get(0); var meta = n.path("nutrient");
            String unit = meta.path("unitName").asText().toLowerCase(Locale.ROOT).replace('\u00b5', 'u').replace('\u03bc', 'u');
            require(spec.path("sourceUnit").asText().equals(unit)
                    && meta.path("number").isTextual() && spec.path("nutrientNumber").asText().equals(meta.path("number").asText())
                    && spec.path("sourceName").asText().equals(meta.path("name").asText()), "Exact nutrient number/form/unit required");
            BigDecimal amount = amount(n.path("amount"));
            return new Fact(provider, "USDA_FOODDATA:fdc:" + body.path("fdcId").asLong(), field,
                    spec.path("nutrientId").asInt(), spec.path("nutrientNumber").asText(), null, unit,
                    spec.path("semantic").asText(), spec.path("canonicalUnit").asText(), "PER_100G", amount, amount);
        }
        String code = body.path("code").asText();
        require(body.path("code").isTextual() && validGtin(code), "Exact valid string GTIN required");
        String per = body.path("nutrition_data_per").asText();
        require(List.of("100g", "100ml").contains(per), "Explicit native source basis required");
        var n = body.path("nutriments");
        require(n.isObject() && "g".equals(n.path(field + "_unit").asText().toLowerCase(Locale.ROOT)), "Explicit source grams required");
        var modifier = n.path(field + "_modifier");
        require(modifier.isMissingNode() || modifier.isNull() || (modifier.isTextual() && modifier.asText().isEmpty()), "Inequality is not an exact observation");
        BigDecimal amount = amount(n.path(spec.path("sourceField").asText()));
        for (String key : List.of(field, field + "_value")) {
            if (n.has(key)) require(amount.compareTo(amount(n.path(key))) == 0, "Contradictory OFF display/source value");
        }
        return new Fact(provider, "barcode:" + code, field, null, null, spec.path("sourceField").asText(), "g",
                spec.path("semantic").asText(), spec.path("canonicalUnit").asText(), "100ml".equals(per) ? "PER_100ML" : "PER_100G",
                amount, amount.multiply(spec.path("factor").decimalValue()));
    }

    private static BigDecimal amount(JsonNode node) {
        require(node.isNumber(), "Explicit numeric source amount required; missing is not zero");
        BigDecimal value;
        try { value = new BigDecimal(node.asText()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Finite source amount required"); }
        require(value.signum() >= 0 && value.precision() <= 100 && Math.abs((long) value.scale()) <= 100
                        && (long) value.precision() - value.scale() <= 100,
                "Bounded nonnegative source amount required");
        return value;
    }

    public static boolean validGtin(String code) {
        if (code == null || !code.matches("(?:[0-9]{8}|[0-9]{12,14})")) return false;
        int sum = 0;
        for (int i = code.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) sum += (code.charAt(i) - '0') * weight;
        return (10 - sum % 10) % 10 == code.charAt(code.length() - 1) - '0';
    }
    private static void require(boolean valid, String reason) { if (!valid) throw new IllegalArgumentException(reason); }
}

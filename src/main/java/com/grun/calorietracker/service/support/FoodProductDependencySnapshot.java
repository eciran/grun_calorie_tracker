package com.grun.calorietracker.service.support;

import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded fingerprints of declared product/option FK dependents, never user-history payloads. */
public final class FoodProductDependencySnapshot {
    public static final String SCOPE = "PRODUCT_PARENT_LEGACY_AND_FK_DEPENDENCIES_V2";
    public static final int MAX_PATHS = 64;
    public static final int MAX_ROWS = 4096;
    public static final String DISCOVER_SQL = """
            SELECT c.conname AS constraint_name, n.nspname AS table_schema, t.relname AS table_name,
                a.attname AS column_name, target.relname AS target_table, ta.attname AS target_column,
                cardinality(c.conkey) AS key_columns, cardinality(c.confkey) AS target_columns,
                c.convalidated AS validated, c.confdeltype::text AS delete_action,
                c.confupdtype::text AS update_action, c.condeferrable AS deferrable,
                c.condeferred AS deferred, t.relkind::text AS relation_kind,
                format_type(a.atttypid,a.atttypmod) AS column_type
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace
            JOIN pg_class target ON target.oid=c.confrelid
            LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=c.conkey[1]
            LEFT JOIN pg_attribute ta ON ta.attrelid=target.oid AND ta.attnum=c.confkey[1]
            WHERE c.contype='f' AND c.confrelid IN ('public.food_items'::regclass,'public.food_item_serving_options'::regclass)
            ORDER BY n.nspname,t.relname,c.conname
            LIMIT 65
            """;
    private static final Set<String> LEDGERS = Set.of("food_product_source_evidence", "food_typed_nutrient_evidence");
    private static final Set<String> REQUIRED = readRequiredPaths();
    private final JdbcTemplate jdbc;

    public FoodProductDependencySnapshot(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Relation(String path, int rowsExamined, String fingerprint, boolean complete, String reason) {}
    public record Snapshot(List<Map<String, Object>> layout, List<Relation> relations, boolean complete,
            int rowsExamined, String scope) {}

    public Snapshot capture(long productId, boolean lock) {
        var layout = jdbc.queryForList(DISCOVER_SQL).stream()
                .map(row -> Collections.unmodifiableMap(new TreeMap<>(row))).toList();
        boolean complete = !layout.isEmpty() && layout.size() <= MAX_PATHS;
        var measured = new HashSet<String>();
        layout.forEach(edge -> measured.add(text(edge, "table_schema") + "." + text(edge, "table_name")
                + "." + text(edge, "column_name") + "->" + text(edge, "target_table") + "." + text(edge, "target_column")));
        complete &= measured.containsAll(REQUIRED);
        var relations = new ArrayList<Relation>();
        int examined = 0;
        // FOR UPDATE is required here: option consumers can be inserted without touching the food FK.
        var options = jdbc.queryForList("SELECT id FROM public.food_item_serving_options WHERE food_item_id = ?"
                + " ORDER BY id LIMIT 4097" + (lock ? " FOR UPDATE" : ""), productId);
        if (options.size() > MAX_ROWS) {
            return new Snapshot(layout, List.of(new Relation("food_item_serving_options", options.size(),
                    sha("OPTION_BUDGET_EXCEEDED"), false, "OPTION_BUDGET_EXCEEDED")), false, options.size(), SCOPE);
        }
        for (var edge : layout) {
            String table = text(edge, "table_name"), column = text(edge, "column_name");
            String path = text(edge, "table_schema") + "." + table + ":" + text(edge, "constraint_name");
            if (!supported(edge)) {
                relations.add(new Relation(path, 0, sha("UNSUPPORTED_RELATION"), false, "UNSUPPORTED_RELATION"));
                complete = false;
                continue;
            }
            // Legacy rows are already fully fenced by V1; the new append-only ledger must not invalidate its own retry.
            if (LEDGERS.contains(table)) continue;
            String predicate = quote(column) + " = ?";
            if ("food_item_serving_options".equals(text(edge, "target_table"))) {
                predicate = quote(column) + " IN (SELECT id FROM public.food_item_serving_options WHERE food_item_id = ?)";
            }
            int remaining = Math.max(0, MAX_ROWS - examined);
            String sql = "SELECT encode(sha256(convert_to(to_jsonb(d)::text,'UTF8')),'hex') AS row_hash FROM "
                    + quote(text(edge, "table_schema")) + "." + quote(table) + " d WHERE d." + predicate
                    + " ORDER BY d.ctid LIMIT ?" + (lock ? " FOR SHARE OF d" : "");
            var found = jdbc.queryForList(sql, productId, remaining + 1);
            boolean fits = found.size() <= remaining;
            examined += found.size();
            var hashes = found.stream().map(row -> text(row, "row_hash")).sorted().toList();
            relations.add(new Relation(path, found.size(), sha(String.join("\n", hashes)), fits,
                    fits ? "DECLARED_FK_ROWS_FINGERPRINTED" : "DEPENDENCY_ROW_BUDGET_EXCEEDED"));
            complete &= fits;
        }
        return new Snapshot(layout, List.copyOf(relations), complete, examined, SCOPE);
    }

    private boolean supported(Map<String, Object> edge) {
        return "public".equals(text(edge, "table_schema")) && "id".equals(text(edge, "target_column"))
                && Objects.equals(1, edge.get("key_columns")) && Objects.equals(1, edge.get("target_columns"))
                && Boolean.TRUE.equals(edge.get("validated")) && "bigint".equals(text(edge, "column_type"))
                && Set.of("r", "p").contains(text(edge, "relation_kind"))
                && validIdentifier(text(edge, "table_name")) && validIdentifier(text(edge, "column_name"))
                && Set.of("food_items", "food_item_serving_options").contains(text(edge, "target_table"));
    }
    private static String text(Map<String, Object> row, String key) { return Objects.toString(row.get(key), ""); }
    private static String quote(String identifier) {
        if (!validIdentifier(identifier)) throw new IllegalStateException("Unsupported dependency identifier");
        return '"' + identifier + '"';
    }
    private static boolean validIdentifier(String identifier) { return identifier.matches("[a-z_][a-z0-9_]{0,62}"); }
    private static String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static Set<String> requiredPaths() { return REQUIRED; }
    private static Set<String> readRequiredPaths() {
        try (var stream = FoodProductDependencySnapshot.class.getResourceAsStream("/catalog/product-dependency-fk-contract-v310.json")) {
            var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            if (!"product-dependency-fk-contract-v310-v1".equals(body.path("version").asText()))
                throw new IllegalStateException("Unknown dependency contract version");
            var paths = new HashSet<String>();
            body.path("requiredPaths").forEach(path -> {
                if (!path.isTextual() || !paths.add(path.asText())) throw new IllegalStateException("Invalid/duplicate required FK path");
            });
            if (paths.size() != 31) throw new IllegalStateException("Exact reviewed V310 dependency contract required");
            return Set.copyOf(paths);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load product dependency contract", e); }
    }
}

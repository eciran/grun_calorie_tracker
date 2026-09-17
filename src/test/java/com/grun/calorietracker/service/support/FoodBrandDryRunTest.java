package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FoodBrandDryRunTest {
    private String proposed(String brand) {
        String canonical = FoodBrandDisplayRules.canonicalName(brand);
        return canonical == null ? brand : canonical;
    }

    @Test
    void unreviewedValuesDoNotEnterBackfill() {
        assertEquals("unknown BRAND", proposed("unknown BRAND"));
        assertEquals("Coop, Co-op", proposed("Coop, Co-op"));
        assertNull(proposed(null));
        assertEquals("M&S", proposed("M&S, Marks & Spencer"));
        assertEquals("Tesco, Tesco Finest", proposed("TESCo,Tesco finest"));
    }

    @Test
    void generateReportWhenSnapshotIsExplicitlySupplied() throws Exception {
        String input = System.getProperty("brandDryRunInput");
        if (input == null) return;
        Path file = Path.of(input);
        ObjectMapper mapper = new ObjectMapper();
        var snapshot = mapper.readTree(file.toFile());
        assertEquals("STOPPED", snapshot.path("status").asText());
        assertEquals("on", snapshot.path("context").path("readOnly").asText());
        assertTrue(snapshot.path("exits").size() > 0);
        snapshot.path("exits").forEach(exit -> assertEquals(0, exit.asInt(-1)));
        assertEquals(snapshot.path("summary").path("rows").asLong(), snapshot.path("rows").size());
        Set<Long> ids = new HashSet<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, Long> counts = new TreeMap<>();
        for (var row : snapshot.path("rows")) {
            assertTrue(ids.add(row.path("id").asLong()));
            String before = row.path("brand").isNull() ? null : row.path("brand").asText();
            String after = proposed(before);
            assertEquals(after, proposed(after));
            if (Objects.equals(before, after)) continue;
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("id", row.path("id").asLong());
            change.put("barcode", row.path("barcode").isNull() ? null : row.path("barcode").asText());
            change.put("oldBrand", before);
            change.put("newBrand", after);
            changes.add(change);
            counts.merge(before + " -> " + after, 1L, Long::sum);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dryRun", true);
        report.put("task", snapshot.path("task").asText());
        report.put("snapshotTime", snapshot.path("context").path("time").asText());
        report.put("snapshotSha256", hash(file));
        report.put("aliasSha256", hash(Path.of("src/main/resources/catalog/brand-display-aliases.properties")));
        report.put("rulesSha256", hash(Path.of("src/main/java/com/grun/calorietracker/service/support/FoodBrandDisplayRules.java")));
        report.put("rowsChecked", ids.size());
        report.put("catalogRows", snapshot.path("catalog").path("rows").asLong());
        report.put("scope", "Approved brand-family prefilter, excluding already-canonical single brands");
        report.put("rowsProposed", changes.size());
        report.put("unchanged", ids.size() - changes.size());
        report.put("changes", changes);
        Path output = file.getParent();
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("BRAND_DRY_RUN.json").toFile(), report);
        List<String> lines = new ArrayList<>(List.of("# Brand Dry Run", "", "No database changes. Reviewed aliases only; unknown values retained.",
                "", "Catalog rows: " + snapshot.path("catalog").path("rows").asLong(),
                "Scope: approved brand-family prefilter; canonical single brands excluded.",
                "Rows checked in scope: " + ids.size(), "Proposed: " + changes.size(), "Unchanged in scope: " + (ids.size() - changes.size()),
                "", "## Leading changes", "", "| Old -> New | Rows |", "|---|---:|"));
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(40)
                .forEach(e -> lines.add("| " + cell(e.getKey()) + " | " + e.getValue() + " |"));
        lines.addAll(List.of("", "## Row-level proposals", "", "| ID | Barcode | Old brand | New brand |", "|---|---|---|---|"));
        for (var change : changes) lines.add("| " + change.get("id") + " | " + cell(change.get("barcode")) + " | "
                + cell(change.get("oldBrand")) + " | " + cell(change.get("newBrand")) + " |");
        lines.addAll(List.of("", "Not an execution script. A future apply must re-check ID/barcode/old brand, preserve before-state and audit, and refresh caches/search indexes as needed.",
                "No product identity, nutrient, serving or barcode changes are proposed. This does not resolve all duplicate products."));
        Files.write(output.resolve("BRAND_DRY_RUN.md"), lines);
        System.out.println("Brand dry run: checked=" + ids.size() + ", proposed=" + changes.size());
    }

    @Test
    void sqlPrefilterCoversEveryReviewedAlias() throws Exception {
        Properties aliases = new Properties();
        try (var input = FoodBrandDisplayRules.class.getResourceAsStream("/catalog/brand-display-aliases.properties")) {
            assertNotNull(input);
            aliases.load(input);
        }
        var pattern = java.util.regex.Pattern.compile("vit|7|tesco|lidl|dunnes|marks|&|aldi|special|deluxe|waitrose",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (String alias : aliases.stringPropertyNames()) {
            assertTrue(pattern.matcher(alias).find(), "Update the SQL prefilter for alias: " + alias);
        }
    }

    private String cell(Object value) {
        return String.valueOf(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

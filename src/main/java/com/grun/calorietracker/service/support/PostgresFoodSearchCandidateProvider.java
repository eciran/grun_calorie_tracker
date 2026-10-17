package com.grun.calorietracker.service.support;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class PostgresFoodSearchCandidateProvider {
    // PostgreSQL can filter this many ids efficiently while keeping the final
    // specification responsible for every visibility, ranking and count rule.
    // Queries broader than this still use the lossless ORM path instead of
    // silently truncating the catalog.
    static final int MAX_CANDIDATES = 25000;

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean postgres;

    public PostgresFoodSearchCandidateProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<List<Long>> findCandidateIds(String searchQuery) {
        FoodProductNormalizationRules.validateSearchQuery(searchQuery);
        if (!isPostgres()) {
            return Optional.empty();
        }
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(searchQuery);
        if (normalizedBarcode != null && normalizedBarcode.matches("\\d{4,14}")) {
            return Optional.empty();
        }
        Set<String> terms = resolveTerms(searchQuery);
        if (terms.isEmpty()) {
            return Optional.empty();
        }

        Set<String> brandKeys = new LinkedHashSet<>(FoodBrandSearchRules.searchKeys(searchQuery));
        terms.forEach(term -> brandKeys.addAll(FoodBrandSearchRules.searchKeys(term)));
        List<Long> candidates = jdbcTemplate.queryForList(
                buildSql(terms.size(), brandKeys.size()),
                Long.class,
                buildArguments(terms, brandKeys)
        );
        if (candidates.size() > MAX_CANDIDATES) {
            return Optional.empty();
        }
        return Optional.of(candidates);
    }

    private boolean isPostgres() {
        Boolean cached = postgres;
        if (cached != null) {
            return cached;
        }
        Boolean detected = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName()
                        .toLowerCase(Locale.ROOT)
                        .contains("postgresql"));
        postgres = Boolean.TRUE.equals(detected);
        return postgres;
    }

    private Set<String> resolveTerms(String searchQuery) {
        Set<String> terms = new LinkedHashSet<>();
        for (String expanded : FoodProductNormalizationRules.expandSearchTerms(searchQuery)) {
            addNormalizedTerms(terms, expanded);
        }
        addNormalizedTerms(terms, searchQuery);
        List<Set<String>> tokenGroups = FoodProductNormalizationRules.searchTokenVariants(searchQuery);
        if (tokenGroups.size() >= 2) {
            // A final token-AND match must match every group, hence any selected
            // group is a safe candidate superset, even across brand/alias/locales.
            // Prefer a longer group to reduce broad scans; the candidate safety ceiling keeps a lossless ORM fallback.
            tokenGroups.stream().max(java.util.Comparator.comparingInt(group ->
                            group.stream().mapToInt(String::length).max().orElse(0)))
                    .ifPresent(terms::addAll);
        }
        return terms;
    }

    private void addNormalizedTerms(Set<String> terms, String value) {
        String source = FoodProductNormalizationRules.normalizeText(value);
        if (source != null) {
            terms.add(source.toLowerCase(Locale.ROOT));
        }
        String alias = FoodProductNormalizationRules.normalizeSearchAlias(value);
        if (alias != null) {
            terms.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    private String buildSql(int termCount, int brandKeyCount) {
        String itemPredicate = repeatedPredicate(
                termCount,
                "lower(name) like ? escape '!'",
                "lower(display_name) like ? escape '!'",
                "lower(short_display_name) like ? escape '!'",
                "lower(brand) like ? escape '!'"
        );
        String aliasPredicate = repeatedPredicate(
                termCount,
                "lower(alias) like ? escape '!'",
                "lower(normalized_alias) like ? escape '!'"
        );
        String localizationPredicate = repeatedPredicate(
                termCount,
                "lower(display_name) like ? escape '!'",
                "lower(short_display_name) like ? escape '!'"
        );
        String brandPredicate = brandKeyCount == 0 ? "1 = 0" : FoodBrandSearchRules.sqlExpression()
                + (brandKeyCount == 1 ? " = ?" : " in (" + String.join(",", java.util.Collections.nCopies(brandKeyCount, "?")) + ")");
        return """
                with candidate_ids as (
                    select id as food_item_id from food_items where %s
                    union
                    select food_item_id from food_item_search_aliases where active = true and (%s)
                    union
                    select food_item_id from food_item_localizations where active = true and (%s)
                    union
                    select id from food_items where %s
                )
                select food_item_id
                from candidate_ids
                order by food_item_id
                limit %d
                """.formatted(itemPredicate, aliasPredicate, localizationPredicate,
                        brandPredicate, MAX_CANDIDATES + 1);
    }

    private String repeatedPredicate(int termCount, String... columns) {
        List<String> predicates = new ArrayList<>();
        for (int term = 0; term < termCount; term++) {
            predicates.add("(" + String.join(" or ", columns) + ")");
        }
        return String.join(" or ", predicates);
    }

    private Object[] buildArguments(Set<String> terms, Set<String> brandKeys) {
        List<Object> arguments = new ArrayList<>();
        appendPatterns(arguments, terms, 4);
        appendPatterns(arguments, terms, 2);
        appendPatterns(arguments, terms, 2);
        arguments.addAll(brandKeys);
        return arguments.toArray();
    }

    private void appendPatterns(List<Object> arguments, Set<String> terms, int columnCount) {
        for (String term : terms) {
            String pattern = FoodSearchLikePatterns.contains(term);
            for (int column = 0; column < columnCount; column++) {
                arguments.add(pattern);
            }
        }
    }
}

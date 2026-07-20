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
    private static final int MAX_CANDIDATES = 5000;

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean postgres;

    public PostgresFoodSearchCandidateProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<List<Long>> findCandidateIds(String searchQuery) {
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

        List<Long> candidates = jdbcTemplate.queryForList(
                buildSql(terms.size()),
                Long.class,
                buildArguments(terms)
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

    private String buildSql(int termCount) {
        String itemPredicate = repeatedPredicate(
                termCount,
                "lower(name) like ?",
                "lower(display_name) like ?",
                "lower(short_display_name) like ?",
                "lower(brand) like ?"
        );
        String aliasPredicate = repeatedPredicate(
                termCount,
                "lower(alias) like ?",
                "lower(normalized_alias) like ?"
        );
        String localizationPredicate = repeatedPredicate(
                termCount,
                "lower(display_name) like ?",
                "lower(short_display_name) like ?"
        );
        return """
                with candidate_ids as (
                    select id as food_item_id from food_items where %s
                    union
                    select food_item_id from food_item_search_aliases where active = true and (%s)
                    union
                    select food_item_id from food_item_localizations where active = true and (%s)
                )
                select food_item_id
                from candidate_ids
                order by food_item_id
                limit %d
                """.formatted(itemPredicate, aliasPredicate, localizationPredicate, MAX_CANDIDATES + 1);
    }

    private String repeatedPredicate(int termCount, String... columns) {
        List<String> predicates = new ArrayList<>();
        for (int term = 0; term < termCount; term++) {
            predicates.add("(" + String.join(" or ", columns) + ")");
        }
        return String.join(" or ", predicates);
    }

    private Object[] buildArguments(Set<String> terms) {
        List<Object> arguments = new ArrayList<>();
        appendPatterns(arguments, terms, 4);
        appendPatterns(arguments, terms, 2);
        appendPatterns(arguments, terms, 2);
        return arguments.toArray();
    }

    private void appendPatterns(List<Object> arguments, Set<String> terms, int columnCount) {
        for (String term : terms) {
            String pattern = "%" + term + "%";
            for (int column = 0; column < columnCount; column++) {
                arguments.add(pattern);
            }
        }
    }
}

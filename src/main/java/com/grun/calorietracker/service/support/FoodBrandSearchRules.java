package com.grun.calorietracker.service.support;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/** Search equivalence only: never use this key for product identity or merging. */
public final class FoodBrandSearchRules {
    public static final String SEPARATORS = " -\u2022\u00b7\u2010\u2011\u2013\u2014\u00a0";
    private static final String ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿğışşčćžźż";
    private static final String ASCII =    "aaaaaaceeeeiiiinooooouuuuyygisscczzz";

    private FoodBrandSearchRules() { }

    public static Set<String> searchKeys(String value) {
        Set<String> keys = new LinkedHashSet<>();
        String literal = key(value);
        if (literal != null) keys.add(literal);
        for (String alias : FoodBrandDisplayRules.searchAliases(value)) {
            String reviewed = key(alias);
            if (reviewed != null) keys.add(reviewed);
        }
        return java.util.Collections.unmodifiableSet(keys);
    }

    public static String key(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder folded = new StringBuilder(normalized.length());
        normalized.codePoints().filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK)
                .forEach(folded::appendCodePoint);
        String key = folded.toString();
        for (int i = 0; i < SEPARATORS.length(); i++) {
            key = key.replace(String.valueOf(SEPARATORS.charAt(i)), "");
        }
        return key.length() >= 2 && key.codePoints().anyMatch(Character::isLetter) ? key : null;
    }

    public static Expression<String> expression(CriteriaBuilder builder, Expression<String> brand) {
        Expression<String> expression = builder.function("translate", String.class, builder.lower(brand),
                builder.literal(ACCENTED), builder.literal(ASCII));
        for (int i = 0; i < SEPARATORS.length(); i++) {
            expression = builder.function("replace", String.class, expression,
                    builder.literal(String.valueOf(SEPARATORS.charAt(i))), builder.literal(""));
        }
        return expression;
    }

    public static String sqlExpression() {
        String expression = "translate(lower(brand), '" + ACCENTED + "', '" + ASCII + "')";
        for (int i = 0; i < SEPARATORS.length(); i++) {
            expression = "replace(" + expression + ", '" + SEPARATORS.charAt(i) + "', '')";
        }
        return expression;
    }
}

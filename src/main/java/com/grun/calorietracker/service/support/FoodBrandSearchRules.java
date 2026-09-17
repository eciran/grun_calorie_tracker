package com.grun.calorietracker.service.support;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.Locale;

/** Search equivalence only: never use this key for product identity or merging. */
public final class FoodBrandSearchRules {
    public static final String SEPARATORS = " -\u2022\u00b7\u2010\u2011\u2013\u2014\u00a0";

    private FoodBrandSearchRules() { }

    public static String key(String value) {
        if (value == null) {
            return null;
        }
        String key = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < SEPARATORS.length(); i++) {
            key = key.replace(String.valueOf(SEPARATORS.charAt(i)), "");
        }
        return key.length() >= 3 && key.codePoints().anyMatch(Character::isLetter) ? key : null;
    }

    public static Expression<String> expression(CriteriaBuilder builder, Expression<String> brand) {
        Expression<String> expression = builder.lower(brand);
        for (int i = 0; i < SEPARATORS.length(); i++) {
            expression = builder.function("replace", String.class, expression,
                    builder.literal(String.valueOf(SEPARATORS.charAt(i))), builder.literal(""));
        }
        return expression;
    }

    public static String sqlExpression() {
        String expression = "lower(brand)";
        for (int i = 0; i < SEPARATORS.length(); i++) {
            expression = "replace(" + expression + ", '" + SEPARATORS.charAt(i) + "', '')";
        }
        return expression;
    }
}

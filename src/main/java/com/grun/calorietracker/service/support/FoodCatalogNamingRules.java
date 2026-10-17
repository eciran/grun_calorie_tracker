package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.enums.*;
import java.util.*;
import java.util.regex.Pattern;

/** Catalog-wide review checks; no inferred translations or data writes. */
public final class FoodCatalogNamingRules {
    public static final String VERSION = FoodSourceNameRules.VERSION;
    private static final Pattern HTML = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern TR_FOLDED = Pattern.compile("(?iu)\\b(cig|gogus|gogsu|sut|pirinc|pismis|haslanmis|firinlanmis|yagsiz|cikolata|cilek)\\b");
    private static final Pattern EN_PREPARATION = Pattern.compile("(?iu)\\b(raw|cooked|boiled|grilled|roasted|steamed|skinless|boneless|unsalted|unsweetened)\\b");
    private static final Pattern EN_DESCRIPTOR_PHRASE = Pattern.compile("(?iu)\\b(?:with(?:out)? (?:salt|sugar)|(?:banana|strawberry|chicken|broccoli|milk|rice)\\s+and\\s+|(?:raw|cooked|boiled|steamed|grilled|roasted)\\s+(?:chicken|broccoli|rice|milk)\\b)");
    private FoodCatalogNamingRules() { }

    public static boolean requiresManualDisplayReview(String text) {
        var result = new TreeSet<String>();
        inspectText(text, null, "DISPLAY", result);
        return result.stream().anyMatch(finding -> !finding.equals("DISPLAY_FORMAT_REVIEW"));
    }

    public static boolean sourcePreparationConflict(String description, FoodPreparationState state) {
        if (description == null || state == null || state == FoodPreparationState.UNSPECIFIED) return false;
        var methods = EnumSet.noneOf(FoodPreparationState.class);
        boolean raw = false;
        boolean cooked = false;
        for (String part : description.split(",")) {
            String token = part.strip().toUpperCase(Locale.ROOT);
            if (token.equals("RAW")) raw = true;
            if (token.equals("COOKED")) cooked = true;
            for (var method : List.of(FoodPreparationState.BOILED, FoodPreparationState.ROASTED, FoodPreparationState.GRILLED,
                    FoodPreparationState.FRIED, FoodPreparationState.BAKED, FoodPreparationState.STEAMED)) {
                if (token.equals(method.name())) methods.add(method);
            }
        }
        if (raw) return state != FoodPreparationState.RAW || cooked || !methods.isEmpty();
        if (!methods.isEmpty()) return state != FoodPreparationState.COOKED && !methods.contains(state);
        return cooked && state != FoodPreparationState.COOKED;
    }

    public static Set<String> findings(FoodItemEntity product, List<FoodItemLocalizationEntity> localizations) {
        var findings = new TreeSet<String>();
        inspectText(product.getDisplayName(), null, "DISPLAY", findings);
        boolean nonBranded = product.getCatalogType() != FoodCatalogType.BRANDED_PRODUCT
                && product.getCatalogType() != FoodCatalogType.USER_CUSTOM;
        Map<PreferredLanguage, FoodItemLocalizationEntity> active = new EnumMap<>(PreferredLanguage.class);
        for (var localization : localizations) {
            if (!Boolean.TRUE.equals(localization.getActive())) continue;
            if (active.putIfAbsent(localization.getLanguage(), localization) != null) findings.add("DUPLICATE_ACTIVE_LOCALIZATION");
            String prefix = localization.getLanguage().name();
            inspectText(localization.getDisplayName(), localization.getLanguage(), prefix + "_DISPLAY", findings);
            if (localization.getShortDisplayName() != null) inspectText(localization.getShortDisplayName(), localization.getLanguage(), prefix + "_SHORT", findings);
            if (localization.getLanguage() == PreferredLanguage.TR
                    && (EN_DESCRIPTOR_PHRASE.matcher(Objects.toString(localization.getDisplayName(), "")).find()
                        || EN_DESCRIPTOR_PHRASE.matcher(Objects.toString(localization.getShortDisplayName(), "")).find())) {
                findings.add("ENGLISH_DESCRIPTOR_IN_TR_NAME");
            }
            if (nonBranded && localization.getLanguage() == PreferredLanguage.TR) {
                String text = Objects.toString(localization.getDisplayName(), "") + " " + Objects.toString(localization.getShortDisplayName(), "");
                if (TR_FOLDED.matcher(text).find()) findings.add("TR_DIACRITICS_REVIEW");
                if (EN_PREPARATION.matcher(text).find()) findings.add("ENGLISH_DESCRIPTOR_IN_TR_NAME");
            }
        }
        if (nonBranded) {
            for (var language : List.of(PreferredLanguage.EN, PreferredLanguage.TR)) {
                if (!active.containsKey(language)) findings.add("MISSING_" + language.name() + "_LOCALIZATION");
            }
            String display = product.getDisplayName();
            if (display != null && display.chars().filter(c -> c == ',').count() >= 2 && !display.contains("(")) findings.add("SOURCE_STYLE_DISPLAY_NAME");
        }
        String brand = product.getBrand();
        String canonical = FoodBrandDisplayRules.canonicalName(brand);
        if (canonical != null && !canonical.equals(brand)) findings.add("REVIEWED_BRAND_DISPLAY_VARIANT");
        if (canonical == null && brand != null && !brand.equals(FoodProductNormalizationRules.cleanDisplayText(brand))) findings.add("BRAND_FORMAT_REVIEW");
        if (canonical == null && brand != null && brand.codePoints().anyMatch(Character::isLetter)
                && (brand.equals(brand.toLowerCase(Locale.ROOT)) || brand.equals(brand.toUpperCase(Locale.ROOT)))
                && (product.getCanonicalBrand() == null || !product.getCanonicalBrand().isVerified()
                    || product.getCanonicalBrand().getStatus() != FoodBrandStatus.ACTIVE)) findings.add("UNREVIEWED_BRAND_CASING");
        if (product.getDataSource() == FoodDataSource.USDA_FOODDATA) {
            if (nonBranded && sourcePreparationConflict(product.getName(), product.getPreparationState())) findings.add("SOURCE_PREPARATION_NAME_CONFLICT");
            FoodSourceNameRules.reviewedSource(product.getSourceKey()).ifPresent(decision -> {
                if (!"SR Legacy".equals(product.getSourceDataType())) findings.add("SOURCE_NAMING_METADATA_REVIEW");
                if (!decision.displayNameEn().equals(product.getDisplayName()) || product.getPreparationState() != decision.targetState()) findings.add("SOURCE_NAME_EXCEPTION_PENDING");
                for (var language : List.of(PreferredLanguage.EN, PreferredLanguage.TR)) {
                    var localization = active.get(language);
                    if (localization == null || !decision.displayName(language).equals(localization.getDisplayName())) findings.add("SOURCE_NAME_EXCEPTION_PENDING");
                }
            });
        }
        return findings;
    }

    private static void inspectText(String text, PreferredLanguage language, String prefix, Set<String> result) {
        if (text == null || text.isBlank()) { result.add("MISSING_" + prefix + "_NAME"); return; }
        if (!text.equals(FoodProductNormalizationRules.normalizeProductDisplayName(text, language))) result.add(prefix + "_FORMAT_REVIEW");
        if (text.length() > 255) result.add(prefix + "_TOO_LONG");
        if (HTML.matcher(text).find()) result.add(prefix + "_HTML_REVIEW");
        if (text.codePoints().anyMatch(c -> c == 0xfffd || Character.getType(c) == Character.FORMAT || Character.isISOControl(c))) result.add(prefix + "_CONTROL_OR_ENCODING_REVIEW");
        if (text.chars().filter(c -> c == '(').count() != text.chars().filter(c -> c == ')').count()) result.add(prefix + "_PARENTHESES_REVIEW");
    }
}

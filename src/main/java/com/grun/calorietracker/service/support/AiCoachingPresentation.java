package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Presentation guard for coaching only; structured metric identifiers remain unchanged. */
public final class AiCoachingPresentation {
    private static final Pattern INTERNAL = Pattern.compile(
            "\\b[a-z]+[A-Z][a-zA-Z0-9]*\\b|\\b[a-z]+(?:_[a-z0-9]+)+\\b|\\b[a-zA-Z][a-zA-Z0-9_]*\\s*=|[{}]");
    private static final Set<String> DISPLAY_FIELDS = Set.of("title", "summary", "label", "message", "evidence",
            "impact", "action", "reason", "expectedImpact", "tomorrowFocus", "watchOut", "dataQualityNote",
            "highlights", "warnings", "recommendedActions", "reviewReasons", "ctaLabel");

    private AiCoachingPresentation() {}

    public static JsonNode cleanHistory(JsonNode payload) {
        if (payload == null) return null;
        JsonNode copy = payload.deepCopy();
        cleanTree(copy);
        return copy;
    }

    private static void cleanTree(JsonNode node) {
        if (node.isObject()) {
            var object = (ObjectNode) node;
            object.fields().forEachRemaining(field -> {
                var value = field.getValue();
                if (DISPLAY_FIELDS.contains(field.getKey()) && value.isTextual()) {
                    object.put(field.getKey(), text(value.asText()));
                } else if (DISPLAY_FIELDS.contains(field.getKey()) && value.isArray()) {
                    var cleaned = object.arrayNode();
                    value.forEach(item -> {
                        if (item.isTextual()) {
                            String safe = text(item.asText());
                            if (!safe.isBlank()) cleaned.add(safe);
                        }
                    });
                    object.set(field.getKey(), cleaned);
                } else {
                    cleanTree(value);
                }
            });
        } else if (node.isArray()) {
            node.forEach(AiCoachingPresentation::cleanTree);
        }
    }

    public static void clean(AiInsightResponseDto response) {
        response.setTitle(text(response.getTitle()));
        response.setSummary(text(response.getSummary()));
        response.setTomorrowFocus(text(response.getTomorrowFocus()));
        response.setWatchOut(text(response.getWatchOut()));
        response.setDataQualityNote(text(response.getDataQualityNote()));
        response.setCtaLabel(text(response.getCtaLabel()));
        response.setHighlights(strings(response.getHighlights()));
        response.setWarnings(strings(response.getWarnings()));
        response.setRecommendedActions(strings(response.getRecommendedActions()));
        response.setReviewReasons(strings(response.getReviewReasons()));
        var findings = new ArrayList<AiInsightResponseDto.KeyFinding>();
        var seen = new LinkedHashSet<String>();
        if (response.getKeyFindings() != null) {
            for (var finding : response.getKeyFindings()) {
                if (finding == null) continue;
                finding.setLabel(text(finding.getLabel()));
                finding.setMessage(text(finding.getMessage()));
                finding.setEvidence(distinct(text(finding.getEvidence()), finding.getMessage(), response.getSummary()));
                finding.setImpact(distinct(text(finding.getImpact()), finding.getMessage(), finding.getEvidence(), response.getSummary()));
                if (distinct(finding.getMessage(), response.getSummary()).isBlank()
                        && finding.getEvidence().isBlank() && finding.getImpact().isBlank()) continue;
                if (!finding.getMessage().isBlank() && seen.add(key(finding.getMessage()))) findings.add(finding);
            }
        }
        response.setKeyFindings(findings.stream().limit(3).toList());
        var actions = new ArrayList<AiInsightResponseDto.PersonalizedAction>();
        seen.clear();
        if (response.getPersonalizedActions() != null) {
            for (var action : response.getPersonalizedActions()) {
                if (action == null) continue;
                action.setAction(text(action.getAction()));
                action.setReason(distinct(text(action.getReason()), action.getAction()));
                action.setExpectedImpact(distinct(text(action.getExpectedImpact()), action.getAction(), action.getReason()));
                if (!action.getAction().isBlank() && seen.add(key(action.getAction()))) actions.add(action);
            }
        }
        response.setPersonalizedActions(actions.stream().limit(3).toList());
        // Keep core actions; discard repeated supporting copy across visible sections.
        var displayed = new LinkedHashSet<String>();
        displayed.add(key(response.getSummary()));
        for (var finding : response.getKeyFindings()) displayed.add(key(finding.getMessage()));
        for (var action : response.getPersonalizedActions()) displayed.add(key(action.getAction()));
        if (response.getKeyFindings().isEmpty()) {
            response.getHighlights().forEach(value -> displayed.add(key(value)));
        }
        if (response.getPersonalizedActions().isEmpty()) {
            response.getRecommendedActions().forEach(value -> displayed.add(key(value)));
        }
        for (var finding : response.getKeyFindings()) {
            finding.setEvidence(once(finding.getEvidence(), displayed));
            finding.setImpact(once(finding.getImpact(), displayed));
        }
        for (var action : response.getPersonalizedActions()) {
            action.setReason(once(action.getReason(), displayed));
            action.setExpectedImpact(once(action.getExpectedImpact(), displayed));
        }
        response.setTomorrowFocus(once(response.getTomorrowFocus(), displayed));
        response.setWatchOut(once(response.getWatchOut(), displayed));
        response.setWarnings(response.getWarnings().stream().filter(value -> !once(value, displayed).isBlank()).toList());
        response.setDataQualityNote(once(response.getDataQualityNote(), displayed));
        response.setReviewReasons(response.getReviewReasons().stream().filter(value -> !once(value, displayed).isBlank()).toList());
        // Legacy clients still need equivalents; modern clients use these only as fallbacks.
        response.setHighlights(response.getKeyFindings().isEmpty()
                ? response.getHighlights().stream().filter(value -> !key(value).equals(key(response.getSummary()))).toList()
                : response.getKeyFindings().stream().map(AiInsightResponseDto.KeyFinding::getMessage).toList());
        if (!response.getPersonalizedActions().isEmpty()) {
            response.setRecommendedActions(response.getPersonalizedActions().stream()
                    .map(AiInsightResponseDto.PersonalizedAction::getAction).toList());
        }
    }

    private static String once(String value, Set<String> displayed) {
        return value.isBlank() || !displayed.add(key(value)) ? "" : value;
    }

    private static List<String> strings(List<String> values) {
        var seen = new LinkedHashSet<String>();
        return values == null ? List.of() : values.stream().map(AiCoachingPresentation::text)
                .filter(value -> !value.isBlank() && seen.add(key(value))).toList();
    }

    private static String text(String value) {
        if (value == null) return "";
        // Drop diagnostic sentences rather than translating internal keys into unsupported advice.
        return java.util.Arrays.stream(value.trim().split("(?<=[.!?])\\s+|[\\r\\n]+"))
                .filter(sentence -> !INTERNAL.matcher(sentence).find())
                .collect(java.util.stream.Collectors.joining(" ")).trim();
    }

    private static String distinct(String value, String... previous) {
        for (String other : previous) {
            if (!value.isBlank() && key(value).equals(key(other))) return "";
        }
        return value;
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
                .trim().replaceAll("[.!?]+$", "");
    }
}

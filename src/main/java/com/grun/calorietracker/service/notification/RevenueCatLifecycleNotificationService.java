package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RevenueCatEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RevenueCatLifecycleNotificationService {
    private static final String SOURCE = "REVENUECAT";
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE_EN = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK);
    private static final DateTimeFormatter DATE_TR = DateTimeFormatter.ofPattern("d MMMM uuuu", TURKISH);

    private final NotificationOrchestrationService orchestrationService;

    public Optional<NotificationOrchestrationResult> enqueue(
            UserEntity user,
            RevenueCatEventType providerEventType,
            String providerEventId,
            SubscriptionPlan plan,
            LocalDate relevantDate,
            Integer creditAmount,
            LocalDate addOnValidUntil,
            boolean subscriptionRefund) {
        NotificationEventType eventType = notificationType(providerEventType, creditAmount, subscriptionRefund);
        if (eventType == null) return Optional.empty();

        PreferredLanguage language = user.getPreferredLanguage() == null ? PreferredLanguage.EN : user.getPreferredLanguage();
        Map<String, String> parameters = parameters(eventType, plan, relevantDate, creditAmount, addOnValidUntil, language);
        Copy copy = copy(eventType, language, parameters);
        boolean addOn = eventType == NotificationEventType.AI_ADDON_PURCHASED;
        return Optional.of(orchestrationService.enqueue(new NotificationOrchestrationRequest(
                user,
                eventType,
                SOURCE,
                providerEventId,
                copy.title(),
                copy.message(),
                severity(eventType),
                addOn ? "AI_CREDITS" : "SUBSCRIPTION",
                null,
                addOn ? "ai-credits" : "manage-subscription",
                addOn ? "VIEW_AI_CREDITS" : "MANAGE_SUBSCRIPTION",
                parameters,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH),
                null,
                null
        )));
    }

    private NotificationEventType notificationType(
            RevenueCatEventType type, Integer creditAmount, boolean subscriptionRefund) {
        if (type == null) return null;
        return switch (type) {
            case INITIAL_PURCHASE -> NotificationEventType.SUBSCRIPTION_STARTED;
            case RENEWAL -> NotificationEventType.SUBSCRIPTION_RENEWED;
            case UNCANCELLATION -> NotificationEventType.SUBSCRIPTION_RESUMED;
            case CANCELLATION -> creditAmount != null ? null
                    : subscriptionRefund ? NotificationEventType.SUBSCRIPTION_REFUNDED
                    : NotificationEventType.SUBSCRIPTION_CANCELLED;
            case EXPIRATION -> NotificationEventType.SUBSCRIPTION_EXPIRED;
            case BILLING_ISSUE -> NotificationEventType.SUBSCRIPTION_BILLING_ISSUE;
            case PRODUCT_CHANGE -> NotificationEventType.SUBSCRIPTION_PLAN_CHANGED;
            case SUBSCRIPTION_PAUSED -> NotificationEventType.SUBSCRIPTION_PAUSED;
            case NON_RENEWING_PURCHASE -> NotificationEventType.AI_ADDON_PURCHASED;
            case TRANSFER, UNKNOWN -> null;
        };
    }

    private Map<String, String> parameters(NotificationEventType eventType, SubscriptionPlan plan,
            LocalDate relevantDate, Integer creditAmount, LocalDate addOnValidUntil, PreferredLanguage language) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (eventType == NotificationEventType.AI_ADDON_PURCHASED) {
            require(creditAmount != null && creditAmount > 0, "AI add-on credit amount is required for its notification.");
            require(addOnValidUntil != null, "AI add-on validity date is required for its notification.");
            parameters.put("creditAmount", String.valueOf(creditAmount));
            parameters.put("validUntilDate", format(addOnValidUntil, language));
            return Map.copyOf(parameters);
        }

        require(plan != null && plan != SubscriptionPlan.FREE, "A paid plan is required for a subscription notification.");
        parameters.put("planName", displayPlan(plan));
        switch (eventType) {
            case SUBSCRIPTION_STARTED -> {
                if (relevantDate != null) parameters.put("periodEndDate", format(relevantDate, language));
            }
            case SUBSCRIPTION_RENEWED, SUBSCRIPTION_RESUMED ->
                    parameters.put("periodEndDate", requiredDate(relevantDate, language, eventType));
            case SUBSCRIPTION_CANCELLED ->
                    parameters.put("accessUntilDate", requiredDate(relevantDate, language, eventType));
            case SUBSCRIPTION_BILLING_ISSUE -> {
                if (relevantDate != null) parameters.put("accessUntilDate", format(relevantDate, language));
            }
            case SUBSCRIPTION_EXPIRED ->
                    parameters.put("expiredAt", requiredDate(relevantDate, language, eventType));
            case SUBSCRIPTION_PLAN_CHANGED, SUBSCRIPTION_PAUSED, SUBSCRIPTION_REFUNDED ->
                    parameters.put("effectiveDate", requiredDate(relevantDate, language, eventType));
            default -> { }
        }
        return Map.copyOf(parameters);
    }

    private String requiredDate(LocalDate date, PreferredLanguage language, NotificationEventType eventType) {
        require(date != null, "A lifecycle date is required for " + eventType.definitionKey() + ".");
        return format(date, language);
    }

    private String format(LocalDate date, PreferredLanguage language) {
        return (language == PreferredLanguage.TR ? DATE_TR : DATE_EN).format(date);
    }

    private String displayPlan(SubscriptionPlan plan) {
        return switch (plan) {
            case PLUS -> "Plus";
            case PRO -> "Pro";
            case FREE -> "Free";
        };
    }

    private String severity(NotificationEventType eventType) {
        return switch (eventType) {
            case SUBSCRIPTION_BILLING_ISSUE, SUBSCRIPTION_REFUNDED -> "CRITICAL";
            case SUBSCRIPTION_CANCELLED, SUBSCRIPTION_EXPIRED, SUBSCRIPTION_PAUSED -> "WARNING";
            default -> "INFO";
        };
    }

    private Copy copy(NotificationEventType eventType, PreferredLanguage language, Map<String, String> parameters) {
        boolean tr = language == PreferredLanguage.TR;
        String plan = parameters.get("planName");
        return switch (eventType) {
            case SUBSCRIPTION_STARTED -> new Copy(tr ? "Aboneliğin hazır" : "Your subscription is ready",
                    tr ? plan + " aboneliğin aktif." : "Your " + plan + " subscription is active.");
            case SUBSCRIPTION_RENEWED -> new Copy(tr ? "Aboneliğin yenilendi" : "Subscription renewed",
                    tr ? plan + " aboneliğin başarıyla yenilendi." : "Your " + plan + " subscription renewed successfully.");
            case SUBSCRIPTION_CANCELLED -> new Copy(tr ? "Yenileme kapatıldı" : "Renewal has been turned off",
                    tr ? plan + " erişimin dönem sonuna kadar devam ediyor." : "Your " + plan + " access continues until period end.");
            case SUBSCRIPTION_RESUMED -> new Copy(tr ? "Yenileme yeniden aktif" : "Renewal is active again",
                    tr ? plan + " aboneliğin yeniden aktif." : "Your " + plan + " subscription is active again.");
            case SUBSCRIPTION_BILLING_ISSUE -> new Copy(tr ? "Aboneliğini kontrol et" : "Please check your subscription",
                    tr ? plan + " aboneliğinde bir ödeme sorunu var." : "There is a billing issue with your " + plan + " subscription.");
            case SUBSCRIPTION_EXPIRED -> new Copy(tr ? "Abonelik dönemin sona erdi" : "Subscription period ended",
                    tr ? plan + " abonelik dönemin sona erdi." : "Your " + plan + " subscription period ended.");
            case SUBSCRIPTION_PLAN_CHANGED -> new Copy(tr ? "Planın değişiyor" : "Your plan is changing",
                    tr ? "Planın " + plan + " olarak değişecek." : "Your plan will change to " + plan + ".");
            case SUBSCRIPTION_PAUSED -> new Copy(tr ? "Aboneliğin duraklatılıyor" : "Subscription pause scheduled",
                    tr ? plan + " aboneliğin planlanan tarihte duraklatılacak." : "Your " + plan + " subscription will pause on the scheduled date.");
            case SUBSCRIPTION_REFUNDED -> new Copy(tr ? "Abonelik iade güncellemesi" : "Subscription refund update",
                    tr ? plan + " aboneliğine bir iade güncellemesi uygulandı." : "A refund update was applied to your " + plan + " subscription.");
            case AI_ADDON_PURCHASED -> new Copy(tr ? "AI kredilerin eklendi" : "AI credits added",
                    tr ? parameters.get("creditAmount") + " AI kredisi hesabına eklendi."
                            : parameters.get("creditAmount") + " AI credits were added to your account.");
            default -> throw new IllegalArgumentException("Unsupported RevenueCat notification type: " + eventType);
        };
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Copy(String title, String message) { }
}

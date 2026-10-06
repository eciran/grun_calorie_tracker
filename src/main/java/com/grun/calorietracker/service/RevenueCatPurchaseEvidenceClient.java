package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.grun.calorietracker.config.RevenueCatProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RevenueCatPurchaseEvidenceClient {
    private final RevenueCatProperties properties;
    private final RestClient client;

    public RevenueCatPurchaseEvidenceClient(RevenueCatProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        client = builder.clone().requestFactory(factory).build();
    }

    public record Evidence(String transactionId, String productId, String environment, String store,
                           Instant purchasedAt, Instant expiresAt, boolean ownershipConflict) { }

    public List<Evidence> activePurchases(Long userId, String productId) {
        return activePurchasesForCustomer(userId, productId);
    }

    public List<Evidence> activePurchases(Long userId) {
        return activePurchasesForCustomer(userId, null);
    }

    private List<Evidence> activePurchasesForCustomer(Long userId, String productId) {
        var api = properties.getApi();
        if (!api.isEnabled() || blank(api.getSecretKey()) || blank(api.getProjectId())) {
            throw new IllegalStateException("RevenueCat verification is not configured");
        }
        if (!List.of("sandbox", "production").contains(api.getVerificationEnvironment())) {
            throw new IllegalStateException("Invalid verification environment");
        }
        String customerId = "user:" + userId;
        JsonNode subscriptions = collection("/projects/{project}/customers/{customer}/subscriptions",
                api.getProjectId(), customerId);
        var evidence = new ArrayList<Evidence>();
        for (JsonNode subscription : subscriptions.path("items")) {
            if (!subscription.path("gives_access").asBoolean(false)
                    || subscription.path("pending_payment").asBoolean(true)) continue;
            String environment = subscription.path("environment").asText("").toUpperCase(Locale.ROOT);
            String store = subscription.path("store").asText("").toUpperCase(Locale.ROOT);
            if (!List.of("SANDBOX", "PRODUCTION").contains(environment)
                    || !environment.equalsIgnoreCase(api.getVerificationEnvironment())
                    || !List.of("APP_STORE", "PLAY_STORE").contains(store)) continue;
            if (!customerId.equals(subscription.path("customer_id").asText())) continue;
            String latest = subscription.path("store_subscription_identifier").asText("");
            String subscriptionId = subscription.path("id").asText("");
            if (blank(latest) || blank(subscriptionId)) continue;
            JsonNode transactions = collection("/projects/{project}/subscriptions/{subscription}/transactions",
                    api.getProjectId(), subscriptionId);
            if (!transactions.path("items").isArray()) throw new IllegalStateException("Invalid transaction response");
            boolean latestFound = false;
            for (JsonNode transaction : transactions.path("items")) {
                if (!latest.equals(transaction.path("id").asText())) continue;
                latestFound = true;
                String transactionProduct = transaction.path("product_store_identifier").asText("");
                if (blank(transactionProduct) || (productId != null && !productId.equals(transactionProduct))) continue;
                long purchased = transaction.path("purchased_at").asLong(0);
                long expires = transaction.path("effective_expiration_date").asLong(0);
                long periodEnd = subscription.path("current_period_ends_at").asLong(0);
                long now = Instant.now().toEpochMilli();
                if (purchased <= 0 || purchased > now || expires <= now || periodEnd <= now) continue;
                String originalCustomer = subscription.path("original_customer_id").asText("");
                boolean conflict = originalCustomer.startsWith("user:") && !originalCustomer.equals(customerId);
                evidence.add(new Evidence(latest, transactionProduct, environment, store,
                        Instant.ofEpochMilli(purchased), Instant.ofEpochMilli(Math.min(expires, periodEnd)), conflict));
            }
            if (!latestFound) throw new IllegalStateException("Latest provider transaction is unavailable");
        }
        return List.copyOf(evidence);
    }

    private JsonNode get(String path, Object... variables) {
        return getPage(path, null, variables);
    }

    private JsonNode getPage(String path, String cursor, Object... variables) {
        var uri = UriComponentsBuilder.fromUriString(properties.getApi().getBaseUrl())
                .path(path).queryParam("limit", 100);
        if (cursor != null) uri.queryParam("starting_after", cursor);
        if (path.endsWith("/transactions")) uri.queryParam("sort", "purchased_at").queryParam("direction", "desc");
        else uri.queryParam("environment", properties.getApi().getVerificationEnvironment());
        JsonNode result = client.get().uri(uri.buildAndExpand(variables).encode().toUri())
                .headers(headers -> headers.setBearerAuth(properties.getApi().getSecretKey().trim()))
                .retrieve().body(JsonNode.class);
        if (result == null || !result.path("items").isArray()) throw new IllegalStateException("Invalid provider response");
        return result;
    }

    private JsonNode collection(String path, Object... variables) {
        var result = JsonNodeFactory.instance.objectNode();
        var items = result.putArray("items");
        String cursor = null;
        var seen = new java.util.HashSet<String>();
        for (int page = 0; page < 10; page++) {
            JsonNode response = getPage(path, cursor, variables);
            response.path("items").forEach(items::add);
            if (response.path("next_page").asText("").isBlank()) return result;
            JsonNode batch = response.path("items");
            cursor = batch.isEmpty() ? "" : batch.get(batch.size() - 1).path("id").asText("");
            if (blank(cursor) || !seen.add(cursor)) {
                throw new IllegalStateException("Provider pagination did not advance");
            }
        }
        throw new IllegalStateException("Provider pagination exceeded verification budget");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public JsonNode recoverablePurchaseEvent(Long userId, Evidence evidence) {
        var events = recoverablePurchaseEvents(userId, evidence);
        return events.isEmpty() ? null : events.get(0);
    }

    public JsonNode recoverableAddonPurchase(Long userId, String productId, Instant startedAt, String expectedTransactionId) {
        var history = collection("/projects/{project}/customers/{customer}/events",
                properties.getApi().getProjectId(), "user:" + userId).path("items");
        ObjectNode candidate = null;
        for (JsonNode item : history) {
            var body = item.path("body");
            if (!(body instanceof ObjectNode) || !"PURCHASES_NON_RENEWING_PURCHASE".equals(item.path("type").asText())
                    || !productId.equals(body.path("product_id").asText())
                    || !("user:" + userId).equals(body.path("app_user_id").asText())
                    || !properties.getApi().getVerificationEnvironment().equalsIgnoreCase(body.path("environment").asText())
                    || !List.of("APP_STORE", "PLAY_STORE").contains(body.path("store").asText())
                    || blank(body.path("transaction_id").asText()) || blank(item.path("id").asText())) continue;
            if (expectedTransactionId != null && !expectedTransactionId.equals(body.path("transaction_id").asText())) continue;
            long purchasedAt = body.path("purchased_at_ms").asLong();
            if (purchasedAt <= 0 || purchasedAt > Instant.now().toEpochMilli()) continue;
            if (expectedTransactionId == null && (startedAt == null || purchasedAt < startedAt.toEpochMilli())) continue;
            if (candidate != null && !candidate.path("transaction_id").asText().equals(body.path("transaction_id").asText()))
                throw new IllegalStateException("Ambiguous add-on transactions");
            candidate = ((ObjectNode) body).deepCopy();
            candidate.put("id", item.path("id").asText());
            candidate.put("type", "NON_RENEWING_PURCHASE");
        }
        if (candidate == null) return null;
        String transaction = candidate.path("transaction_id").asText();
        for (JsonNode item : history) {
            var body = item.path("body");
            if (transaction.equals(body.path("transaction_id").asText())
                    && "CUSTOMER_SUPPORT".equals(body.path("cancel_reason").asText())
                    && "PURCHASES_CANCELLATION".equals(item.path("type").asText())) return null;
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("api_version", "1.0");
        result.set("event", candidate);
        return result;
    }

    public List<JsonNode> recoverablePurchaseEvents(Long userId, Evidence evidence) {
        JsonNode history = collection("/projects/{project}/customers/{customer}/events",
                properties.getApi().getProjectId(), "user:" + userId).path("items");
        ObjectNode candidate = null;
        long candidateAt = -1;
        long latestOtherEventAt = -1;
        var lifecycle = new ArrayList<ObjectNode>();
        for (JsonNode item : history) {
            JsonNode body = item.path("body");
            if (!(body instanceof ObjectNode) || !evidence.transactionId().equals(body.path("transaction_id").asText())
                    || !evidence.productId().equals(body.path("product_id").asText())
                    || !("user:" + userId).equals(body.path("app_user_id").asText())
                    || !evidence.environment().equalsIgnoreCase(body.path("environment").asText())
                    || !evidence.store().equalsIgnoreCase(body.path("store").asText())) continue;
            String type = item.path("type").asText("");
            long at = body.path("event_timestamp_ms").asLong(0);
            if (!List.of("PURCHASES_INITIAL_PURCHASE", "PURCHASES_RENEWAL").contains(type)) {
                boolean harmlessCancellation = "PURCHASES_CANCELLATION".equals(type)
                        && "UNSUBSCRIBE".equals(body.path("cancel_reason").asText());
                if (harmlessCancellation || "PURCHASES_UNCANCELLATION".equals(type)
                        || "PURCHASES_PRODUCT_CHANGE".equals(type)) {
                    ObjectNode followup = ((ObjectNode) body).deepCopy();
                    followup.put("id", item.path("id").asText());
                    followup.put("type", type.substring("PURCHASES_".length()));
                    if (at > 0 && at <= Instant.now().toEpochMilli()
                            && !blank(followup.path("id").asText())
                            && !blank(followup.path("original_transaction_id").asText())) lifecycle.add(followup);
                    else latestOtherEventAt = Math.max(latestOtherEventAt, at);
                    continue;
                }
                latestOtherEventAt = Math.max(latestOtherEventAt, at);
                continue;
            }
            if (at <= 0 || at > Instant.now().toEpochMilli()
                    || body.path("purchased_at_ms").asLong(0) != evidence.purchasedAt().toEpochMilli()
                    || body.path("expiration_at_ms").asLong(0) != evidence.expiresAt().toEpochMilli()
                    || blank(body.path("original_transaction_id").asText()) || blank(item.path("id").asText())) continue;
            if (at > candidateAt) {
                candidate = ((ObjectNode) body).deepCopy();
                candidate.put("id", item.path("id").asText());
                candidate.put("type", type.substring("PURCHASES_".length()));
                candidateAt = at;
            }
        }
        // Do not synthesize a store transaction or overwrite a later cancellation/refund/change.
        if (candidate == null || latestOtherEventAt >= candidateAt) return List.of();
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("api_version", "1.0");
        payload.set("event", candidate);
        var result = new ArrayList<JsonNode>();
        result.add(payload);
        long purchaseEventAt = candidateAt;
        String chain = candidate.path("original_transaction_id").asText();
        lifecycle.sort(java.util.Comparator.comparingLong(item -> item.path("event_timestamp_ms").asLong()));
        for (ObjectNode item : lifecycle) {
            if (item.path("event_timestamp_ms").asLong() <= purchaseEventAt) continue;
            if (!chain.equals(item.path("original_transaction_id").asText())) return List.of();
            ObjectNode followup = JsonNodeFactory.instance.objectNode().put("api_version", "1.0");
            followup.set("event", item);
            result.add(followup);
        }
        return List.copyOf(result);
    }
}

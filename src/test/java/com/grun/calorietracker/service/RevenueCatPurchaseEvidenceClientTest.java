package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class RevenueCatPurchaseEvidenceClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    HttpServer server;
    RevenueCatPurchaseEvidenceClient client;
    ObjectNode subscription, transaction, event, events;
    long purchased, expires;
    int httpStatus;
    String lastAuthorization;

    @BeforeEach void setup() throws Exception {
        purchased = Instant.now().minusSeconds(10).toEpochMilli();
        expires = Instant.now().plusSeconds(3600).toEpochMilli();
        subscription = mapper.createObjectNode().put("id", "sub1").put("customer_id", "user:44")
                .put("original_customer_id", "user:44").put("environment", "sandbox").put("store", "app_store")
                .put("gives_access", true).put("pending_payment", false).put("store_subscription_identifier", "tx1")
                .put("current_period_ends_at", expires);
        transaction = mapper.createObjectNode().put("id", "tx1").put("product_store_identifier", "pro")
                .put("purchased_at", purchased).put("effective_expiration_date", expires);
        event = mapper.createObjectNode().put("id", "event1").put("type", "PURCHASES_INITIAL_PURCHASE");
        event.set("body", mapper.createObjectNode().put("app_user_id", "user:44").put("environment", "SANDBOX")
                .put("store", "APP_STORE").put("transaction_id", "tx1").put("original_transaction_id", "original1")
                .put("product_id", "pro").put("event_timestamp_ms", purchased + 1)
                .put("purchased_at_ms", purchased).put("expiration_at_ms", expires));
        events = list(event);
        httpStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            String path = exchange.getRequestURI().getPath();
            ObjectNode response = path.endsWith("/subscriptions") ? list(subscription)
                    : path.endsWith("/transactions") ? list(transaction) : events;
            byte[] bytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        var properties = new RevenueCatProperties();
        properties.getApi().setEnabled(true); properties.getApi().setSecretKey("test-secret");
        properties.getApi().setProjectId("project1"); properties.getApi().setVerificationEnvironment("sandbox");
        properties.getApi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v2");
        client = new RevenueCatPurchaseEvidenceClient(properties, RestClient.builder());
    }
    @AfterEach void cleanup() { if (server != null) server.stop(0); }

    @Test void validatesActiveTransactionAndPreservesOriginalTransactionForRecovery() {
        var proof = client.activePurchases(44L, "pro");
        assertThat(proof).hasSize(1);
        assertThat(lastAuthorization).isEqualTo("Bearer test-secret");
        var payload = client.recoverablePurchaseEvent(44L, proof.get(0));
        assertThat(payload.at("/event/original_transaction_id").asText()).isEqualTo("original1");
        assertThat(payload.at("/event/type").asText()).isEqualTo("INITIAL_PURCHASE");
        assertThat(payload.at("/event/id").asText()).isEqualTo("event1");
    }
    @Test void adminInspectionReturnsAllActiveProductsWithoutChangingEvidence() {
        var proof = client.activePurchases(44L);
        assertThat(proof).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("pro");
            assertThat(item.transactionId()).isEqualTo("tx1");
            assertThat(item.ownershipConflict()).isFalse();
        });
    }
    @Test void refusesWrongEnvironmentAndWrongProduct() {
        assertThat(client.activePurchases(44L, "other")).isEmpty();
        subscription.put("environment", "production");
        assertThat(client.activePurchases(44L, "pro")).isEmpty();
    }
    @Test void pendingPaymentOrExpiredTransactionCannotVerify() {
        subscription.put("pending_payment", true);
        assertThat(client.activePurchases(44L, "pro")).isEmpty();
        subscription.put("pending_payment", false);
        transaction.put("effective_expiration_date", purchased);
        assertThat(client.activePurchases(44L, "pro")).isEmpty();
    }
    @Test void transferredSubscriptionIsFlaggedForOwnershipReview() {
        subscription.put("original_customer_id", "user:33");
        assertThat(client.activePurchases(44L, "pro").get(0).ownershipConflict()).isTrue();
    }
    @Test void missingOriginalTransactionCannotBeInventedFromLatestId() {
        var proof = client.activePurchases(44L, "pro").get(0);
        ((ObjectNode) event.path("body")).remove("original_transaction_id");
        assertThat(client.recoverablePurchaseEvent(44L, proof)).isNull();
    }
    @Test void laterCancellationPreventsReplayingOldPurchase() {
        var proof = client.activePurchases(44L, "pro").get(0);
        var cancellation = event.deepCopy().put("id", "cancel1").put("type", "PURCHASES_CANCELLATION");
        ((ObjectNode) cancellation.path("body")).put("event_timestamp_ms", purchased + 5);
        events.withArray("items").add(cancellation);
        assertThat(client.recoverablePurchaseEvent(44L, proof)).isNull();
    }
    @Test void partialEventHistoryAndProviderErrorAreNotSuccess() {
        var proof = client.activePurchases(44L, "pro").get(0);
        events.put("next_page", "more");
        assertThatThrownBy(() -> client.recoverablePurchaseEvent(44L, proof)).isInstanceOf(IllegalStateException.class);
        httpStatus = 403;
        assertThatThrownBy(() -> client.activePurchases(44L, "pro"))
                .isInstanceOf(org.springframework.web.client.RestClientResponseException.class);
    }
    private ObjectNode list(ObjectNode item) {
        ObjectNode response = mapper.createObjectNode().putNull("next_page");
        response.putArray("items").add(item);
        return response;
    }
}

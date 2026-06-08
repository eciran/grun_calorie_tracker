package com.grun.calorietracker.service;

import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.dto.AdminMailMonitoringDto;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.service.impl.BrevoAdminMailMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoAdminMailMonitoringServiceTest {

    @Test
    void getMonitoringReturnsSafeDisabledStatusWhenProviderIsNotBrevo() {
        RestClient.Builder builder = RestClient.builder();
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.LOG);
        BrevoAdminMailMonitoringService service = new BrevoAdminMailMonitoringService(builder, properties);

        AdminMailMonitoringDto result = service.getMonitoring(7, 10);

        assertThat(result.getProvider()).isEqualTo("LOG");
        assertThat(result.isApiKeyConfigured()).isFalse();
        assertThat(result.isProviderReachable()).isFalse();
        assertThat(result.getStatusMessage()).contains("not BREVO");
    }

    @Test
    void getMonitoringFetchesBrevoCountersAndEventsWithoutExposingSecret() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.BREVO);
        properties.getBrevo().setApiKey("secret-brevo-key");
        properties.getBrevo().setApiUrl("https://api.brevo.com/v3/smtp/email");
        BrevoAdminMailMonitoringService service = new BrevoAdminMailMonitoringService(builder, properties);
        String utcEndDate = LocalDate.now(ZoneOffset.UTC).toString();

        server.expect(once(), requestTo(allOf(
                        startsWith("https://api.brevo.com/v3/smtp/statistics/aggregatedReport"),
                        containsString("endDate=" + utcEndDate))))
                .andExpect(header("api-key", "secret-brevo-key"))
                .andRespond(withSuccess("""
                        {
                          "requests": 12,
                          "delivered": 10,
                          "hardBounces": 1,
                          "opened": 5,
                          "clicked": 2
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(
                        startsWith("https://api.brevo.com/v3/smtp/statistics/events"),
                        containsString("endDate=" + utcEndDate))))
                .andExpect(header("api-key", "secret-brevo-key"))
                .andRespond(withSuccess("""
                        {
                          "events": [
                            {
                              "event": "delivered",
                              "email": "user@example.com",
                              "subject": "Verify your GRun email",
                              "messageId": "message-1",
                              "date": "2026-06-05T10:00:00"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AdminMailMonitoringDto result = service.getMonitoring(7, 10);

        assertThat(result.isProviderReachable()).isTrue();
        assertThat(result.getProviderBaseUrl()).isEqualTo("https://api.brevo.com");
        assertThat(result.getCounters()).containsEntry("requests", 12L).containsEntry("delivered", 10L);
        assertThat(result.getRecentEvents()).hasSize(1);
        assertThat(result.getRecentEvents().get(0).getEmail()).isEqualTo("user@example.com");
        assertThat(result.toString()).doesNotContain("secret-brevo-key");
        server.verify();
    }
}

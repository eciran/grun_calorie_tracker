package com.grun.calorietracker.service;

import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.impl.ConfigurableMailDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConfigurableMailDeliveryServiceTest {

    @Test
    void sendTransactionalEmailUsesBrevoPayloadWhenProviderIsBrevo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailProperties properties = brevoProperties();
        ConfigurableMailDeliveryService service = new ConfigurableMailDeliveryService(builder, properties);

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "brevo-api-key"))
                .andExpect(content().json("""
                        {
                          "sender": {
                            "email": "no-reply@grun.app",
                            "name": "GRun"
                          },
                          "to": [
                            {
                              "email": "user@example.com"
                            }
                          ],
                          "subject": "Verify your GRun email",
                          "textContent": "Verify text",
                          "htmlContent": "<p>Verify html</p>"
                        }
                        """))
                .andRespond(withSuccess());

        service.sendTransactionalEmail(
                "user@example.com",
                "Verify your GRun email",
                "Verify text",
                "<p>Verify html</p>"
        );

        server.verify();
    }

    @Test
    void sendTransactionalEmailFailsFastWhenBrevoApiKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MailProperties properties = brevoProperties();
        properties.getBrevo().setApiKey("");
        ConfigurableMailDeliveryService service = new ConfigurableMailDeliveryService(builder, properties);

        assertThatThrownBy(() -> service.sendTransactionalEmail(
                "user@example.com",
                "Subject",
                "Text",
                null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Brevo API key is required");
    }

    @Test
    void sendTransactionalEmailWrapsBrevoErrors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailProperties properties = brevoProperties();
        ConfigurableMailDeliveryService service = new ConfigurableMailDeliveryService(builder, properties);

        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.sendTransactionalEmail(
                "user@example.com",
                "Subject",
                "Text",
                null
        )).isInstanceOf(MailDeliveryException.class)
                .hasMessageContaining("Brevo rejected transactional email request");

        server.verify();
    }

    @Test
    void sendTransactionalEmailWhenProviderIsLogRedactsSensitiveLinks(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.LOG);
        ConfigurableMailDeliveryService service = new ConfigurableMailDeliveryService(builder, properties);

        service.sendTransactionalEmail(
                "user@example.com",
                "Reset",
                "Use https://app.grun.local/reset?token=raw-token&state=raw-state",
                "<a href=\"https://app.grun.local/reset?token=raw-token\">Reset</a>"
        );

        assertThat(output).doesNotContain("raw-token");
        assertThat(output).doesNotContain("raw-state");
        assertThat(output).contains("token=[REDACTED]");
        assertThat(output).contains("state=[REDACTED]");
    }

    private MailProperties brevoProperties() {
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.BREVO);
        properties.setFromEmail("no-reply@grun.app");
        properties.setFromName("GRun");
        properties.getBrevo().setApiKey("brevo-api-key");
        properties.getBrevo().setApiUrl("https://api.brevo.com/v3/smtp/email");
        return properties;
    }
}

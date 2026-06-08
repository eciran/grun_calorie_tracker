package com.grun.calorietracker.service;

import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.dto.AdminBrevoSenderDto;
import com.grun.calorietracker.dto.AdminBrevoSenderListDto;
import com.grun.calorietracker.dto.AdminBrevoSenderRequestDto;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.service.impl.BrevoAdminSenderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoAdminSenderServiceTest {

    @Test
    void getSendersReturnsSafeDisabledStatusWhenProviderIsNotBrevo() {
        RestClient.Builder builder = RestClient.builder();
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.LOG);
        BrevoAdminSenderService service = new BrevoAdminSenderService(builder, properties);

        AdminBrevoSenderListDto result = service.getSenders();

        assertThat(result.isProviderReachable()).isFalse();
        assertThat(result.getStatusMessage()).contains("not BREVO");
        assertThat(result.getSenders()).isEmpty();
    }

    @Test
    void getSendersFetchesBrevoSenders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BrevoAdminSenderService service = new BrevoAdminSenderService(builder, brevoProperties());

        server.expect(once(), requestTo("https://api.brevo.com/v3/senders"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("api-key", "secret-brevo-key"))
                .andRespond(withSuccess("""
                        {
                          "senders": [
                            {
                              "id": 15,
                              "name": "GRun Support",
                              "email": "support@grun.app",
                              "active": true,
                              "dkimError": false,
                              "spfError": false
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AdminBrevoSenderListDto result = service.getSenders();

        assertThat(result.isProviderReachable()).isTrue();
        assertThat(result.getSenders()).hasSize(1);
        assertThat(result.getSenders().get(0).getEmail()).isEqualTo("support@grun.app");
        server.verify();
    }

    @Test
    void createSenderSendsSenderPayloadWithoutExposingSecret() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BrevoAdminSenderService service = new BrevoAdminSenderService(builder, brevoProperties());
        AdminBrevoSenderRequestDto request = request();

        server.expect(once(), requestTo("https://api.brevo.com/v3/senders"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("api-key", "secret-brevo-key"))
                .andExpect(content().string(containsString("\"name\":\"GRun Support\"")))
                .andExpect(content().string(containsString("\"email\":\"support@grun.app\"")))
                .andRespond(withSuccess("{\"id\":15,\"dkimError\":false,\"spfError\":false}", MediaType.APPLICATION_JSON));

        AdminBrevoSenderDto result = service.createSender(request);

        assertThat(result.getId()).isEqualTo(15L);
        assertThat(result.toString()).doesNotContain("secret-brevo-key");
        server.verify();
    }

    @Test
    void updateSenderUsesSenderIdAndPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BrevoAdminSenderService service = new BrevoAdminSenderService(builder, brevoProperties());

        server.expect(once(), requestTo("https://api.brevo.com/v3/senders/15"))
                .andExpect(method(org.springframework.http.HttpMethod.PUT))
                .andExpect(header("api-key", "secret-brevo-key"))
                .andExpect(content().string(containsString("\"email\":\"support@grun.app\"")))
                .andRespond(withSuccess());

        AdminBrevoSenderDto result = service.updateSender(15L, request());

        assertThat(result.getId()).isEqualTo(15L);
        assertThat(result.getEmail()).isEqualTo("support@grun.app");
        server.verify();
    }

    private AdminBrevoSenderRequestDto request() {
        AdminBrevoSenderRequestDto request = new AdminBrevoSenderRequestDto();
        request.setName("GRun Support");
        request.setEmail("support@grun.app");
        return request;
    }

    private MailProperties brevoProperties() {
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProvider.BREVO);
        properties.getBrevo().setApiKey("secret-brevo-key");
        properties.getBrevo().setApiUrl("https://api.brevo.com/v3/smtp/email");
        return properties;
    }
}

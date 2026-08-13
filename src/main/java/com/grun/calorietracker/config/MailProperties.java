package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.MailProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "grun.mail")
public class MailProperties {

    private MailProvider provider = MailProvider.LOG;
    private String fromEmail = "no-reply@grun.local";
    private String fromName = "GRun";
    private Brevo brevo = new Brevo();

    @Getter
    @Setter
    public static class Brevo {
        private String apiKey = "";
        private String apiUrl = "https://api.brevo.com/v3/smtp/email";
        private Templates templates = new Templates();
    }

    @Getter
    @Setter
    public static class Templates {
        private long emailVerificationEn;
        private long emailVerificationTr;
        private long passwordResetEn;
        private long passwordResetTr;
        private long subscriptionFeatureChangeEn;
        private long subscriptionFeatureChangeTr;
        private long adminInvitationEn;
        private long adminInvitationTr;
        private long adminPasswordChangedEn;
        private long adminPasswordChangedTr;
    }
}

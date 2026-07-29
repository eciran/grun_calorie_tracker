package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Configuration
public class OwnerBootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapConfig.class);
    static final String RECOVERY_OWNER_EMAIL = "root-owner@gruncalorietracker.com";

    @Bean
    @ConditionalOnProperty(name = "grun.security.owner-bootstrap.enabled", havingValue = "true")
    CommandLineRunner ownerBootstrapRunner(UserRepository users, PasswordEncoder encoder,
            @Value("${grun.security.owner-bootstrap.primary-email:}") String primaryEmail,
            @Value("${grun.security.owner-bootstrap.primary-password:}") String primaryPassword,
            @Value("${grun.security.owner-bootstrap.recovery-password:}") String recoveryPassword) {
        return args -> bootstrapOwners(users, encoder,
                required(primaryEmail, "primary email").toLowerCase(Locale.ROOT),
                required(primaryPassword, "primary password"),
                required(recoveryPassword, "recovery password"));
    }

    void bootstrapOwners(UserRepository users, PasswordEncoder encoder, String primaryEmail,
                         String primaryPassword, String recoveryPassword) {
        if (primaryEmail.equalsIgnoreCase(RECOVERY_OWNER_EMAIL)) {
            throw new IllegalStateException("Primary and recovery owner identities must be different.");
        }
        createOwnerIfAbsent(users, encoder, primaryEmail, primaryPassword, "Primary Owner");
        createOwnerIfAbsent(users, encoder, RECOVERY_OWNER_EMAIL, recoveryPassword, "Recovery Owner");
    }

    private void createOwnerIfAbsent(UserRepository users, PasswordEncoder encoder, String email,
                                     String password, String name) {
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.OWNER) {
                throw new IllegalStateException("Configured owner identity already exists with a non-owner role: " + email);
            }
            log.info("Owner identity already exists; credentials were not changed: {}", email);
            return;
        }
        UserEntity owner = new UserEntity();
        owner.setEmail(email);
        owner.setName(name);
        owner.setPassword(encoder.encode(password));
        owner.setPasswordSet(true);
        owner.setEmailVerified(true);
        owner.setAccountEnabled(true);
        owner.setAccountLocked(false);
        owner.setAdminMfaEnabled(false);
        owner.setRole(UserRole.OWNER);
        users.save(owner);
        log.info("Bootstrapped owner identity: {}", email);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Owner bootstrap " + label + " is required.");
        return value.trim();
    }
}

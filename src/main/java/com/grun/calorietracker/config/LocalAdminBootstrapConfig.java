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
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalAdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminBootstrapConfig.class);

    @Bean
    @ConditionalOnProperty(name = "grun.local.admin.bootstrap-enabled", havingValue = "true")
    public CommandLineRunner localAdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${grun.local.admin.email:}") String adminEmail,
            @Value("${grun.local.admin.password:}") String adminPassword) {
        return args -> {
            String email = trimToNull(adminEmail);
            String password = trimToNull(adminPassword);

            if (email == null || password == null) {
                log.warn("Local admin bootstrap is enabled but email/password is missing. Skipping admin bootstrap.");
                return;
            }

            UserEntity admin = userRepository.findByEmail(email)
                    .orElseGet(UserEntity::new);
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(UserRole.ADMIN);

            if (admin.getName() == null || admin.getName().isBlank()) {
                admin.setName("Local Admin");
            }

            userRepository.save(admin);
            log.info("Local admin bootstrap ensured ADMIN user for email={}", email);
        };
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

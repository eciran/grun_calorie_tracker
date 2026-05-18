package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAdminBootstrapConfigTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final LocalAdminBootstrapConfig config = new LocalAdminBootstrapConfig();

    @Test
    void localAdminBootstrapRunner_createsAdminUser() throws Exception {
        when(userRepository.findByEmail("admin@grun.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("local-password")).thenReturn("encoded-password");

        CommandLineRunner runner = config.localAdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                " admin@grun.local ",
                " local-password "
        );

        runner.run();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("admin@grun.local", captor.getValue().getEmail());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertEquals(UserRole.ADMIN, captor.getValue().getRole());
        assertEquals("Local Admin", captor.getValue().getName());
        assertEquals(true, captor.getValue().getEmailVerified());
    }

    @Test
    void localAdminBootstrapRunner_skipsWhenCredentialsAreMissing() throws Exception {
        CommandLineRunner runner = config.localAdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "",
                "local-password"
        );

        runner.run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(UserEntity.class));
    }
}

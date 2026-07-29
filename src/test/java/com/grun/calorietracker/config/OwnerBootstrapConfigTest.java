package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerBootstrapConfigTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    private final OwnerBootstrapConfig config = new OwnerBootstrapConfig();

    @Test
    void createsBothOwnerIdentitiesWithoutPersistingPlaintextPasswords() {
        when(users.findByEmail("primary@gmail.com")).thenReturn(Optional.empty());
        when(users.findByEmail(OwnerBootstrapConfig.RECOVERY_OWNER_EMAIL)).thenReturn(Optional.empty());
        when(encoder.encode("primary-secret")).thenReturn("primary-hash");
        when(encoder.encode("recovery-secret")).thenReturn("recovery-hash");

        config.bootstrapOwners(users, encoder, "primary@gmail.com", "primary-secret", "recovery-secret");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(UserRole.OWNER, captor.getAllValues().get(0).getRole());
        assertEquals("primary-hash", captor.getAllValues().get(0).getPassword());
        assertEquals(OwnerBootstrapConfig.RECOVERY_OWNER_EMAIL, captor.getAllValues().get(1).getEmail());
        assertEquals("recovery-hash", captor.getAllValues().get(1).getPassword());
    }

    @Test
    void neverOverwritesAnEstablishedOwner() {
        UserEntity owner = new UserEntity();
        owner.setRole(UserRole.OWNER);
        owner.setPassword("established-hash");
        when(users.findByEmail("primary@gmail.com")).thenReturn(Optional.of(owner));
        when(users.findByEmail(OwnerBootstrapConfig.RECOVERY_OWNER_EMAIL)).thenReturn(Optional.of(owner));

        config.bootstrapOwners(users, encoder, "primary@gmail.com", "new-secret", "other-secret");

        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
        verify(encoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        assertEquals("established-hash", owner.getPassword());
    }

    @Test
    void refusesToTakeOverAnExistingNonOwnerAccount() {
        UserEntity existing = new UserEntity();
        existing.setRole(UserRole.STANDARD);
        when(users.findByEmail("primary@gmail.com")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> config.bootstrapOwners(users, encoder, "primary@gmail.com", "secret", "recovery"));
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

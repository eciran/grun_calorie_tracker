package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserDetailsServiceImpl userDetailsService = new UserDetailsServiceImpl(userRepository);

    @Test
    void loadUserByUsername_whenAccountEnabledAndUnlocked_returnsAuthenticatableUser() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(true, false)));

        UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_whenAccountDisabled_returnsDisabledUserDetails() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(false, false)));

        UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

        assertFalse(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_whenAccountLocked_returnsLockedUserDetails() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(true, true)));

        UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

        assertTrue(details.isEnabled());
        assertFalse(details.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_whenTemporarilyLoginLocked_returnsLockedUserDetails() {
        UserEntity user = user(true, false);
        user.setLoginLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

        assertTrue(details.isEnabled());
        assertFalse(details.isAccountNonLocked());
    }

    private UserEntity user(boolean accountEnabled, boolean accountLocked) {
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setPassword("encoded-password");
        user.setRole(UserRole.STANDARD);
        user.setAccountEnabled(accountEnabled);
        user.setAccountLocked(accountLocked);
        return user;
    }
}

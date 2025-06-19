package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("rawpassword");
        testUser.setName("Test User");
        testUser.setBirthdate(LocalDate.of(1990, 1, 1));
        testUser.setHeight(180.0);
        testUser.setWeight(75.0);
    }

    @Test
    void registerUser_ShouldEncryptPasswordAndSave() {
        when(passwordEncoder.encode("rawpassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        UserEntity savedUser = userService.registerUser(testUser);

        assertNotNull(savedUser);
        verify(passwordEncoder).encode("rawpassword");
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void findByEmail_ShouldReturnUser_WhenExists() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        Optional<UserEntity> result = userService.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
    }

    @Test
    void updateCurrentUser_ShouldUpdateNonNullFieldsOnly() {
        UserEntity updateData = new UserEntity();
        updateData.setWeight(70.0);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        Optional<UserEntity> result = userService.updateCurrentUser(updateData, "test@example.com");

        assertTrue(result.isPresent());
        verify(userRepository).save(testUser);
        assertEquals(70.0, testUser.getWeight());
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminUserStatusUpdateRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.NotificationPreferenceDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.impl.UserServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Spy
    private UserTimeZoneSupport userTimeZoneSupport = new UserTimeZoneSupport();

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserServiceImpl(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtUtil,
                userTimeZoneSupport,
                5,
                15
        );
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("rawpassword");
        testUser.setName("Test User");
        testUser.setAge(30);
        testUser.setHeight(180.0);
        testUser.setWeight(75.0);
        testUser.setEmailVerified(true);
        testUser.setPasswordSet(true);
        testUser.setAccountEnabled(true);
        testUser.setAccountLocked(false);
        testUser.setTimeZone("Europe/Dublin");
        testUser.setUnitPreference(UnitPreference.METRIC);
        testUser.setFailedLoginAttempts(0);
        testUser.setPushNotificationsEnabled(true);
        testUser.setMealRemindersEnabled(true);
        testUser.setHydrationRemindersEnabled(true);
        testUser.setStepRemindersEnabled(true);
    }

    @Test
    void registerUser_ShouldEncryptPasswordAndSave() {
        when(passwordEncoder.encode("rawpassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Dönüş tipi UserEntity'den UserProfileDto'ya değişti.
        UserProfileDto savedUserDto = userService.registerUser(testUser);

        assertNotNull(savedUserDto);
        assertEquals("test@example.com", savedUserDto.getEmail());
        assertEquals(true, savedUserDto.getPasswordSet());
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
    void loginUser_whenCredentialsAreValid_resetsFailedAttemptStateAndReturnsToken() {
        testUser.setFailedLoginAttempts(2);
        testUser.setLastFailedLoginAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken("test@example.com")).thenReturn("jwt-token");

        String token = userService.loginUser("test@example.com", "rawpassword");

        assertEquals("jwt-token", token);
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLoginLockedUntil());
        assertNull(testUser.getLastFailedLoginAt());
        verify(authenticationManager).authenticate(any());
        verify(userRepository).save(testUser);
    }

    @Test
    void loginUser_whenCredentialsAreInvalid_incrementsFailedAttempts() {
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> userService.loginUser("test@example.com", "wrong-password"));

        assertEquals(1, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getLastFailedLoginAt());
        assertNull(testUser.getLoginLockedUntil());
        verify(userRepository).save(testUser);
    }

    @Test
    void loginUser_whenFailedAttemptsReachLimit_temporarilyLocksAccount() {
        testUser.setFailedLoginAttempts(4);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> userService.loginUser("test@example.com", "wrong-password"));

        assertEquals(5, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getLoginLockedUntil());
        assertTrue(testUser.getLoginLockedUntil().isAfter(LocalDateTime.now()));
        verify(userRepository).save(testUser);
    }

    @Test
    void loginUser_whenAccountIsTemporarilyLocked_doesNotAuthenticate() {
        testUser.setFailedLoginAttempts(5);
        testUser.setLoginLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(testUser));

        assertThrows(BadCredentialsException.class,
                () -> userService.loginUser("test@example.com", "rawpassword"));

        verify(authenticationManager, never()).authenticate(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCurrentUser_ShouldUpdateNonNullFieldsOnly() {
        UserProfileDto updateDataDto = new UserProfileDto();
        updateDataDto.setWeight(70.0);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        UserProfileDto resultDto = userService.updateCurrentUser(updateDataDto, "test@example.com");

        assertNotNull(resultDto);
        verify(userRepository).save(testUser);
        assertEquals(70.0, testUser.getWeight());
        assertEquals(70.0, resultDto.getWeight());
        assertEquals(true, resultDto.getEmailVerified());
        assertEquals(true, resultDto.getPasswordSet());
        assertEquals(true, resultDto.getAccountEnabled());
        assertEquals(false, resultDto.getAccountLocked());
        assertEquals(true, resultDto.getGoalRecalculationRecommended());
        assertEquals("Profile metrics that affect calorie calculation changed.", resultDto.getGoalRecalculationReason());
        assertEquals("Europe/Dublin", resultDto.getTimeZone());
    }

    @Test
    void updateCurrentUser_whenTimeZoneIsValid_updatesTimeZone() {
        UserProfileDto updateDataDto = new UserProfileDto();
        updateDataDto.setTimeZone("Europe/Istanbul");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        UserProfileDto resultDto = userService.updateCurrentUser(updateDataDto, "test@example.com");

        assertEquals("Europe/Istanbul", testUser.getTimeZone());
        assertEquals("Europe/Istanbul", resultDto.getTimeZone());
    }

    @Test
    void updateCurrentUser_whenTimeZoneIsInvalid_rejectsRequest() {
        UserProfileDto updateDataDto = new UserProfileDto();
        updateDataDto.setTimeZone("Not/AZone");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateCurrentUser(updateDataDto, "test@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCurrentUser_whenUnitPreferenceProvided_updatesPreference() {
        UserProfileDto updateDataDto = new UserProfileDto();
        updateDataDto.setUnitPreference(UnitPreference.IMPERIAL);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        UserProfileDto resultDto = userService.updateCurrentUser(updateDataDto, "test@example.com");

        assertEquals(UnitPreference.IMPERIAL, testUser.getUnitPreference());
        assertEquals(UnitPreference.IMPERIAL, resultDto.getUnitPreference());
    }

    @Test
    void getNotificationPreferences_returnsCurrentPreferences() {
        testUser.setPushNotificationsEnabled(false);
        testUser.setMealRemindersEnabled(true);
        testUser.setHydrationRemindersEnabled(false);
        testUser.setStepRemindersEnabled(false);
        testUser.setFastingRemindersEnabled(true);
        testUser.setRecipeSuggestionsEnabled(false);
        testUser.setAiInsightsEnabled(true);
        testUser.setWeeklyReportsEnabled(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        NotificationPreferenceDto result = userService.getNotificationPreferences("test@example.com");

        assertEquals(false, result.getPushNotificationsEnabled());
        assertEquals(true, result.getMealRemindersEnabled());
        assertEquals(false, result.getHydrationRemindersEnabled());
        assertEquals(false, result.getStepRemindersEnabled());
        assertEquals(true, result.getFastingRemindersEnabled());
        assertEquals(false, result.getRecipeSuggestionsEnabled());
        assertEquals(true, result.getAiInsightsEnabled());
        assertEquals(false, result.getWeeklyReportsEnabled());
    }

    @Test
    void updateNotificationPreferences_updatesOnlyProvidedFields() {
        NotificationPreferenceDto request = new NotificationPreferenceDto(false, null, false);
        request.setFastingRemindersEnabled(false);
        request.setStepRemindersEnabled(false);
        request.setAiInsightsEnabled(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        NotificationPreferenceDto result = userService.updateNotificationPreferences("test@example.com", request);

        assertEquals(false, testUser.getPushNotificationsEnabled());
        assertEquals(true, testUser.getMealRemindersEnabled());
        assertEquals(false, testUser.getHydrationRemindersEnabled());
        assertEquals(false, testUser.getStepRemindersEnabled());
        assertEquals(false, testUser.getFastingRemindersEnabled());
        assertEquals(false, testUser.getAiInsightsEnabled());
        assertEquals(false, result.getPushNotificationsEnabled());
        assertEquals(true, result.getMealRemindersEnabled());
        assertEquals(false, result.getHydrationRemindersEnabled());
        assertEquals(false, result.getStepRemindersEnabled());
        assertEquals(false, result.getFastingRemindersEnabled());
        assertEquals(false, result.getAiInsightsEnabled());
    }

    @Test
    void listUsersForAdmin_appliesPaginationAndStatusFilters() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    assertEquals(0, pageable.getPageNumber());
                    assertEquals(100, pageable.getPageSize());
                    return new PageImpl<>(List.of(testUser), pageable, 1);
                });

        var result = userService.listUsersForAdmin(UserRole.STANDARD, true, false, -1, 500);

        assertEquals(1, result.getTotalElements());
        assertEquals(100, result.getSize());
        assertEquals("test@example.com", result.getContent().get(0).getEmail());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void updateUserStatus_whenAdminTargetsOtherUser_updatesStatus() {
        AdminUserStatusUpdateRequestDto request = new AdminUserStatusUpdateRequestDto();
        request.setAccountEnabled(false);
        request.setAccountLocked(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        UserProfileDto result = userService.updateUserStatus(1L, request, "admin@example.com");

        assertEquals(false, result.getAccountEnabled());
        assertEquals(true, result.getAccountLocked());
        verify(userRepository).save(testUser);
    }

    @Test
    void updateUserStatus_whenAdminLocksOwnAccount_throwsException() {
        AdminUserStatusUpdateRequestDto request = new AdminUserStatusUpdateRequestDto();
        request.setAccountEnabled(true);
        request.setAccountLocked(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserStatus(1L, request, "test@example.com"));
        verify(userRepository, never()).save(any());
    }
}

package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDemoSeedConfigTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodLogsRepository foodLogsRepository;

    @Mock
    private ExerciseItemRepository exerciseItemRepository;

    @Mock
    private ExerciseLogRepository exerciseLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final LocalDemoSeedConfig config = new LocalDemoSeedConfig();

    @Test
    void localDemoSeedRunner_createsDemoUserAndFoodProducts() throws Exception {
        when(userRepository.findByEmail("demo.user@grun.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("DemoUserPass1!")).thenReturn("encoded-password");
        when(foodItemRepository.findByNormalizedBarcode(any())).thenReturn(Optional.empty());
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                any(UserEntity.class),
                any(String.class),
                any(),
                any()
        )).thenReturn(List.of());
        when(exerciseLogRepository.findByUserAndSourceAndExternalId(any(UserEntity.class), any(), any()))
                .thenReturn(Optional.empty());
        ExerciseItemEntity running = new ExerciseItemEntity();
        running.setMetCode("RUNNING_GENERAL");
        when(exerciseItemRepository.findByMetCode("RUNNING_GENERAL")).thenReturn(Optional.of(running));

        CommandLineRunner runner = config.localDemoSeedRunner(
                userRepository,
                foodItemRepository,
                foodLogsRepository,
                exerciseItemRepository,
                exerciseLogRepository,
                passwordEncoder,
                " demo.user@grun.local ",
                " DemoUserPass1! "
        );

        runner.run();

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("demo.user@grun.local", userCaptor.getValue().getEmail());
        assertEquals("encoded-password", userCaptor.getValue().getPassword());
        assertEquals(UserRole.STANDARD, userCaptor.getValue().getRole());

        ArgumentCaptor<FoodItemEntity> foodCaptor = ArgumentCaptor.forClass(FoodItemEntity.class);
        verify(foodItemRepository, times(4)).save(foodCaptor.capture());
        FoodItemEntity firstProduct = foodCaptor.getAllValues().get(0);
        assertEquals("8690000000011", firstProduct.getNormalizedBarcode());
        assertEquals(VerificationStatus.VERIFIED, firstProduct.getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, firstProduct.getImageStatus());
        assertEquals(FoodDataSource.ADMIN_IMPORT, firstProduct.getDataSource());
        FoodItemEntity reviewProduct = foodCaptor.getAllValues().get(3);
        assertEquals("8690000000042", reviewProduct.getNormalizedBarcode());
        assertEquals(VerificationStatus.RAW_IMPORTED, reviewProduct.getVerificationStatus());
        assertEquals(ImageStatus.NEEDS_REVIEW, reviewProduct.getImageStatus());

        verify(foodLogsRepository, times(3)).save(any(FoodLogsEntity.class));
        verify(exerciseLogRepository).save(any(ExerciseLogsEntity.class));
    }

    @Test
    void localDemoSeedRunner_skipsDemoUserWhenCredentialsAreMissing() throws Exception {
        when(foodItemRepository.findByNormalizedBarcode(any())).thenReturn(Optional.empty());

        CommandLineRunner runner = config.localDemoSeedRunner(
                userRepository,
                foodItemRepository,
                foodLogsRepository,
                exerciseItemRepository,
                exerciseLogRepository,
                passwordEncoder,
                "",
                "DemoUserPass1!"
        );

        runner.run();

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(foodItemRepository, times(4)).save(any(FoodItemEntity.class));
        verify(foodLogsRepository, never()).save(any(FoodLogsEntity.class));
        verify(exerciseLogRepository, never()).save(any(ExerciseLogsEntity.class));
    }
}

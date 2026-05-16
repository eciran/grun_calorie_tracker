package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
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
    private PasswordEncoder passwordEncoder;

    private final LocalDemoSeedConfig config = new LocalDemoSeedConfig();

    @Test
    void localDemoSeedRunner_createsDemoUserAndFoodProducts() throws Exception {
        when(userRepository.findByEmail("demo.user@grun.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("DemoUserPass1!")).thenReturn("encoded-password");
        when(foodItemRepository.findByNormalizedBarcode(any())).thenReturn(Optional.empty());

        CommandLineRunner runner = config.localDemoSeedRunner(
                userRepository,
                foodItemRepository,
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
        verify(foodItemRepository, times(3)).save(foodCaptor.capture());
        FoodItemEntity firstProduct = foodCaptor.getAllValues().get(0);
        assertEquals("8690000000011", firstProduct.getNormalizedBarcode());
        assertEquals(VerificationStatus.VERIFIED, firstProduct.getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, firstProduct.getImageStatus());
        assertEquals(FoodDataSource.ADMIN_IMPORT, firstProduct.getDataSource());
    }

    @Test
    void localDemoSeedRunner_skipsDemoUserWhenCredentialsAreMissing() throws Exception {
        when(foodItemRepository.findByNormalizedBarcode(any())).thenReturn(Optional.empty());

        CommandLineRunner runner = config.localDemoSeedRunner(
                userRepository,
                foodItemRepository,
                passwordEncoder,
                "",
                "DemoUserPass1!"
        );

        runner.run();

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(foodItemRepository, times(3)).save(any(FoodItemEntity.class));
    }
}

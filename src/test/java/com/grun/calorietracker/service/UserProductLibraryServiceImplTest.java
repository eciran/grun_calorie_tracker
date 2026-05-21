package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserProductLibraryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProductLibraryServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private FoodLogsRepository foodLogsRepository;
    @Mock
    private UserFavoriteRepository userFavoriteRepository;
    @InjectMocks
    private UserProductLibraryServiceImpl service;

    @Test
    void getRecentProducts_preservesRecentLogOrder() {
        UserEntity user = user();
        FoodItemEntity first = product(8L, "Latest");
        FoodItemEntity second = product(3L, "Earlier");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findRecentAvailableFoodItemIds(eq(1L), eq("REJECTED"), any(Pageable.class)))
                .thenReturn(List.of(8L, 3L));
        when(foodItemRepository.findAllById(List.of(8L, 3L))).thenReturn(List.of(second, first));

        List<FoodProductDto> result = service.getRecentProducts("user@test.com", 10);

        assertEquals(List.of("Latest", "Earlier"), result.stream().map(FoodProductDto::getProductName).toList());
    }

    @Test
    void addFavoriteProduct_isIdempotentWhenFavoriteExists() {
        UserEntity user = user();
        FoodItemEntity product = product(5L, "Banana");
        UserFavoriteEntity favorite = new UserFavoriteEntity();
        favorite.setUser(user);
        favorite.setFoodItem(product);
        favorite.setCreatedAt(LocalDateTime.now());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(5L)).thenReturn(Optional.of(product));
        when(userFavoriteRepository.findByUserAndFoodItem(user, product)).thenReturn(Optional.of(favorite));

        FoodProductDto result = service.addFavoriteProduct("user@test.com", 5L);

        assertEquals("Banana", result.getProductName());
        verify(userFavoriteRepository, never()).save(any());
    }

    @Test
    void createCustomFood_marksProductOwnedAndManual() {
        UserEntity user = user();
        CustomFoodRequestDto request = new CustomFoodRequestDto();
        request.setName("Homemade Soup");
        request.setCalories(92.0);
        request.setProtein(4.0);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> {
            FoodItemEntity saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        FoodProductDto result = service.createCustomFood("user@test.com", request);

        assertEquals("Homemade Soup", result.getProductName());
        assertEquals(true, result.getCustom());
        verify(foodItemRepository).save(org.mockito.ArgumentMatchers.argThat(product ->
                product.getCreatedByUser() == user
                        && Boolean.TRUE.equals(product.getIsCustom())
                        && product.getDataSource() == com.grun.calorietracker.enums.FoodDataSource.MANUAL
        ));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        return user;
    }

    private FoodItemEntity product(Long id, String name) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setName(name);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        return product;
    }
}

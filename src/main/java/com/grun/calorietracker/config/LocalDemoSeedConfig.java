package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
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

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@Profile("local")
public class LocalDemoSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeedConfig.class);

    @Bean
    @ConditionalOnProperty(name = "grun.local.demo-seed.enabled", havingValue = "true")
    public CommandLineRunner localDemoSeedRunner(
            UserRepository userRepository,
            FoodItemRepository foodItemRepository,
            PasswordEncoder passwordEncoder,
            @Value("${GRUN_LOCAL_DEMO_USER_EMAIL:${grun.local.demo-seed.user-email:}}") String demoUserEmail,
            @Value("${GRUN_LOCAL_DEMO_USER_PASSWORD:${grun.local.demo-seed.user-password:}}") String demoUserPassword) {
        return args -> {
            seedDemoUser(userRepository, passwordEncoder, demoUserEmail, demoUserPassword);
            seedDemoFoodProducts(foodItemRepository);
            log.info("Local demo seed completed.");
        };
    }

    private void seedDemoUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String demoUserEmail,
            String demoUserPassword) {
        String email = trimToNull(demoUserEmail);
        String password = trimToNull(demoUserPassword);

        if (email == null || password == null) {
            log.warn("Local demo seed is enabled but demo user email/password is missing. Skipping demo user.");
            return;
        }

        UserEntity user = userRepository.findByEmail(email).orElseGet(UserEntity::new);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.STANDARD);
        user.setName("Demo User");
        user.setAge(32);
        user.setGender("MALE");
        user.setHeight(180.0);
        user.setWeight(82.0);
        user.setBmi(25.3);

        userRepository.save(user);
    }

    private void seedDemoFoodProducts(FoodItemRepository foodItemRepository) {
        for (DemoFoodProduct product : demoFoodProducts()) {
            FoodItemEntity entity = foodItemRepository.findByNormalizedBarcode(product.normalizedBarcode())
                    .orElseGet(FoodItemEntity::new);

            entity.setName(product.name());
            entity.setBarcode(product.barcode());
            entity.setNormalizedBarcode(product.normalizedBarcode());
            entity.setCalories(product.calories());
            entity.setProtein(product.protein());
            entity.setCarbs(product.carbs());
            entity.setFat(product.fat());
            entity.setFiber(product.fiber());
            entity.setSugar(product.sugar());
            entity.setDataSource(FoodDataSource.ADMIN_IMPORT);
            entity.setVerificationStatus(VerificationStatus.VERIFIED);
            entity.setImageSource(ImageSource.ADMIN_UPLOAD);
            entity.setImageStatus(ImageStatus.APPROVED);
            entity.setImageUrl(product.displayImageUrl());
            entity.setDisplayImageUrl(product.displayImageUrl());
            entity.setQualityScore(95);
            entity.setReviewPriority(0);
            entity.setUsageCount(0L);
            entity.setIsCustom(false);
            entity.setLastReviewedAt(LocalDateTime.now());
            entity.setReviewedBy("local-demo-seed");

            foodItemRepository.save(entity);
        }
    }

    private List<DemoFoodProduct> demoFoodProducts() {
        return List.of(
                new DemoFoodProduct(
                        "8690000000011",
                        "GRun Demo Greek Yogurt",
                        59.0,
                        10.0,
                        3.6,
                        0.4,
                        0.0,
                        3.2,
                        "https://cdn.grun.app/demo/greek-yogurt.jpg"
                ),
                new DemoFoodProduct(
                        "8690000000028",
                        "GRun Demo Banana",
                        89.0,
                        1.1,
                        22.8,
                        0.3,
                        2.6,
                        12.2,
                        "https://cdn.grun.app/demo/banana.jpg"
                ),
                new DemoFoodProduct(
                        "8690000000035",
                        "GRun Demo Chicken Breast",
                        165.0,
                        31.0,
                        0.0,
                        3.6,
                        0.0,
                        0.0,
                        "https://cdn.grun.app/demo/chicken-breast.jpg"
                )
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record DemoFoodProduct(
            String barcode,
            String name,
            Double calories,
            Double protein,
            Double carbs,
            Double fat,
            Double fiber,
            Double sugar,
            String displayImageUrl) {

        String normalizedBarcode() {
            return barcode;
        }
    }
}

package com.grun.calorietracker.config;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.RecipeRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
@Profile("local")
public class LocalDemoSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeedConfig.class);

    @Bean
    @ConditionalOnProperty(name = "grun.local.demo-seed.enabled", havingValue = "true")
    public CommandLineRunner localDemoSeedRunner(
            UserRepository userRepository,
            FoodItemRepository foodItemRepository,
            FoodLogsRepository foodLogsRepository,
            RecipeRepository recipeRepository,
            ExerciseItemRepository exerciseItemRepository,
            ExerciseLogRepository exerciseLogRepository,
            PasswordEncoder passwordEncoder,
            @Value("${GRUN_LOCAL_DEMO_USER_EMAIL:${grun.local.demo-seed.user-email:}}") String demoUserEmail,
            @Value("${GRUN_LOCAL_DEMO_USER_PASSWORD:${grun.local.demo-seed.user-password:}}") String demoUserPassword) {
        return args -> {
            Optional<UserEntity> demoUser = seedDemoUser(userRepository, passwordEncoder, demoUserEmail, demoUserPassword);
            List<FoodItemEntity> demoProducts = seedDemoFoodProducts(foodItemRepository);
            seedDemoReviewProduct(foodItemRepository);
            demoUser.ifPresent(user -> {
                seedDemoFoodLogs(foodLogsRepository, user, demoProducts);
                seedDemoPublicRecipes(recipeRepository, user, demoProducts);
                seedDemoExerciseLog(exerciseItemRepository, exerciseLogRepository, user);
            });
            log.info("Local demo seed completed.");
        };
    }

    private Optional<UserEntity> seedDemoUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String demoUserEmail,
            String demoUserPassword) {
        String email = trimToNull(demoUserEmail);
        String password = trimToNull(demoUserPassword);

        if (email == null || password == null) {
            log.warn("Local demo seed is enabled but demo user email/password is missing. Skipping demo user.");
            return Optional.empty();
        }

        UserEntity user = userRepository.findByEmail(email).orElseGet(UserEntity::new);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.STANDARD);
        user.setEmailVerified(true);
        user.setName("Demo User");
        user.setAge(32);
        user.setGender("MALE");
        user.setHeight(180.0);
        user.setWeight(82.0);
        user.setBmi(25.3);

        return Optional.of(userRepository.save(user));
    }

    private List<FoodItemEntity> seedDemoFoodProducts(FoodItemRepository foodItemRepository) {
        List<FoodItemEntity> seededProducts = new java.util.ArrayList<>();
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

            seededProducts.add(foodItemRepository.save(entity));
        }
        return seededProducts;
    }

    private void seedDemoReviewProduct(FoodItemRepository foodItemRepository) {
        String barcode = "8690000000042";
        FoodItemEntity entity = foodItemRepository.findByNormalizedBarcode(barcode)
                .orElseGet(FoodItemEntity::new);

        entity.setName("GRun Demo Raw Protein Bar");
        entity.setBarcode(barcode);
        entity.setNormalizedBarcode(barcode);
        entity.setCalories(230.0);
        entity.setProtein(18.0);
        entity.setCarbs(22.0);
        entity.setFat(8.0);
        entity.setFiber(5.0);
        entity.setSugar(6.0);
        entity.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        entity.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        entity.setImageSource(ImageSource.OPEN_FOOD_FACTS);
        entity.setImageStatus(ImageStatus.NEEDS_REVIEW);
        entity.setExternalImageUrl("https://images.openfoodfacts.org/demo/raw-protein-bar.jpg");
        entity.setImageUrl("https://images.openfoodfacts.org/demo/raw-protein-bar.jpg");
        entity.setQualityScore(45);
        entity.setReviewPriority(200);
        entity.setUsageCount(0L);
        entity.setIsCustom(false);
        entity.setLastExternalSyncAt(LocalDateTime.now());

        foodItemRepository.save(entity);
    }

    private void seedDemoFoodLogs(
            FoodLogsRepository foodLogsRepository,
            UserEntity user,
            List<FoodItemEntity> demoProducts) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        createFoodLogIfMissing(foodLogsRepository, user, demoProducts, "BREAKFAST", start.plusHours(8), start, end, 0, 200.0);
        createFoodLogIfMissing(foodLogsRepository, user, demoProducts, "SNACK", start.plusHours(11), start, end, 1, 120.0);
        createFoodLogIfMissing(foodLogsRepository, user, demoProducts, "LUNCH", start.plusHours(13), start, end, 2, 180.0);
    }

    private void createFoodLogIfMissing(
            FoodLogsRepository foodLogsRepository,
            UserEntity user,
            List<FoodItemEntity> demoProducts,
            String mealType,
            LocalDateTime logDate,
            LocalDateTime start,
            LocalDateTime end,
            int productIndex,
            Double portionSize) {
        if (demoProducts.size() <= productIndex) {
            return;
        }

        if (!foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(user, mealType, start, end).isEmpty()) {
            return;
        }

        FoodLogsEntity log = new FoodLogsEntity();
        log.setUser(user);
        log.setFoodItem(demoProducts.get(productIndex));
        log.setMealType(mealType);
        log.setPortionSize(portionSize);
        log.setLogDate(logDate);
        foodLogsRepository.save(log);
    }

    private void seedDemoExerciseLog(
            ExerciseItemRepository exerciseItemRepository,
            ExerciseLogRepository exerciseLogRepository,
            UserEntity user) {
        LocalDate today = LocalDate.now();
        String source = "LOCAL_DEMO";
        String externalId = "local-demo-run-" + today;

        if (exerciseLogRepository.findByUserAndSourceAndExternalId(user, source, externalId).isPresent()) {
            return;
        }

        Optional<ExerciseItemEntity> running = exerciseItemRepository.findByMetCode("RUNNING_GENERAL");
        if (running.isEmpty()) {
            log.warn("Local demo seed could not find RUNNING_GENERAL exercise item. Skipping demo exercise log.");
            return;
        }

        ExerciseLogsEntity logEntity = new ExerciseLogsEntity();
        logEntity.setUser(user);
        logEntity.setExerciseItem(running.get());
        logEntity.setDurationMinutes(30);
        logEntity.setCaloriesBurned(300.0);
        logEntity.setLogDate(today.atTime(18, 30));
        logEntity.setSource(source);
        logEntity.setExternalId(externalId);
        logEntity.setExtraData("{\"seed\":\"local-demo\"}");
        exerciseLogRepository.save(logEntity);
    }

    private void seedDemoPublicRecipes(
            RecipeRepository recipeRepository,
            UserEntity user,
            List<FoodItemEntity> demoProducts) {
        if (demoProducts.size() < 3) {
            log.warn("Local demo seed could not create public recipes because demo products are missing.");
            return;
        }
        createRecipeIfMissing(
                recipeRepository,
                user,
                "GRun Demo High Protein Yogurt Bowl",
                "Greek yogurt and banana bowl for a quick high protein breakfast.",
                "BREAKFAST",
                MarketRegion.UK_IE,
                "en",
                "https://cdn.grun.app/demo/recipes/high-protein-yogurt-bowl.jpg",
                new LinkedHashSet<>(Set.of(RecipeCategory.HIGH_PROTEIN, RecipeCategory.BREAKFAST, RecipeCategory.QUICK_MEAL)),
                List.of(
                        new DemoRecipeIngredient(demoProducts.get(0), 250.0),
                        new DemoRecipeIngredient(demoProducts.get(1), 120.0)
                )
        );
        createRecipeIfMissing(
                recipeRepository,
                user,
                "GRun Demo Chicken Meal Prep Salad",
                "Simple chicken salad prepared for meal prep and public recipe testing.",
                "LUNCH",
                MarketRegion.UK_IE,
                "en",
                "https://cdn.grun.app/demo/recipes/chicken-meal-prep-salad.jpg",
                new LinkedHashSet<>(Set.of(RecipeCategory.HIGH_PROTEIN, RecipeCategory.SALAD, RecipeCategory.MEAL_PREP)),
                List.of(
                        new DemoRecipeIngredient(demoProducts.get(2), 180.0),
                        new DemoRecipeIngredient(demoProducts.get(1), 80.0)
                )
        );
    }

    private void createRecipeIfMissing(
            RecipeRepository recipeRepository,
            UserEntity user,
            String name,
            String description,
            String mealType,
            MarketRegion marketRegion,
            String language,
            String imageUrl,
            Set<RecipeCategory> categories,
            List<DemoRecipeIngredient> ingredients) {
        boolean exists = recipeRepository.findByVisibilityAndArchivedFalseOrderByUpdatedAtDesc(RecipeVisibility.PUBLIC_ADMIN).stream()
                .anyMatch(recipe -> name.equals(recipe.getName()));
        if (exists) {
            return;
        }
        RecipeEntity recipe = new RecipeEntity();
        recipe.setOwnerUser(user);
        recipe.setName(name);
        recipe.setDescription(description);
        recipe.setMealType(mealType);
        recipe.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        recipe.setVerificationStatus(VerificationStatus.VERIFIED);
        recipe.setMarketRegion(marketRegion);
        recipe.setLanguage(language);
        recipe.setImageUrl(imageUrl);
        recipe.setImageSource(ImageSource.ADMIN_UPLOAD);
        recipe.setImageStatus(ImageStatus.APPROVED);
        recipe.setCategories(categories);
        recipe.setServingCount(1);
        double totalYield = 0.0;
        for (int index = 0; index < ingredients.size(); index++) {
            DemoRecipeIngredient item = ingredients.get(index);
            RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
            ingredient.setRecipe(recipe);
            ingredient.setFoodItem(item.foodItem());
            ingredient.setPortionSize(item.grams());
            ingredient.setPortionUnit(FoodPortionUnit.GRAM);
            ingredient.setNormalizedPortionGrams(item.grams());
            ingredient.setItemOrder(index);
            recipe.getIngredients().add(ingredient);
            totalYield += item.grams();
        }
        recipe.setTotalYieldGrams(totalYield);
        recipe.setDefaultServingGrams(totalYield);
        recipe.setSnapshotCalories(totalNutrient(ingredients, "calories"));
        recipe.setSnapshotProtein(totalNutrient(ingredients, "protein"));
        recipe.setSnapshotCarbs(totalNutrient(ingredients, "carbs"));
        recipe.setSnapshotFat(totalNutrient(ingredients, "fat"));
        recipe.setSnapshotFiber(totalNutrient(ingredients, "fiber"));
        recipe.setSnapshotSugar(totalNutrient(ingredients, "sugar"));
        recipeRepository.save(recipe);
    }

    private double totalNutrient(List<DemoRecipeIngredient> ingredients, String nutrient) {
        return ingredients.stream()
                .mapToDouble(ingredient -> nutrientValue(ingredient.foodItem(), nutrient) * ingredient.grams() / 100.0)
                .sum();
    }

    private double nutrientValue(FoodItemEntity foodItem, String nutrient) {
        Double value = switch (nutrient) {
            case "calories" -> foodItem.getCalories();
            case "protein" -> foodItem.getProtein();
            case "carbs" -> foodItem.getCarbs();
            case "fat" -> foodItem.getFat();
            case "fiber" -> foodItem.getFiber();
            case "sugar" -> foodItem.getSugar();
            default -> null;
        };
        return value == null ? 0.0 : value;
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

    private record DemoRecipeIngredient(FoodItemEntity foodItem, Double grams) {
    }
}

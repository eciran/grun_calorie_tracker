package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductFamilyMemberRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductFamilyMemberRepository;
import com.grun.calorietracker.repository.FoodProductFamilyRepository;
import com.grun.calorietracker.service.impl.FoodProductFamilyAdminServiceImpl;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodProductFamilyAdminServiceImplTest {
    @Test
    void previewRejectsDifferentBrandsWithoutWriting() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Pringles", "5000222222222")
        ));
        var preview = fixture.service().preview(request());
        assertFalse(preview.eligible());
        assertTrue(preview.errors().contains("All family members must resolve to the same brand"));
        assertFalse(fixture.fenceAcquired().get());
    }

    @Test
    void applyKeepsDistinctBarcodeProductsAndCreatesPresentationFamily() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Doritos", "5000222222222")
        ));
        var result = fixture.service().apply(request(), "admin@grun.local");
        assertTrue(fixture.fenceAcquired().get());
        assertEquals(77L, result.id());
        assertEquals(List.of("5000111111111", "5000222222222"),
                result.members().stream().map(member -> member.barcode()).toList());
        assertEquals(1L, fixture.savedFamily().get().getRepresentativeFoodItem().getId());
    }

    @Test
    void applyAllVolumeFamilyNormalizesMeasurementsAndReadyAmountLabels() {
        FoodItemEntity small = product(1L, "Coca Cola", "5000111111111");
        FoodItemEntity large = product(2L, "Coca Cola", "5000222222222");
        small.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        small.setServingSizeGrams(500.0);
        small.setServingUnit("GRAM");
        large.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        large.setServingSizeGrams(1000.0);
        large.setServingUnit("GRAM");
        var fixture = fixture(List.of(small, large));
        var request = new FoodProductFamilyRequestDto(
                "coca-cola:diet", "Coca Cola Diet", 1L,
                "Volume package identity verified against source labels",
                List.of(
                        new FoodProductFamilyMemberRequestDto(1L, 500.0, FoodPortionUnit.MILLILITER, "Package 500 ml", true),
                        new FoodProductFamilyMemberRequestDto(2L, 1000.0, FoodPortionUnit.MILLILITER, "1000ml bottle", false)
                )
        );

        var result = fixture.service().apply(request, "admin@grun.local");

        assertEquals(FoodNutritionReferenceUnit.PER_100ML, small.getNutritionReferenceUnit());
        assertEquals(FoodNutritionReferenceUnit.PER_100ML, large.getNutritionReferenceUnit());
        assertEquals("MILLILITER", small.getServingUnit());
        assertEquals("MILLILITER", large.getServingUnit());
        assertEquals(null, small.getServingSizeGrams());
        assertEquals(null, large.getServingSizeGrams());
        assertEquals(List.of("500 ml", "1 L"), result.members().stream().map(member -> member.packageLabel()).toList());
    }

    @Test
    void previewRejectsMixedMassAndVolumeFamily() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Doritos", "5000222222222")
        ));
        var mixed = new FoodProductFamilyRequestDto(
                "doritos:chilli-heatwave", "Doritos Chilli Heatwave", 1L,
                "Package identity verified against source labels",
                List.of(
                        new FoodProductFamilyMemberRequestDto(1L, 35.0, FoodPortionUnit.GRAM, "35 g", true),
                        new FoodProductFamilyMemberRequestDto(2L, 100.0, FoodPortionUnit.MILLILITER, "100 ml", false)
                )
        );

        var preview = fixture.service().preview(mixed);

        assertFalse(preview.eligible());
        assertTrue(preview.errors().contains("All members of a product family must use the same package unit"));
    }

    @Test
    void previewRejectsDefaultVariantThatIsNotRepresentative() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Doritos", "5000222222222")
        ));
        var request = new FoodProductFamilyRequestDto(
                "doritos:chilli-heatwave", "Doritos Chilli Heatwave", 1L,
                "Package identity verified against source labels",
                List.of(
                        new FoodProductFamilyMemberRequestDto(1L, 35.0, FoodPortionUnit.GRAM, "35 g", false),
                        new FoodProductFamilyMemberRequestDto(2L, 100.0, FoodPortionUnit.GRAM, "100 g", true)
                )
        );

        var preview = fixture.service().preview(request);

        assertFalse(preview.eligible());
        assertTrue(preview.errors().contains("Default package variant must be the representative product"));
        assertFalse(fixture.fenceAcquired().get());
    }

    @Test
    void previewAcceptsReviewedAccentVariantsAndSmallAbsoluteNutritionDifferences() {
        FoodItemEntity first = product(1L, "Nestlé", "5000111111111");
        FoodItemEntity second = product(2L, "Nestle", "5000222222222");
        second.setCalories(504.0);
        second.setProtein(6.8);
        second.setCarbs(58.8);
        second.setFat(25.8);

        var preview = fixture(List.of(first, second)).service().preview(request());

        assertTrue(preview.eligible());
        assertTrue(preview.warnings().isEmpty());
    }

    @Test
    void previewKeepsMaterialNutritionDifferencesInReview() {
        FoodItemEntity first = product(1L, "Doritos", "5000111111111");
        FoodItemEntity second = product(2L, "Doritos", "5000222222222");
        second.setCalories(430.0);

        var preview = fixture(List.of(first, second)).service().preview(request());

        assertTrue(preview.eligible());
        assertTrue(preview.warnings().contains(
                "Nutrition profiles differ materially; source evidence must confirm package-only variation"));
    }

    @Test
    void applyRejectsStalePreviewFingerprint() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Doritos", "5000222222222")
        ));

        var exception = assertThrows(IllegalArgumentException.class, () ->
                fixture.service().apply(request(), "stale", List.of(), "admin@grun.local"));

        assertTrue(exception.getMessage().contains("Preview is stale"));
        assertTrue(fixture.fenceAcquired().get());
    }

    @Test
    void applyRequiresEveryPreviewWarningAcknowledgement() {
        FoodItemEntity first = product(1L, "Doritos", "5000111111111");
        FoodItemEntity second = product(2L, "Doritos", "5000222222222");
        second.setCalories(430.0);
        var fixture = fixture(List.of(first, second));
        var preview = fixture.service().preview(request());

        var exception = assertThrows(IllegalArgumentException.class, () ->
                fixture.service().apply(request(), preview.previewFingerprint(), List.of(), "admin@grun.local"));

        assertTrue(exception.getMessage().contains("exactly match"));
        assertTrue(fixture.fenceAcquired().get());
    }

    @Test
    void previewBlocksPackageSizeInFamilyDisplayName() {
        var fixture = fixture(List.of(
                product(1L, "Doritos", "5000111111111"),
                product(2L, "Doritos", "5000222222222")
        ));
        var sizedName = new FoodProductFamilyRequestDto(
                "doritos:chilli-heatwave", "Doritos Chilli Heatwave 100 G", 1L,
                "Package identity verified against source labels", request().members());

        var preview = fixture.service().preview(sizedName);

        assertFalse(preview.eligible());
        assertTrue(preview.errors().contains("Family display name must not contain a package size"));
    }

    @Test
    void applyRejectsProductChangedAfterPreview() {
        FoodItemEntity first = product(1L, "Doritos", "5000111111111");
        FoodItemEntity second = product(2L, "Doritos", "5000222222222");
        var fixture = fixture(List.of(first, second));
        var preview = fixture.service().preview(request());
        second.setCalories(430.0);

        var exception = assertThrows(IllegalArgumentException.class, () ->
                fixture.service().apply(request(), preview.previewFingerprint(), List.of(), "admin@grun.local"));

        assertTrue(exception.getMessage().contains("Preview is stale"));
    }

    private Fixture fixture(List<FoodItemEntity> products) {
        AtomicReference<FoodProductFamilyEntity> savedFamily = new AtomicReference<>();
        AtomicBoolean fenceAcquired = new AtomicBoolean();
        FoodItemRepository productRepository = proxy(FoodItemRepository.class, (method, args) ->
                method.getName().equals("findAllById") ? products : defaultValue(method.getReturnType()));
        FoodProductFamilyRepository familyRepository = proxy(FoodProductFamilyRepository.class, (method, args) -> {
            if (method.getName().equals("findFirstByFamilyKeyIgnoreCaseAndActiveTrue")) return Optional.empty();
            if (method.getName().equals("saveAndFlush")) {
                FoodProductFamilyEntity family = (FoodProductFamilyEntity) args[0];
                family.setId(77L);
                savedFamily.set(family);
                return family;
            }
            return defaultValue(method.getReturnType());
        });
        FoodProductFamilyMemberRepository memberRepository = proxy(FoodProductFamilyMemberRepository.class,
                (method, args) -> switch (method.getName()) {
                    case "findActiveMembershipsForProducts" -> List.of();
                    case "saveAllAndFlush" -> args[0];
                    default -> defaultValue(method.getReturnType());
                });
        ExternalFoodImportFence fence = new ExternalFoodImportFence(null) {
            @Override
            public void acquire() {
                fenceAcquired.set(true);
            }
        };
        return new Fixture(new FoodProductFamilyAdminServiceImpl(
                productRepository, familyRepository, memberRepository, fence
        ), fenceAcquired, savedFamily);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.invoke(method, args == null ? new Object[0] : args));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private FoodProductFamilyRequestDto request() {
        return new FoodProductFamilyRequestDto(
                "doritos:chilli-heatwave", "Doritos Chilli Heatwave", 1L,
                "Package identity verified against source labels",
                List.of(
                        new FoodProductFamilyMemberRequestDto(1L, 35.0, FoodPortionUnit.GRAM, "35 g", true),
                        new FoodProductFamilyMemberRequestDto(2L, 100.0, FoodPortionUnit.GRAM, "100 g", false)
                )
        );
    }

    private FoodItemEntity product(Long id, String brand, String barcode) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setName("Chilli Heatwave");
        product.setDisplayName("Doritos Chilli Heatwave");
        product.setBrand(brand);
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setCalories(500.0);
        product.setProtein(6.0);
        product.setCarbs(58.0);
        product.setFat(25.0);
        return product;
    }

    private record Fixture(
            FoodProductFamilyAdminServiceImpl service,
            AtomicBoolean fenceAcquired,
            AtomicReference<FoodProductFamilyEntity> savedFamily
    ) {
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}

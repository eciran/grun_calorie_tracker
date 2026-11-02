package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import com.grun.calorietracker.entity.FoodProductFamilyMemberEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductFamilyMemberRepository;
import com.grun.calorietracker.repository.FoodProductFamilyRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
class ProductPackageFamilySearchIntegrationTest {
    @Autowired FoodItemRepository foodItemRepository;
    @Autowired FoodItemLocalizationRepository localizationRepository;
    @Autowired FoodItemServingOptionRepository servingOptionRepository;
    @Autowired FoodItemServingOptionLocalizationRepository servingLocalizationRepository;
    @Autowired FoodProductFamilyRepository familyRepository;
    @Autowired FoodProductFamilyMemberRepository memberRepository;
    @Autowired EntityManager entityManager;

    private FoodItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodItemServiceImpl(
                foodItemRepository, localizationRepository, servingOptionRepository,
                servingLocalizationRepository, null, null, null, null
        );
        service.setFoodProductFamilyMemberRepository(memberRepository);
    }

    @Test
    void nameSearchReturnsOneFamilyWhileBarcodeLookupKeepsExactVariant() {
        FoodItemEntity small = product("Doritos Chilli Heatwave 35 G", "5000111111111");
        FoodItemEntity large = product("Doritos Chilli Heatwave 100 G", "5000222222222");
        foodItemRepository.saveAllAndFlush(List.of(small, large));
        servingOptionRepository.saveAndFlush(servingOption(small, "1 bowl", 30.0));

        FoodProductFamilyEntity family = new FoodProductFamilyEntity();
        family.setFamilyKey("doritos:chilli-heatwave");
        family.setBrandKey("doritos");
        family.setVariantKey("chilli-heatwave");
        family.setDisplayName("Doritos Chilli Heatwave");
        family.setRepresentativeFoodItem(small);
        family.setEvidenceReason("Test fixture with verified package labels");
        family.setAppliedBy("integration-test@grun.local");
        family.setActive(true);
        family = familyRepository.saveAndFlush(family);
        memberRepository.saveAllAndFlush(List.of(
                member(family, small, 35.0, "35 g", true, 0),
                member(family, large, 100.0, "100 g", false, 1)
        ));
        assertEquals(1, familyRepository.search("Chilli Heatwave", true, PageRequest.of(0, 25)).getTotalElements());
        assertEquals(1, familyRepository.search(large.getBarcode(), true, PageRequest.of(0, 25)).getTotalElements());
        assertEquals(0, familyRepository.search("Chilli Heatwave", false, PageRequest.of(0, 25)).getTotalElements());
        entityManager.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("doritos chilli heatwave");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        var search = service.searchFoodItems(criteria, 0, 25);
        assertEquals(1, search.getContent().size());
        assertEquals(small.getId(), search.getContent().get(0).getId());
        assertTrue(search.getContent().get(0).getProductFamilyRepresentative());
        assertEquals("Doritos Chilli Heatwave", search.getContent().get(0).getDisplayName());
        assertEquals("Doritos Chilli Heatwave", search.getContent().get(0).getProductFamilyDisplayName());
        assertEquals(2, search.getContent().get(0).getPackageVariants().size());
        assertEquals(3, search.getContent().get(0).getConsumptionOptions().size());
        assertEquals(List.of("1 bowl", "35 g", "100 g"), search.getContent().get(0).getConsumptionOptions().stream()
                .map(com.grun.calorietracker.dto.FoodConsumptionOptionDto::label).toList());
        assertEquals(List.of(small.getId(), small.getId(), large.getId()), search.getContent().get(0).getConsumptionOptions().stream()
                .map(com.grun.calorietracker.dto.FoodConsumptionOptionDto::foodItemId).toList());

        var barcodeResult = service.getFoodProductByBarcode(large.getBarcode());
        assertEquals(large.getId(), barcodeResult.getId());
        assertEquals("Doritos Chilli Heatwave 100 G", barcodeResult.getDisplayName());
        assertEquals("Doritos Chilli Heatwave", barcodeResult.getProductFamilyDisplayName());
        assertFalse(barcodeResult.getProductFamilyRepresentative());
        assertEquals(2, barcodeResult.getPackageVariants().size());
        assertEquals(3, barcodeResult.getConsumptionOptions().size());
        assertTrue(barcodeResult.getConsumptionOptions().stream()
                .filter(com.grun.calorietracker.dto.FoodConsumptionOptionDto::selectedOption)
                .allMatch(option -> option.foodItemId().equals(large.getId())));
        assertTrue(barcodeResult.getPackageVariants().stream()
                .filter(variant -> variant.selectedVariant())
                .allMatch(variant -> variant.productId().equals(large.getId())));
    }

    @Test
    void liquidPackageUsesVerifiedMassVolumeBridgeWithoutChangingScreens() {
        FoodItemEntity small = product("Biotiful Kefir Drink Original 500 Ml", "5060337220415");
        FoodItemEntity large = product("Biotiful Kefir Drink Original 1 L", "5060337221085");
        small.setBrand("Biotiful gut health");
        large.setBrand("Biotiful gut health");
        small.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        large.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        foodItemRepository.saveAllAndFlush(List.of(small, large));
        FoodItemServingOptionEntity glass = servingOption(small, "1 glass", 250.0);
        glass.setMlVolume(250.0);
        glass = servingOptionRepository.saveAndFlush(glass);

        FoodProductFamilyEntity family = new FoodProductFamilyEntity();
        family.setFamilyKey("biotiful:kefir-drink-original");
        family.setBrandKey("biotifulguthealth");
        family.setVariantKey("kefir-drink-original");
        family.setDisplayName("Biotiful Kefir Drink Original");
        family.setRepresentativeFoodItem(small);
        family.setEvidenceReason("Source serving confirms 250 ml equals the reported 250 g nutrition amount");
        family.setAppliedBy("integration-test@grun.local");
        family.setActive(true);
        family = familyRepository.saveAndFlush(family);
        memberRepository.saveAllAndFlush(List.of(
                member(family, small, 500.0, FoodPortionUnit.MILLILITER, "500 ml", true, 0),
                member(family, large, 1000.0, FoodPortionUnit.MILLILITER, "1 L", false, 1)
        ));
        entityManager.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("biotiful kefir drink original");
        criteria.setMarketRegion(MarketRegion.UK_IE);
        var result = service.searchFoodItems(criteria, 0, 25).getContent().get(0);

        assertEquals(3, result.getConsumptionOptions().size());
        var packageOption = result.getConsumptionOptions().stream()
                .filter(option -> option.label().equals("500 ml"))
                .findFirst().orElseThrow();
        assertEquals(small.getId(), packageOption.foodItemId());
        assertEquals(glass.getId(), packageOption.servingOptionId());
        assertEquals(500.0, packageOption.gramWeight());
        assertEquals(500.0, packageOption.mlVolume());
        assertEquals(FoodPortionUnit.MILLILITER, packageOption.portionUnit());
    }

    private FoodItemServingOptionEntity servingOption(FoodItemEntity product, String label, double grams) {
        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setFoodItem(product);
        option.setLabel(label);
        option.setUnitType(FoodServingOptionUnit.BOWL);
        option.setQuantity(1.0);
        option.setGramWeight(grams);
        option.setIsDefault(true);
        option.setSource(FoodServingOptionSource.ADMIN);
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        option.setUserVisible(true);
        return option;
    }

    private FoodProductFamilyMemberEntity member(
            FoodProductFamilyEntity family,
            FoodItemEntity product,
            double quantity,
            String label,
            boolean defaultVariant,
            int order
    ) {
        return member(family, product, quantity, FoodPortionUnit.GRAM, label, defaultVariant, order);
    }

    private FoodProductFamilyMemberEntity member(
            FoodProductFamilyEntity family,
            FoodItemEntity product,
            double quantity,
            FoodPortionUnit unit,
            String label,
            boolean defaultVariant,
            int order
    ) {
        FoodProductFamilyMemberEntity member = new FoodProductFamilyMemberEntity();
        member.setFamily(family);
        member.setFoodItem(product);
        member.setPackageQuantity(BigDecimal.valueOf(quantity));
        member.setPackageUnit(unit);
        member.setPackageLabel(label);
        member.setDefaultVariant(defaultVariant);
        member.setSortOrder(order);
        return member;
    }

    private FoodItemEntity product(String name, String barcode) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setDisplayName(name);
        product.setBrand("Doritos");
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setCalories(500.0);
        product.setProtein(6.0);
        product.setCarbs(58.0);
        product.setFat(25.0);
        product.setQualityScore(90);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }
}

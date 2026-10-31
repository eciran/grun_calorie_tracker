package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import com.grun.calorietracker.entity.FoodProductFamilyMemberEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;
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

        var barcodeResult = service.getFoodProductByBarcode(large.getBarcode());
        assertEquals(large.getId(), barcodeResult.getId());
        assertEquals("Doritos Chilli Heatwave 100 G", barcodeResult.getDisplayName());
        assertEquals("Doritos Chilli Heatwave", barcodeResult.getProductFamilyDisplayName());
        assertFalse(barcodeResult.getProductFamilyRepresentative());
        assertEquals(2, barcodeResult.getPackageVariants().size());
        assertTrue(barcodeResult.getPackageVariants().stream()
                .filter(variant -> variant.selectedVariant())
                .allMatch(variant -> variant.productId().equals(large.getId())));
    }

    private FoodProductFamilyMemberEntity member(
            FoodProductFamilyEntity family,
            FoodItemEntity product,
            double quantity,
            String label,
            boolean defaultVariant,
            int order
    ) {
        FoodProductFamilyMemberEntity member = new FoodProductFamilyMemberEntity();
        member.setFamily(family);
        member.setFoodItem(product);
        member.setPackageQuantity(BigDecimal.valueOf(quantity));
        member.setPackageUnit(FoodPortionUnit.GRAM);
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

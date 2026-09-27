package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodBrandAliasEntity;
import com.grun.calorietracker.entity.FoodBrandEntity;
import com.grun.calorietracker.entity.FoodCategoryEntity;
import com.grun.calorietracker.enums.FoodBrandStatus;
import com.grun.calorietracker.repository.FoodBrandAliasRepository;
import com.grun.calorietracker.repository.FoodBrandRepository;
import com.grun.calorietracker.repository.FoodCategoryRepository;
import com.grun.calorietracker.service.impl.CatalogClassificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogClassificationServiceImplTest {
    private final FoodBrandRepository brands = mock(FoodBrandRepository.class);
    private final FoodBrandAliasRepository aliases = mock(FoodBrandAliasRepository.class);
    private final FoodCategoryRepository categories = mock(FoodCategoryRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogClassificationServiceImpl service =
            new CatalogClassificationServiceImpl(brands, aliases, categories, jdbc);

    @Test
    void resolvesReviewedAliasToCanonicalBrand() {
        FoodBrandEntity brand = brand(7L, "Alpro", FoodBrandStatus.ACTIVE);
        FoodBrandAliasEntity alias = new FoodBrandAliasEntity();
        alias.setBrand(brand);
        when(brands.findByNormalizedKeyAndStatus("alprofoods", FoodBrandStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(aliases.findByNormalizedAlias("alprofoods")).thenReturn(Optional.of(alias));

        var result = service.resolveBrand("Alpro Foods");

        assertTrue(result.isPresent());
        assertEquals(7L, result.orElseThrow().id());
        assertEquals("Alpro", result.orElseThrow().canonicalName());
    }

    @Test
    void buildsBilingualHierarchyWithoutExposingInactiveCategories() {
        FoodCategoryEntity beverages = category(1L, null, "beverages", "Beverages", "İçecekler", 1);
        FoodCategoryEntity milk = category(2L, beverages, "plant-milk", "Plant milk", "Bitkisel süt", 2);
        when(categories.findAllByActiveTrueOrderBySortOrderAscNameEnAsc())
                .thenReturn(List.of(milk, beverages));

        var tree = service.activeCategoryTree();

        assertEquals(1, tree.size());
        assertEquals("İçecekler", tree.get(0).nameTr());
        assertEquals("plant-milk", tree.get(0).children().get(0).slug());
    }

    @Test
    void rejectsCategoryCycleInsteadOfRecursingForever() {
        FoodCategoryEntity first = category(1L, null, "first", "First", "Bir", 1);
        FoodCategoryEntity second = category(2L, first, "second", "Second", "İki", 2);
        first.setParent(second);
        when(categories.findAllByActiveTrueOrderBySortOrderAscNameEnAsc())
                .thenReturn(List.of(first, second));

        assertThrows(IllegalStateException.class, service::activeCategoryTree);
    }

    private FoodBrandEntity brand(Long id, String name, FoodBrandStatus status) {
        FoodBrandEntity brand = new FoodBrandEntity();
        brand.setId(id);
        brand.setCanonicalName(name);
        brand.setStatus(status);
        return brand;
    }

    private FoodCategoryEntity category(Long id, FoodCategoryEntity parent, String slug,
                                        String en, String tr, int order) {
        FoodCategoryEntity category = new FoodCategoryEntity();
        category.setId(id);
        category.setParent(parent);
        category.setSlug(slug);
        category.setNameEn(en);
        category.setNameTr(tr);
        category.setSortOrder(order);
        category.setActive(true);
        return category;
    }
}

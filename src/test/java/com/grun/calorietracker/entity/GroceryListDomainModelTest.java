package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.GroceryCategory;
import com.grun.calorietracker.enums.GroceryListItemSource;
import com.grun.calorietracker.enums.GroceryListSourceType;
import com.grun.calorietracker.enums.GroceryListStatus;
import com.grun.calorietracker.service.support.GroceryListLimits;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroceryListDomainModelTest {

    @Test
    void listLifecycle_appliesSafeDefaultsAndOwnsItems() throws Exception {
        GroceryListEntity list = new GroceryListEntity();
        GroceryListItemEntity item = new GroceryListItemEntity();

        list.addItem(item);
        list.onCreate();

        assertEquals(GroceryListSourceType.MEAL_PLAN, list.getSourceType());
        assertEquals(GroceryListStatus.ACTIVE, list.getStatus());
        assertEquals(0L, list.getVersion());
        assertNotNull(list.getCreatedAt());
        assertNotNull(list.getUpdatedAt());
        assertEquals(list, item.getGroceryList());
        assertNotNull(GroceryListEntity.class.getDeclaredField("version").getAnnotation(Version.class));

        list.removeItem(item);

        assertTrue(list.getItems().isEmpty());
        assertNull(item.getGroceryList());
    }

    @Test
    void itemLifecycle_preservesEditableWorkflowDefaults() throws Exception {
        GroceryListItemEntity item = new GroceryListItemEntity();
        item.setSource(GroceryListItemSource.MANUAL);
        item.setDisplayName("Coffee");
        item.onCreate();

        assertEquals(GroceryCategory.OTHER, item.getCategory());
        assertFalse(item.getPurchased());
        assertFalse(item.getExcluded());
        assertFalse(item.getQuantityOverridden());
        assertEquals(0, item.getPlannedUses());
        assertEquals(0L, item.getVersion());
        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());
        assertNotNull(GroceryListItemEntity.class.getDeclaredField("version").getAnnotation(Version.class));
    }

    @Test
    void limits_areBoundedForUserEditableData() {
        assertEquals(20, GroceryListLimits.MAX_ACTIVE_LISTS_PER_USER);
        assertEquals(300, GroceryListLimits.MAX_ITEMS_PER_LIST);
        assertEquals(160, GroceryListLimits.MAX_ITEM_NAME_LENGTH);
        assertTrue(GroceryListLimits.MAX_DISPLAY_QUANTITY > 0);
        assertTrue(GroceryListLimits.MAX_NORMALIZED_GRAMS > GroceryListLimits.MAX_DISPLAY_QUANTITY);
    }
}

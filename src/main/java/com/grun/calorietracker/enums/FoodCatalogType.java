package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Catalog classification for food records.")
public enum FoodCatalogType {
    @Schema(description = "Packaged branded product, usually identified by barcode.")
    BRANDED_PRODUCT,

    @Schema(description = "Generic ingredient or base food, usually sourced from official nutrition datasets.")
    GENERIC_INGREDIENT,

    @Schema(description = "Regional or traditional dish represented by a standard recipe/nutrition profile.")
    LOCAL_DISH,

    @Schema(description = "Private food created by the authenticated user.")
    USER_CUSTOM
}

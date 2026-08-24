package com.grun.calorietracker.service.media;

public enum MediaNamespace {
    PRIVATE_PRODUCT_EVIDENCE("private/product-evidence"),
    PROFILE_AVATAR("profile/avatars"),
    CATALOG_MEDIA("public/catalog"),
    RECIPE_MEDIA("public/recipes");

    private final String path;

    MediaNamespace(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}

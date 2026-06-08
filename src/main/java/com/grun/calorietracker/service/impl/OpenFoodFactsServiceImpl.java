package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.OpenFoodFactsService;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OpenFoodFactsServiceImpl implements OpenFoodFactsService {

    private static final int DEFAULT_SEARCH_SIZE = 20;

    private final RestClient restClient;

    public OpenFoodFactsServiceImpl(@Value("${openfoodfacts.base-url}") String baseUrl,
                                    RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<FoodProductDto> searchProducts(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null) {
            return List.of();
        }

        return fetchSearchResults(normalizedQuery, null, null);
    }

    @Override
    public Optional<FoodProductDto> getProductByBarcode(String barcode) {
        String normalizedBarcode = normalize(barcode);
        if (normalizedBarcode == null) {
            return Optional.empty();
        }

        try {
            JsonNode response = restClient.get()
                    .uri("/api/v2/product/{barcode}.json", normalizedBarcode)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.path("status").asInt(0) != 1) {
                return Optional.empty();
            }

            FoodProductDto product = mapProduct(response.path("product"), normalizedBarcode);
            return product.getProductName() == null ? Optional.empty() : Optional.of(product);
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<FoodProductDto> searchProductsByCriteria(FoodSearchCriteriaDto criteria) {
        if (criteria == null) {
            return List.of();
        }

        String normalizedQuery = normalize(criteria.getQuery());
        if (normalizedQuery == null) {
            return List.of();
        }

        return fetchSearchResults(normalizedQuery, criteria.getBrand(), criteria.getNutriScore(), criteria.getMarketRegion());
    }

    private List<FoodProductDto> fetchSearchResults(String query, String brand, String nutriScore) {
        return fetchSearchResults(query, brand, nutriScore, null);
    }

    private List<FoodProductDto> fetchSearchResults(String query, String brand, String nutriScore, MarketRegion marketRegion) {
        try {
            String countryTag = toOpenFoodFactsCountryTag(marketRegion);
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi/search.pl")
                            .queryParam("search_terms", query)
                            .queryParam("search_simple", 1)
                            .queryParam("action", "process")
                            .queryParam("json", 1)
                            .queryParam("page_size", DEFAULT_SEARCH_SIZE)
                            .queryParamIfPresent("tagtype_0", java.util.Optional.ofNullable(countryTag).map(tag -> "countries"))
                            .queryParamIfPresent("tag_contains_0", java.util.Optional.ofNullable(countryTag).map(tag -> "contains"))
                            .queryParamIfPresent("tag_0", java.util.Optional.ofNullable(countryTag))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode products = response == null ? null : response.path("products");
            if (products == null || !products.isArray()) {
                return List.of();
            }

            String normalizedBrand = normalizeLower(brand);
            String normalizedNutriScore = normalizeLower(nutriScore);
            List<FoodProductDto> result = new ArrayList<>();
            for (JsonNode productNode : products) {
                FoodProductDto product = mapProduct(productNode, text(productNode, "code"));
                if (product.getProductName() == null) {
                    continue;
                }
                if (normalizedBrand != null && !containsIgnoreCase(product.getBrand(), normalizedBrand)) {
                    continue;
                }
                if (normalizedNutriScore != null && !normalizedNutriScore.equals(normalizeLower(product.getNutriScore()))) {
                    continue;
                }
                product.setMarketRegion(marketRegion);
                result.add(product);
            }
            return result;
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private FoodProductDto mapProduct(JsonNode productNode, String fallbackBarcode) {
        FoodProductDto dto = new FoodProductDto();
        dto.setBarcode(firstText(productNode, fallbackBarcode, "code", "barcode"));
        dto.setProductName(firstText(productNode, null, "product_name", "product_name_en", "generic_name"));
        dto.setBrand(text(productNode, "brands"));
        dto.setImageUrl(firstText(productNode, null, "image_front_url", "image_url"));
        dto.setExternalImageUrl(dto.getImageUrl());
        dto.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        dto.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        dto.setImageSource(ImageSource.OPEN_FOOD_FACTS);
        dto.setImageStatus(ImageStatus.NEEDS_REVIEW);
        dto.setIngredientsText(firstText(productNode, null, "ingredients_text", "ingredients_text_en"));
        dto.setAllergens(firstText(productNode, null, "allergens_from_ingredients", "allergens"));
        dto.setNutriScore(normalizeLower(firstText(productNode, null, "nutriscore_grade", "nutri_score")));
        dto.setServingSize(NutritionValueNormalizer.servingSize(number(productNode, "serving_quantity")));
        if (dto.getServingSize() != null) {
            dto.setServingUnit("g");
        }

        JsonNode nutriments = productNode.path("nutriments");
        dto.setCalories(NutritionValueNormalizer.calories(number(nutriments, "energy-kcal_100g", "energy-kcal_serving")));
        dto.setProtein(NutritionValueNormalizer.macro(number(nutriments, "proteins_100g", "proteins_serving")));
        dto.setFat(NutritionValueNormalizer.macro(number(nutriments, "fat_100g", "fat_serving")));
        dto.setCarbs(NutritionValueNormalizer.macro(number(nutriments, "carbohydrates_100g", "carbohydrates_serving")));
        dto.setFiber(NutritionValueNormalizer.macro(number(nutriments, "fiber_100g", "fiber_serving")));
        dto.setSugar(NutritionValueNormalizer.macro(number(nutriments, "sugars_100g", "sugars_serving")));
        dto.setSodium(NutritionValueNormalizer.micronutrient(number(nutriments, "sodium_100g", "sodium_serving")));
        dto.setPotassium(NutritionValueNormalizer.micronutrient(number(nutriments, "potassium_100g", "potassium_serving")));
        dto.setCholesterol(NutritionValueNormalizer.micronutrient(number(nutriments, "cholesterol_100g", "cholesterol_serving")));
        dto.setCalcium(NutritionValueNormalizer.micronutrient(number(nutriments, "calcium_100g", "calcium_serving")));
        dto.setIron(NutritionValueNormalizer.micronutrient(number(nutriments, "iron_100g", "iron_serving")));
        dto.setMagnesium(NutritionValueNormalizer.micronutrient(number(nutriments, "magnesium_100g", "magnesium_serving")));
        dto.setZinc(NutritionValueNormalizer.micronutrient(number(nutriments, "zinc_100g", "zinc_serving")));
        dto.setVitaminA(NutritionValueNormalizer.micronutrient(number(nutriments, "vitamin-a_100g", "vitamin-a_serving", "vitamin_a_100g", "vitamin_a_serving")));
        dto.setVitaminC(NutritionValueNormalizer.micronutrient(number(nutriments, "vitamin-c_100g", "vitamin-c_serving", "vitamin_c_100g", "vitamin_c_serving")));
        dto.setVitaminD(NutritionValueNormalizer.micronutrient(number(nutriments, "vitamin-d_100g", "vitamin-d_serving", "vitamin_d_100g", "vitamin_d_serving")));
        dto.setVitaminE(NutritionValueNormalizer.micronutrient(number(nutriments, "vitamin-e_100g", "vitamin-e_serving", "vitamin_e_100g", "vitamin_e_serving")));
        dto.setVitaminB12(NutritionValueNormalizer.micronutrient(number(nutriments, "vitamin-b12_100g", "vitamin-b12_serving", "vitamin_b12_100g", "vitamin_b12_serving")));
        dto.setSaturatedFat(NutritionValueNormalizer.macro(number(nutriments, "saturated-fat_100g", "saturated-fat_serving", "saturated_fat_100g", "saturated_fat_serving")));
        dto.setTransFat(NutritionValueNormalizer.macro(number(nutriments, "trans-fat_100g", "trans-fat_serving", "trans_fat_100g", "trans_fat_serving")));
        dto.setSugarAlcohol(NutritionValueNormalizer.macro(number(nutriments, "sugar-alcohol_100g", "sugar-alcohol_serving", "sugar_alcohol_100g", "sugar_alcohol_serving")));
        return dto;
    }

    private String toOpenFoodFactsCountryTag(MarketRegion marketRegion) {
        if (marketRegion == null) {
            return null;
        }
        return switch (marketRegion) {
            case TR -> "en:turkey";
            case UK_IE -> "en:united-kingdom";
            case EU, GLOBAL -> null;
        };
    }

    private String firstText(JsonNode node, String fallback, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return normalize(value);
    }

    private Double number(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            String textValue = text(node, field);
            if (textValue != null) {
                try {
                    return Double.parseDouble(textValue);
                } catch (NumberFormatException ignored) {
                    // Try the next candidate field.
                }
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(String value, String normalizedExpected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedExpected);
    }

    private String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

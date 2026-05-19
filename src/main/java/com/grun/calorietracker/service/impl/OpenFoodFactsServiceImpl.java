package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.OpenFoodFactsService;
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

        return fetchSearchResults(normalizedQuery, criteria.getBrand(), criteria.getNutriScore());
    }

    private List<FoodProductDto> fetchSearchResults(String query, String brand, String nutriScore) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi/search.pl")
                            .queryParam("search_terms", query)
                            .queryParam("search_simple", 1)
                            .queryParam("action", "process")
                            .queryParam("json", 1)
                            .queryParam("page_size", DEFAULT_SEARCH_SIZE)
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
        dto.setServingSize(number(productNode, "serving_quantity"));
        if (dto.getServingSize() != null) {
            dto.setServingUnit("g");
        }

        JsonNode nutriments = productNode.path("nutriments");
        dto.setCalories(number(nutriments, "energy-kcal_100g", "energy-kcal_serving"));
        dto.setProtein(number(nutriments, "proteins_100g", "proteins_serving"));
        dto.setFat(number(nutriments, "fat_100g", "fat_serving"));
        dto.setCarbs(number(nutriments, "carbohydrates_100g", "carbohydrates_serving"));
        dto.setFiber(number(nutriments, "fiber_100g", "fiber_serving"));
        dto.setSugar(number(nutriments, "sugars_100g", "sugars_serving"));
        dto.setSodium(number(nutriments, "sodium_100g", "sodium_serving"));
        return dto;
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

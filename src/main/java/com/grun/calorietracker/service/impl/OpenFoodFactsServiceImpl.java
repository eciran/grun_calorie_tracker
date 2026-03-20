package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.service.OpenFoodFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OpenFoodFactsServiceImpl implements OpenFoodFactsService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openfoodfacts.base-url:https://world.openfoodfacts.org}")
    private String baseUrl;

    @Override
    public List<FoodProductDto> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/cgi/search.pl")
                .queryParam("search_terms", query.trim())
                .queryParam("search_simple", 1)
                .queryParam("action", "process")
                .queryParam("json", 1)
                .queryParam("page_size", 20)
                .queryParam("fields", "code,product_name,brands,image_url,nutriments,allergens,nutrition_grades,ingredients_text")
                .toUriString();

        try {
            String response = RestClient.create()
                    .get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode products = root.path("products");

            if (!products.isArray()) {
                return List.of();
            }

            List<FoodProductDto> result = new ArrayList<>();
            for (JsonNode productNode : products) {
                FoodProductDto dto = mapProductNode(productNode);
                if (dto.getProductName() != null && !dto.getProductName().isBlank()) {
                    result.add(dto);
                }
            }

            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    public Optional<FoodProductDto> getProductByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v2/product/{barcode}")
                .queryParam("fields", "code,product_name,brands,image_url,nutriments,allergens,nutrition_grades,ingredients_text")
                .buildAndExpand(barcode.trim())
                .toUriString();

        try {
            String response = RestClient.create()
                    .get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            if (root.path("status").asInt() != 1) {
                return Optional.empty();
            }

            JsonNode productNode = root.path("product");
            if (productNode.isMissingNode() || productNode.isNull()) {
                return Optional.empty();
            }

            FoodProductDto dto = mapProductNode(productNode);
            if (dto.getBarcode() == null || dto.getBarcode().isBlank()) {
                dto.setBarcode(barcode.trim());
            }

            return Optional.of(dto);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<FoodProductDto> searchProductsByCriteria(FoodSearchCriteriaDto criteria) {
        List<FoodProductDto> results = searchProducts(criteria.getQuery());

        return results.stream()
                .filter(product -> criteria.getMinCalories() == null || safe(product.getCalories()) >= criteria.getMinCalories())
                .filter(product -> criteria.getMaxCalories() == null || safe(product.getCalories()) <= criteria.getMaxCalories())
                .filter(product -> criteria.getNutriScore() == null
                        || criteria.getNutriScore().isBlank()
                        || equalsIgnoreCase(product.getNutriScore(), criteria.getNutriScore()))
                .toList();
    }

    private FoodProductDto mapProductNode(JsonNode productNode) {
        JsonNode nutriments = productNode.path("nutriments");

        FoodProductDto dto = new FoodProductDto();
        dto.setBarcode(readText(productNode, "code"));
        dto.setProductName(readText(productNode, "product_name"));
        dto.setBrand(readText(productNode, "brands"));
        dto.setImageUrl(readText(productNode, "image_url"));
        dto.setCalories(readDouble(nutriments, "energy-kcal_100g"));
        dto.setProtein(readDouble(nutriments, "proteins_100g"));
        dto.setFat(readDouble(nutriments, "fat_100g"));
        dto.setCarbs(readDouble(nutriments, "carbohydrates_100g"));
        dto.setFiber(readDouble(nutriments, "fiber_100g"));
        dto.setSugar(readDouble(nutriments, "sugars_100g"));
        dto.setSodium(readDouble(nutriments, "sodium_100g"));
        dto.setServingSize(readDouble(productNode, "serving_quantity"));
        dto.setIngredientsText(readText(productNode, "ingredients_text"));
        dto.setAllergens(readText(productNode, "allergens"));
        dto.setNutriScore(readText(productNode, "nutrition_grades"));
        return dto;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Double readDouble(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull() || !field.isNumber()) {
            return null;
        }
        return field.asDouble();
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
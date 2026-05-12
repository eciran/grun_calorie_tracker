package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.impl.OpenFoodFactsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenFoodFactsServiceImplTest {

    private MockRestServiceServer server;
    private OpenFoodFactsServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenFoodFactsServiceImpl("https://world.openfoodfacts.org", builder);
    }

    @Test
    void getProductByBarcode_whenProductExists_mapsProductSafely() {
        String response = """
                {
                  "status": 1,
                  "product": {
                    "code": "3017620422003",
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "image_front_url": "https://images.openfoodfacts.org/nutella.jpg",
                    "ingredients_text": "Sugar, palm oil, hazelnuts",
                    "allergens_from_ingredients": "milk, nuts",
                    "nutriscore_grade": "e",
                    "serving_quantity": "100",
                    "nutriments": {
                      "energy-kcal_100g": 539,
                      "proteins_100g": 6.3,
                      "fat_100g": 30.9,
                      "carbohydrates_100g": 57.5,
                      "fiber_100g": 3.4,
                      "sugars_100g": 56.3,
                      "sodium_100g": 0.107
                    }
                  }
                }
                """;

        server.expect(requestTo("https://world.openfoodfacts.org/api/v2/product/3017620422003.json"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Optional<FoodProductDto> result = service.getProductByBarcode(" 3017620422003 ");

        assertTrue(result.isPresent());
        FoodProductDto product = result.get();
        assertEquals("3017620422003", product.getBarcode());
        assertEquals("Nutella", product.getProductName());
        assertEquals("Ferrero", product.getBrand());
        assertEquals("https://images.openfoodfacts.org/nutella.jpg", product.getExternalImageUrl());
        assertEquals(539.0, product.getCalories());
        assertEquals(6.3, product.getProtein());
        assertEquals("e", product.getNutriScore());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, product.getDataSource());
        assertEquals(VerificationStatus.RAW_IMPORTED, product.getVerificationStatus());
        assertEquals(ImageStatus.NEEDS_REVIEW, product.getImageStatus());
        server.verify();
    }

    @Test
    void getProductByBarcode_whenNotFound_returnsEmpty() {
        server.expect(requestTo("https://world.openfoodfacts.org/api/v2/product/000.json"))
                .andRespond(withResourceNotFound());

        Optional<FoodProductDto> result = service.getProductByBarcode("000");

        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    void searchProductsByCriteria_filtersByBrandAndNutriScore() {
        String response = """
                {
                  "products": [
                    {
                      "code": "111",
                      "product_name": "Greek Yogurt",
                      "brands": "Good Dairy",
                      "nutriscore_grade": "a",
                      "nutriments": {"energy-kcal_100g": 90}
                    },
                    {
                      "code": "222",
                      "product_name": "Chocolate Yogurt",
                      "brands": "Other Brand",
                      "nutriscore_grade": "d",
                      "nutriments": {"energy-kcal_100g": 180}
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://world.openfoodfacts.org/cgi/search.pl?search_terms=yogurt&search_simple=1&action=process&json=1&page_size=20"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("yogurt");
        criteria.setBrand("Good");
        criteria.setNutriScore("A");

        List<FoodProductDto> result = service.searchProductsByCriteria(criteria);

        assertEquals(1, result.size());
        assertEquals("111", result.get(0).getBarcode());
        assertEquals("Greek Yogurt", result.get(0).getProductName());
        assertEquals(90.0, result.get(0).getCalories());
        server.verify();
    }
}

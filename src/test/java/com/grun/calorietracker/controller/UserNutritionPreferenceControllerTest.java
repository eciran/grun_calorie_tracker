package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.service.UserNutritionPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserNutritionPreferenceControllerTest {

    private final UserNutritionPreferenceService service =
            mock(UserNutritionPreferenceService.class);
    private final UserNutritionPreferenceController controller =
            new UserNutritionPreferenceController(service);
    private final UserDetails user = mock(UserDetails.class);

    @Test
    void get_usesAuthenticatedUserIdentity() {
        UserNutritionPreferenceDto expected = new UserNutritionPreferenceDto();
        expected.setAllergens(Set.of(RecipeAllergen.MILK));
        when(user.getUsername()).thenReturn("user@example.com");
        when(service.get("user@example.com")).thenReturn(expected);

        var response = controller.get(user);

        assertEquals(expected, response.getBody());
        verify(service).get("user@example.com");
    }

    @Test
    void update_replacesOnlyAuthenticatedUsersOwnPreferences() {
        UserNutritionPreferenceDto request = new UserNutritionPreferenceDto();
        request.setAllergens(Set.of(RecipeAllergen.PEANUTS));
        when(user.getUsername()).thenReturn("user@example.com");
        when(service.update("user@example.com", request)).thenReturn(request);

        var response = controller.update(user, request);

        assertEquals(request, response.getBody());
        verify(service).update("user@example.com", request);
    }
}

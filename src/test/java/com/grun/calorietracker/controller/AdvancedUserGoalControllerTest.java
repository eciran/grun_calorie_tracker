package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.service.AdvancedUserGoalService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class AdvancedUserGoalControllerTest {
    @Test
    void saveForwardsAuthenticatedUserAndIdempotencyKey() {
        AdvancedUserGoalService service = mock(AdvancedUserGoalService.class);
        AdvancedUserGoalController controller = new AdvancedUserGoalController(service);
        AdvancedGoalRequestDto request = new AdvancedGoalRequestDto();
        User principal = new User("advanced@grun.app", "n/a", java.util.List.of());
        UserGoalDto expected = new UserGoalDto();
        when(service.save(request, principal.getUsername(), "advanced-goal:test-1234")).thenReturn(expected);

        var response = controller.save(request, "advanced-goal:test-1234", principal);

        assertSame(expected, response.getBody());
        verify(service).save(request, "advanced@grun.app", "advanced-goal:test-1234");
    }
}

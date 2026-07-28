package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.FastingProgramStatus;
import com.grun.calorietracker.service.AdvancedFastingProgramService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdvancedFastingProgramControllerTest {
    private final AdvancedFastingProgramService service = mock(AdvancedFastingProgramService.class);
    private final AdvancedFastingProgramController controller = new AdvancedFastingProgramController(service);
    private final UserDetails user = mock(UserDetails.class);

    @Test
    void createReturnsCreatedForFirstExecution() {
        FastingProgramDto program = program();
        when(user.getUsername()).thenReturn("user@grun.app");
        when(service.create(eq("user@grun.app"), eq("fasting-key-01"), any()))
                .thenReturn(new FastingProgramCreateResult(program, false));

        var response = controller.create(user, "fasting-key-01", new FastingProgramRequestDto());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(program, response.getBody());
    }

    @Test
    void createReturnsOkForReplay() {
        FastingProgramDto program = program();
        when(user.getUsername()).thenReturn("user@grun.app");
        when(service.create(eq("user@grun.app"), eq("fasting-key-01"), any()))
                .thenReturn(new FastingProgramCreateResult(program, true));

        var response = controller.create(user, "fasting-key-01", new FastingProgramRequestDto());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private FastingProgramDto program() {
        return new FastingProgramDto(
                1L, "Plan", FastingProgramStatus.DRAFT, null, null,
                1, 0L, "FASTING_SAFETY_V1", null, null, List.of());
    }
}
package com.grun.calorietracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPageControllerTest {
    @Test void everyRegisteredPageSupportsDirectNavigationAndRefresh() throws Exception {
        var mapper = new ObjectMapper();
        var mvc = MockMvcBuilders.standaloneSetup(new AdminPageController(mapper)).build();
        Map<String, String> routes;
        try (var input = new ClassPathResource("static/admin-ui/routes.json").getInputStream()) {
            routes = mapper.readValue(input, new TypeReference<>() {});
        }
        assertEquals(59, routes.size());
        for (String route : routes.values()) {
            for (String suffix : new String[]{"", "/"}) {
                mvc.perform(get(route + suffix)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin-ui/assets/")));
            }
        }
        mvc.perform(head("/admin/products")).andExpect(status().isOk()).andExpect(content().string(""));
        for (String path : new String[]{"/admin/unknown", "/admin/assets/missing.js", "/api/v1/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/admin/products")).andExpect(status().isMethodNotAllowed());
    }
}

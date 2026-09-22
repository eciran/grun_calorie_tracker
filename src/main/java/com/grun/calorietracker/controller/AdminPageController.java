package com.grun.calorietracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Public HTML shell only. Admin data remains behind the existing /api/v1/admin permissions. */
@RestController
public class AdminPageController {
    private final Set<String> routes;
    private final Resource index = new ClassPathResource("static/admin-ui/index.html");

    public AdminPageController(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("static/admin-ui/routes.json").getInputStream()) {
            Map<String, String> registry = mapper.readValue(stream, new TypeReference<>() {});
            routes = Set.copyOf(registry.values());
        }
    }

    @GetMapping({"/admin", "/admin/**"})
    public ResponseEntity<Resource> page(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        // Unknown routes/assets are real 404s; never substitute HTML for an API or asset failure.
        if (!routes.contains(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML)
                .body("HEAD".equals(request.getMethod()) ? null : index);
    }
}

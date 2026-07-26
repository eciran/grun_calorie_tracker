package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.RuntimeClientConfigDto;
import com.grun.calorietracker.service.RuntimeOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/runtime/config")
public class RuntimeClientConfigController {
    private final RuntimeOperationsService runtimeOperationsService;

    @GetMapping
    public ResponseEntity<RuntimeClientConfigDto> getConfig(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(runtimeOperationsService.getClientConfig(userDetails.getUsername()));
    }
}

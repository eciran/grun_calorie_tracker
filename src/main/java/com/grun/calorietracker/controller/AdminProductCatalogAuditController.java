package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProductCatalogAuditStatusDto;
import com.grun.calorietracker.entity.ProductCatalogAuditItemEntity;
import com.grun.calorietracker.service.impl.ProductCatalogAuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products/quality-audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Product Catalog Audit")
public class AdminProductCatalogAuditController {
    private final ProductCatalogAuditService service;
    @GetMapping
    public ProductCatalogAuditStatusDto status() { return service.status(); }
    @PostMapping("/start")
    public ProductCatalogAuditStatusDto start(@AuthenticationPrincipal UserDetails principal) { return service.start(principal.getUsername()); }
    @PostMapping("/step")
    public ProductCatalogAuditStatusDto step() { return service.step(); }
    @PostMapping("/cancel")
    public ProductCatalogAuditStatusDto cancel() { return service.cancel(); }
    @GetMapping("/items")
    public Page<ProductCatalogAuditItemEntity> items(@RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.items(outcome, page, size);
    }
}

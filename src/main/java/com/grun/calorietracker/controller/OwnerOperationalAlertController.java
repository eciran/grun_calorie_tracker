package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.OwnerOperationalAlertDto;
import com.grun.calorietracker.dto.OwnerOperationalAlertPageDto;
import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.dto.OwnerAlertActionRequestDto;
import com.grun.calorietracker.service.OwnerOperationalAlertManagementService;
import com.grun.calorietracker.service.OwnerDailySummaryService;
import com.grun.calorietracker.dto.OwnerDailySummaryDto;
import com.grun.calorietracker.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/owner-alerts")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerOperationalAlertController {
    private static final Set<String> STATUSES = Set.of("PENDING", "RETRY", "SENT", "FAILED", "ACKNOWLEDGED");
    private final OwnerOperationalAlertRepository repository;
    private final OwnerOperationalAlertManagementService managementService;
    private final OwnerDailySummaryService dailySummaryService;

    @GetMapping
    public ResponseEntity<OwnerOperationalAlertPageDto> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (tooLong(status, 20) || tooLong(category, 60)) return ResponseEntity.badRequest().build();
        String safeStatus = normalize(status, 20); String safeCategory = normalize(category, 60);
        if (page < 0 || page > 10000 || size < 1 || size > 100 || safeStatus != null && !STATUSES.contains(safeStatus))
            return ResponseEntity.badRequest().build();
        Specification<OwnerOperationalAlertEntity> spec = (root, query, cb) -> cb.conjunction();
        if (safeStatus != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), safeStatus));
        if (safeCategory != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), safeCategory));
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("lastOccurredAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(OwnerOperationalAlertPageDto.from(repository.findAll(spec, pageable).map(OwnerOperationalAlertDto::from)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerOperationalAlertDto> detail(@PathVariable Long id) {
        if (id == null || id <= 0) return ResponseEntity.badRequest().build();
        return repository.findById(id).map(OwnerOperationalAlertDto::from)
                .map(value -> ResponseEntity.ok().header("Cache-Control", "no-store").body(value))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<OwnerDailySummaryDto> dailySummary(@RequestParam(required = false) LocalDate date) {
        LocalDate selected = date == null ? dailySummaryService.today() : date;
        if (selected.isAfter(dailySummaryService.today()) || selected.isBefore(dailySummaryService.today().minusDays(90)))
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dailySummaryService.get(selected));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<OwnerOperationalAlertDto> retry(@PathVariable long id,
            @RequestHeader("X-Admin-Reauth-Token") String token, @RequestBody @Valid OwnerAlertActionRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(managementService.retry(id, user.getUsername(), token, request.reason(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<OwnerOperationalAlertDto> acknowledge(@PathVariable long id,
            @RequestHeader("X-Admin-Reauth-Token") String token, @RequestBody @Valid OwnerAlertActionRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(managementService.acknowledge(id, user.getUsername(), token, request.reason(), correlationId(servletRequest)));
    }

    private String normalize(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.length() <= max ? normalized : null;
    }

    private boolean tooLong(String value, int max) { return value != null && value.trim().length() > max; }
    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

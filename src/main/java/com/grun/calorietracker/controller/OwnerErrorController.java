package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.OwnerErrorGroupLifecycleService;
import com.grun.calorietracker.dto.OwnerErrorGroupStateRequestDto;
import com.grun.calorietracker.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/admin/errors")
@PreAuthorize("hasRole('OWNER')")
public class OwnerErrorController {
    private final OwnerErrorStore store;
    private final OwnerErrorRecorder recorder;
    private final OwnerErrorGroupLifecycleService lifecycleService;
    public OwnerErrorController(OwnerErrorStore store, OwnerErrorRecorder recorder, OwnerErrorGroupLifecycleService lifecycleService) { this.store=store; this.recorder=recorder; this.lifecycleService=lifecycleService; }
    @GetMapping public ResponseEntity<?> list(@RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
            @RequestParam(required=false) Integer status, @RequestParam(required=false) Integer statusClass,
            @RequestParam(required=false) String source,
            @RequestParam(required=false) String method, @RequestParam(required=false) String route,
            @RequestParam(required=false) String correlationId, @RequestParam(required=false) String errorCode,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(7, ChronoUnit.DAYS) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start,end)>366 || page<0 || page>10000 || size<1 || size>100
                || status != null && (status<400 || status>599) || statusClass != null && statusClass!=4 && statusClass!=5
                || source != null && !java.util.Set.of("BACKEND","PROXY","ADMIN_WEB","MOBILE").contains(source)
                || tooLong(method,12) || tooLong(route,300) || tooLong(correlationId,128) || tooLong(errorCode,80)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(store.find(new OwnerErrorStore.Query(start,end,status,statusClass,source,method,route,correlationId,errorCode,page,size)));
    }
    @GetMapping("/health") public ResponseEntity<OwnerErrorRecorder.Health> health() {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(recorder.health());
    }
    @GetMapping("/groups") public ResponseEntity<?> groups(@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to, @RequestParam(required=false) String source,
            @RequestParam(defaultValue="10") int limit) {
        Instant end=to==null?Instant.now():to; Instant start=from==null?end.minus(7,ChronoUnit.DAYS):from;
        if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>366||limit<1||limit>50
                || source!=null&&!java.util.Set.of("BACKEND","PROXY","ADMIN_WEB","MOBILE").contains(source))
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(store.groups(start,end,source,limit));
    }
    @GetMapping("/{id}") public ResponseEntity<OwnerErrorEvent> detail(@PathVariable long id) {
        return store.detail(id).map(value -> ResponseEntity.ok().header("Cache-Control","no-store").body(value)).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/groups/{fingerprint}/state") public ResponseEntity<Void> updateGroupState(@PathVariable String fingerprint,
            @RequestHeader("X-Admin-Reauth-Token") String token,@RequestBody @Valid OwnerErrorGroupStateRequestDto request,
            @AuthenticationPrincipal UserDetails user,HttpServletRequest servletRequest){
        lifecycleService.update(fingerprint,request,user.getUsername(),token,correlationId(servletRequest)); return ResponseEntity.noContent().build();
    }
    private boolean tooLong(String value, int max) { return value != null && value.length()>max; }
    private String correlationId(HttpServletRequest request){Object value=request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);return value==null?request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER):value.toString();}
}

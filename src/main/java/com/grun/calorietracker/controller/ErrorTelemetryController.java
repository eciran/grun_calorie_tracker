package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/error-telemetry")
public class ErrorTelemetryController {
    private final OwnerErrorRecorder recorder; private final byte[] proxyKey;
    private final Map<String, Window> clientWindows = new ConcurrentHashMap<>();
    private record Window(long minute, int count) {}
    public ErrorTelemetryController(OwnerErrorRecorder recorder, @Value("${grun.error-center.proxy-ingest-key:}") String proxyKey) {
        this.recorder=recorder; this.proxyKey=proxyKey==null?new byte[0]:proxyKey.getBytes(StandardCharsets.UTF_8);
    }
    @PostMapping("/client") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> client(@RequestBody @Valid ClientErrorTelemetryRequestDto request, @AuthenticationPrincipal UserDetails user) {
        if (!allow(user.getUsername())) return ResponseEntity.status(429).build();
        if (!validTime(request.occurredAt()) || !validRoute(request.route()) || !validCorrelation(request.correlationId())) return ResponseEntity.badRequest().build();
        recorder.record(new OwnerErrorEvent(null,UUID.randomUUID(),request.occurredAt(),null,request.method(),request.route(),blank(request.correlationId()),
                "CLIENT_"+request.failureKind(),null,null,request.durationMs(),request.source(),safe(request.clientPlatform(),24),safe(request.appVersion(),40)));
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/proxy")
    public ResponseEntity<Void> proxy(@RequestHeader(value="X-Proxy-Telemetry-Key",required=false) String supplied,
            @RequestBody @Valid ProxyErrorTelemetryRequestDto request) {
        byte[] candidate=supplied==null?new byte[0]:supplied.getBytes(StandardCharsets.UTF_8);
        if(proxyKey.length<24||!MessageDigest.isEqual(proxyKey,candidate))return ResponseEntity.status(403).build();
        if(!validTime(request.occurredAt())||!validRoute(request.route())||!validCorrelation(request.correlationId()))return ResponseEntity.badRequest().build();
        recorder.record(new OwnerErrorEvent(null,request.eventKey(),request.occurredAt(),request.status(),request.method(),request.route(),blank(request.correlationId()),
                "PROXY_UPSTREAM_FAILURE",null,null,request.durationMs(),"PROXY",null,null));
        return ResponseEntity.accepted().build();
    }
    private boolean allow(String principal){long minute=System.currentTimeMillis()/60000;Window value=clientWindows.compute(principal,(key,old)->old==null||old.minute()!=minute?new Window(minute,1):new Window(minute,old.count()+1));if(clientWindows.size()>10000)clientWindows.entrySet().removeIf(entry->entry.getValue().minute()<minute-1);return value.count()<=30;}
    private boolean validTime(Instant value){Instant now=Instant.now();return value!=null&&!value.isBefore(now.minus(Duration.ofDays(2)))&&!value.isAfter(now.plus(Duration.ofMinutes(5)));}
    private boolean validRoute(String value){return value!=null&&value.startsWith("/api/")&&!value.contains("?")&&value.matches("/[A-Za-z0-9_{}./\\-\\[\\]]{1,299}");}
    private boolean validCorrelation(String value){return value==null||value.isBlank()||value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");}
    private String blank(String value){return value==null||value.isBlank()?null:value;}
    private String safe(String value,int max){if(value==null||value.isBlank())return null;String clean=value.replaceAll("[^A-Za-z0-9_.\\-]","_");return clean.substring(0,Math.min(max,clean.length()));}
}

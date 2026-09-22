package com.grun.calorietracker.service.support;

import java.time.Instant;

/** Deliberately excludes request bodies, query values, headers and exception messages. */
public record OwnerErrorEvent(Long id, java.util.UUID eventKey, Instant occurredAt, Integer status, String method, String route,
                              String correlationId, String errorCode, String exceptionType,
                              String technicalLocation, long durationMs, String source, String clientPlatform, String appVersion) { }

package com.grun.calorietracker.security;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.ApiErrorCode;
import jakarta.servlet.http.*;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Observes typed metadata without reading/copying response payloads or changing exception handling. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OwnerErrorMetadata implements ResponseBodyAdvice<Object>, HandlerExceptionResolver {
    public static final String CODE = OwnerErrorMetadata.class.getName()+".code";
    public static final String TYPE = OwnerErrorMetadata.class.getName()+".type";
    public static final String LOCATION = OwnerErrorMetadata.class.getName()+".location";
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiErrorResponseDto error && request instanceof ServletServerHttpRequest servlet) {
            try { servlet.getServletRequest().setAttribute(CODE, ApiErrorCode.valueOf(error.getCode()).name()); }
            catch (IllegalArgumentException | NullPointerException ignored) { /* Unrecognized codes are not persisted. */ }
        }
        return body;
    }
    public static void exception(HttpServletRequest request, Throwable exception) {
        if (request.getAttribute(TYPE) != null) return;
        for (int i=0; i<8 && exception.getCause()!=null && exception.getCause()!=exception; i++) exception=exception.getCause();
        String name = exception.getClass().getName();
        request.setAttribute(TYPE, name.substring(0, Math.min(name.length(),120)));
        String frames = Arrays.stream(exception.getStackTrace()).filter(frame -> frame.getClassName().startsWith("com.grun.calorietracker."))
                .limit(10).map(frame -> frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber()).collect(Collectors.joining("\n"));
        request.setAttribute(LOCATION, frames.substring(0,Math.min(frames.length(),2000)));
    }
    @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        exception(request,exception); return null;
    }
}

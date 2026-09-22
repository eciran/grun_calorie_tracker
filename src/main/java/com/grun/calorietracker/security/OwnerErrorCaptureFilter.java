package com.grun.calorietracker.security;

import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class OwnerErrorCaptureFilter implements Filter {
    private static final String STATE = OwnerErrorCaptureFilter.class.getName();
    private final OwnerErrorRecorder recorder;
    private final Supplier<List<PathPattern>> patterns;
    public OwnerErrorCaptureFilter(OwnerErrorRecorder recorder, Supplier<List<PathPattern>> patterns) { this.recorder=recorder; this.patterns=patterns; }
    private static class State {
        final UUID key = UUID.randomUUID();
        final Instant at = Instant.now();
        final long start = System.nanoTime();
        final AtomicBoolean finished = new AtomicBoolean();
        final String path, method;
        volatile boolean awaitingError;
        State(HttpServletRequest request) { path=request.getRequestURI().substring(request.getContextPath().length()); method=request.getMethod(); }
    }
    @Override public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)req; HttpServletResponse response = (HttpServletResponse)res;
        State existing = (State)request.getAttribute(STATE);
        if (existing == null && !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")) { chain.doFilter(req,res); return; }
        State state = existing == null ? new State(request) : existing;
        request.setAttribute(STATE,state);
        HttpServletResponseWrapper wrapped = new HttpServletResponseWrapper(response) {
            @Override public void sendError(int status) throws IOException { state.awaitingError=true; super.sendError(status); }
            @Override public void sendError(int status, String message) throws IOException { state.awaitingError=true; super.sendError(status,message); }
        };
        boolean failed = false;
        try { chain.doFilter(req,wrapped); }
        catch (IOException | ServletException | RuntimeException failure) { failed=true; OwnerErrorMetadata.exception(request,failure); throw failure; }
        finally {
            if (request.isAsyncStarted()) {
                AsyncListener listener = new AsyncListener() {
                    public void onComplete(AsyncEvent event) { finish(state,request,response); }
                    public void onTimeout(AsyncEvent event) { }
                    public void onError(AsyncEvent event) { if(event.getThrowable()!=null) OwnerErrorMetadata.exception(request,event.getThrowable()); }
                    public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
                };
                try { request.getAsyncContext().addListener(listener); }
                catch (IllegalStateException completed) { finish(state,request,response); }
            } else if (!failed && (!state.awaitingError || request.getDispatcherType()==DispatcherType.ERROR)) finish(state,request,response);
            // Unhandled failures are recorded after the container's ERROR dispatch sets the final status.
        }
    }
    private void finish(State state, HttpServletRequest request, HttpServletResponse response) {
        if (!state.finished.compareAndSet(false,true) || response.getStatus()<400 || response.getStatus()>599) return;
        try {
            PathContainer path = PathContainer.parsePath(state.path);
            String route = patterns.get().stream().filter(pattern -> pattern.matches(path))
                .sorted(PathPattern.SPECIFICITY_COMPARATOR).map(PathPattern::getPatternString).filter(value -> value.startsWith("/api/") && value.length()<=300).findFirst().orElse("/api/[unmapped]");
            String cid = Objects.toString(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE), "");
            if (!cid.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) cid=null;
            String method = Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE").contains(state.method) ? state.method : "OTHER";
            recorder.record(new OwnerErrorEvent(null,state.key,state.at,response.getStatus(),method,route,cid,
                (String)request.getAttribute(OwnerErrorMetadata.CODE),(String)request.getAttribute(OwnerErrorMetadata.TYPE),
                (String)request.getAttribute(OwnerErrorMetadata.LOCATION),Math.max(0,(System.nanoTime()-state.start)/1_000_000),
                "BACKEND",null,null));
        } catch (RuntimeException captureFailure) { recorder.captureFailed(); }
    }
}

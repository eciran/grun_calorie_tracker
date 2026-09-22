package com.grun.calorietracker.security;

import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import org.springframework.web.util.pattern.PathPatternParser;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerErrorCaptureFilterTest {
    private final OwnerErrorRecorder recorder = mock(OwnerErrorRecorder.class);
    private final OwnerErrorCaptureFilter filter = new OwnerErrorCaptureFilter(recorder,
        () -> List.of(new PathPatternParser().parse("/api/v1/users/{id}")));
    @Test void capturesFinalStatusesWithoutBodiesSecretsOrPathValues() throws Exception {
        for (int status : new int[]{400,401,403,422,429,500,502,503}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/users/private@example.com");
            request.setQueryString("token=secret"); request.addHeader("Authorization","Bearer secret");
            request.setContent("{\"password\":\"secret\"}".getBytes());
            request.setAttribute("correlationId", "sensitive@example.com");
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res) -> { ((HttpServletResponse)res).setStatus(status); res.getWriter().write("original secret response"); });
            assertEquals("original secret response",response.getContentAsString());
        }
        var captured = ArgumentCaptor.forClass(OwnerErrorEvent.class);
        verify(recorder,times(8)).record(captured.capture());
        for (var event : captured.getAllValues()) {
            assertEquals("/api/v1/users/{id}",event.route()); assertNull(event.correlationId());
            assertFalse(event.toString().contains("secret")); assertFalse(event.toString().contains("private@"));
        }
    }
    @Test void successAndNonApiRequestsAreIgnoredAndUnknownRoutesAreMasked() throws Exception {
        for (int status : new int[]{200,201,204,302}) filter.doFilter(new MockHttpServletRequest("GET","/api/v1/users/123"),new MockHttpServletResponse(),(req,res)->((HttpServletResponse)res).setStatus(status));
        filter.doFilter(new MockHttpServletRequest("GET","/admin/system/errors"),new MockHttpServletResponse(),(req,res)->((HttpServletResponse)res).setStatus(500));
        verifyNoInteractions(recorder);
        filter.doFilter(new MockHttpServletRequest("GET","/api/unknown/secret"),new MockHttpServletResponse(),(req,res)->((HttpServletResponse)res).setStatus(404));
        verify(recorder).record(argThat(event -> event.route().equals("/api/[unmapped]")));
    }
    @Test void errorDispatchUsesFinalStatusAndDoesNotDuplicate() throws Exception {
        var request = new MockHttpServletRequest("GET","/api/v1/users/42"); var response = new MockHttpServletResponse();
        assertThrows(ServletException.class,()->filter.doFilter(request,response,(req,res)->{throw new ServletException("secret token");}));
        verifyNoInteractions(recorder);
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request,response,(req,res)->((HttpServletResponse)res).setStatus(503));
        filter.doFilter(request,response,(req,res)->{});
        verify(recorder,times(1)).record(argThat(event -> event.status()==503 && !event.toString().contains("secret")));
    }
    @Test void sendErrorWaitsForErrorDispatch() throws Exception {
        var request = new MockHttpServletRequest("GET","/api/v1/users/42"); var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->((HttpServletResponse)res).sendError(400,"secret"));
        verifyNoInteractions(recorder);
        response.setCommitted(false); response.reset(); request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request,response,(req,res)->((HttpServletResponse)res).setStatus(500));
        verify(recorder).record(argThat(event -> event.status()==500));
    }
    @Test void asyncRecordsAtCompletionAndCaptureFailureCannotChangeResponse() throws Exception {
        var request = new MockHttpServletRequest("GET","/api/v1/users/42"); request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->req.startAsync(req,res));
        verifyNoInteractions(recorder);
        response.setStatus(429);
        var context = (MockAsyncContext)request.getAsyncContext();
        for(var listener:context.getListeners()) listener.onComplete(new AsyncEvent(context,request,response));
        verify(recorder).record(argThat(event -> event.status()==429));
        var broken = new OwnerErrorCaptureFilter(recorder,()->{throw new IllegalStateException();});
        var second = new MockHttpServletResponse();
        broken.doFilter(new MockHttpServletRequest("GET","/api/test"),second,(req,res)->((HttpServletResponse)res).setStatus(422));
        assertEquals(422,second.getStatus()); verify(recorder).captureFailed();
    }
}

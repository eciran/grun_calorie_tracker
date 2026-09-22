package com.grun.calorietracker.security;

import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.pattern.PathPatternParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerErrorContainerTest {
    @TempDir Path temp;
    @Test void realContainerFinalErrorAndAsyncDispatchAreCapturedOnce() throws Exception {
        var recorder=mock(OwnerErrorRecorder.class);
        var tomcat=new Tomcat(); tomcat.setBaseDir(temp.toString()); tomcat.setPort(0); tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temp.toString());
        var wrapper=Tomcat.addServlet(context,"fixture",new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException,ServletException {
                if (request.getRequestURI().equals("/failure")) { response.setStatus(503); response.getWriter().write("safe failure"); return; }
                switch(request.getParameter("mode")) {
                    case "throw" -> throw new ServletException("must never be stored");
                    case "send" -> response.sendError(400,"must never be stored");
                    case "async" -> { var async=request.startAsync(); async.start(()->{ ((HttpServletResponse)async.getResponse()).setStatus(429); async.complete(); }); }
                    default -> response.setStatus(204);
                }
            }
        });
        wrapper.setAsyncSupported(true); context.addServletMappingDecoded("/*","fixture");
        var errorPage=new ErrorPage(); errorPage.setErrorCode(0); errorPage.setLocation("/failure"); context.addErrorPage(errorPage);
        var definition=new FilterDef(); definition.setFilterName("errors"); definition.setAsyncSupported("true");
        definition.setFilter(new OwnerErrorCaptureFilter(recorder,()->List.of(new PathPatternParser().parse("/api/test/{id}")))); context.addFilterDef(definition);
        var mapping=new FilterMap(); mapping.setFilterName("errors"); mapping.addURLPattern("/*");
        for(String dispatcher:List.of("REQUEST","ERROR","ASYNC")) mapping.setDispatcher(dispatcher); context.addFilterMap(mapping);
        try {
            tomcat.start(); var client=HttpClient.newHttpClient();
            String base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/api/test/secret?mode=";
            for(String mode:List.of("throw","send","async","success")) {
                var response=client.send(HttpRequest.newBuilder(URI.create(base+mode)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(mode.equals("async")?429:mode.equals("success")?204:503,response.statusCode());
            }
            var events=ArgumentCaptor.forClass(OwnerErrorEvent.class);
            verify(recorder,timeout(2000).times(3)).record(events.capture());
            assertEquals(List.of(503,503,429),events.getAllValues().stream().map(OwnerErrorEvent::status).toList());
            for(var event:events.getAllValues()) { assertEquals("/api/test/{id}",event.route()); assertFalse(event.toString().contains("secret")); assertFalse(event.toString().contains("must never")); }
        } finally { tomcat.stop(); tomcat.destroy(); }
    }
}

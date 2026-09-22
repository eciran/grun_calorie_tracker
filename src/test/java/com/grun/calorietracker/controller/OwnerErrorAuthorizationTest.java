package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.OwnerErrorGroupLifecycleService;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import com.grun.calorietracker.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerErrorAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean OwnerErrorStore store() { return mock(OwnerErrorStore.class); }
        @Bean OwnerErrorRecorder recorder() { return mock(OwnerErrorRecorder.class); }
        @Bean OwnerErrorGroupLifecycleService lifecycleService() { return mock(OwnerErrorGroupLifecycleService.class); }
        @Bean OwnerErrorController controller(OwnerErrorStore store,OwnerErrorRecorder recorder,OwnerErrorGroupLifecycleService lifecycleService) { return new OwnerErrorController(store,recorder,lifecycleService); }
    }
    @Test void allEndpointsRequireOwnerEvenWhenAdminHasTechnicalPermission() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var controller=context.getBean(OwnerErrorController.class);
            for(String role:new String[]{"ADMIN","ADMIN_TECHNICAL","ADMIN_READ_ONLY","USER"}) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture",null,AuthorityUtils.createAuthorityList("ROLE_"+role,"ADMIN_PERMISSION_TECHNICAL_READ")));
                assertThrows(AccessDeniedException.class,controller::health);
                assertThrows(AccessDeniedException.class,()->controller.detail(1));
                assertThrows(AccessDeniedException.class,()->controller.list(null,null,null,null,null,null,null,null,null,0,25));
                assertThrows(AccessDeniedException.class,()->controller.groups(null,null,null,10));
                var response=new MockHttpServletResponse();
                new AdminAuthorizationFilter(new ObjectMapper().findAndRegisterModules()).doFilter(new MockHttpServletRequest("GET","/api/v1/admin/errors"),response,(req,res)->fail("Non-owner must not pass filter"));
                assertEquals(403,response.getStatus());
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture",null,AuthorityUtils.createAuthorityList("ROLE_OWNER","ADMIN_PERMISSION_TECHNICAL_READ")));
            assertEquals(200,controller.health().getStatusCode().value());
            when(context.getBean(OwnerErrorStore.class).detail(1)).thenReturn(Optional.empty());
            assertEquals(404,controller.detail(1).getStatusCode().value());
            assertEquals(400,controller.list(null,null,200,null,null,null,null,null,null,0,25).getStatusCode().value());
            assertEquals(400,controller.list(null,null,null,null,null,null,null,null,null,0,1000).getStatusCode().value());
            assertEquals(400,controller.groups(null,null,"UNKNOWN",10).getStatusCode().value());
            assertEquals(400,controller.groups(null,null,null,51).getStatusCode().value());
            verify(context.getBean(OwnerErrorStore.class),never()).find(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}

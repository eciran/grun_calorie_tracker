package com.grun.calorietracker.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    @Test
    void usesIncomingCorrelationIdWhenPresent() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/search");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "mobile-request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals("mobile-request-1", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals("mobile-request-1", request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNotNull(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals(
                response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER),
                request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        );
    }

    @Test
    void stripsLineBreaksFromIncomingCorrelationId() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/search");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "mobile\r\n-request\t1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals("mobile-request1", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals("mobile-request1", request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE));
    }
}

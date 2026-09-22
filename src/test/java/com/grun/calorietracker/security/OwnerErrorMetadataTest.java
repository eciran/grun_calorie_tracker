package com.grun.calorietracker.security;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import static org.junit.jupiter.api.Assertions.*;

class OwnerErrorMetadataTest {
    @Test void onlyRecognizedCodesAndCodeLocationsAreObservedWithoutChangingResponse() {
        var advice = new OwnerErrorMetadata(); var request = new MockHttpServletRequest();
        var body = new ApiErrorResponseDto(); body.setMessage("password=secret user@private.example"); body.setCode("INVALID_REQUEST");
        assertSame(body, advice.beforeBodyWrite(body,null,MediaType.APPLICATION_JSON,null,new ServletServerHttpRequest(request),null));
        assertEquals("INVALID_REQUEST",request.getAttribute(OwnerErrorMetadata.CODE));
        assertEquals("password=secret user@private.example",body.getMessage());
        var exception = new IllegalStateException("password=secret");
        advice.resolveException(request,null,null,exception);
        assertEquals("java.lang.IllegalStateException",request.getAttribute(OwnerErrorMetadata.TYPE));
        assertTrue(request.getAttribute(OwnerErrorMetadata.LOCATION).toString().contains("OwnerErrorMetadataTest"));
        assertFalse(request.getAttribute(OwnerErrorMetadata.LOCATION).toString().contains("password"));
        var otherRequest = new MockHttpServletRequest(); body.setCode("PRIVATE_TOKEN_VALUE");
        advice.beforeBodyWrite(body,null,MediaType.APPLICATION_JSON,null,new ServletServerHttpRequest(otherRequest),null);
        assertNull(otherRequest.getAttribute(OwnerErrorMetadata.CODE));
    }
}

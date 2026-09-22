package com.grun.calorietracker.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovalDecisionErrorTest {
    @Test void safeExpiryReasonIsVisibleWithoutInternalDetails() {
        var handler = new GlobalExceptionHandler(new StaticMessageSource(), false);
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/approvals/1/reject");
        request.addHeader("X-Correlation-Id", "approval-test");
        var response = handler.handleApprovalDecision(new ApprovalDecisionException.Expired(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("APPROVAL_EXPIRED");
        assertThat(response.getBody().getMessage()).contains("expired");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("approval-test");
    }
}

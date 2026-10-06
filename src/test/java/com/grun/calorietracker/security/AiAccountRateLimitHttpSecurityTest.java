package com.grun.calorietracker.security;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {"grun.rate-limit.enabled=true", "grun.rate-limit.redis.enabled=false",
        "grun.rate-limit.ai-draft.max-requests-per-minute=100",
        "grun.rate-limit.ai-draft.account-max-requests-per-minute=2"})
@AutoConfigureMockMvc
@Transactional
class AiAccountRateLimitHttpSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired UserRepository users;
    @MockBean AiInsightService insights;
    @MockBean PasswordResetMailSender resetMail;
    @MockBean MailDeliveryService mail;
    @MockBean AdminAuditService audit;
    @MockBean UserActivityService activity;

    @Test void changingIpOrEndpointDoesNotResetAccountBudgetOrCallProviderAfterLimit() throws Exception {
        UserEntity first = user();
        UserEntity second = user();
        generate(first, "daily", "192.0.2.11", 200);
        generate(first, "weekly", "192.0.2.12", 200);
        var blocked = post("/api/v1/ai/insights/daily").header("Authorization", "Bearer " + jwt.generateUserToken(first))
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(request -> {
                    request.setRemoteAddr("192.0.2.13"); return request;
                });
        mvc.perform(blocked).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("code").value("RATE_LIMIT_EXCEEDED"));
        verify(insights, times(1)).createDailyInsight(eq(first.getEmail()), anyString(), any());
        verify(insights, times(1)).createWeeklyInsight(eq(first.getEmail()), anyString(), any());
        generate(second, "daily", "192.0.2.13", 200);
        verify(insights, times(1)).createDailyInsight(eq(second.getEmail()), anyString(), any());
    }

    @Test void unauthenticatedAttemptsDoNotReachTheAiService() throws Exception {
        mvc.perform(post("/api/v1/ai/insights/daily").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(insights);
    }

    private void generate(UserEntity user, String type, String ip, int status) throws Exception {
        mvc.perform(post("/api/v1/ai/insights/" + type).header("Authorization", "Bearer " + jwt.generateUserToken(user))
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(request -> {
                    request.setRemoteAddr(ip); return request;
                })).andExpect(status().is(status));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setEmail("ai-limit-" + UUID.randomUUID() + "@example.com");
        user.setName("Synthetic account");
        user.setRole(UserRole.STANDARD);
        user.setEmailVerified(true);
        user.setPassword("synthetic-unused-hash");
        return users.saveAndFlush(user);
    }
}

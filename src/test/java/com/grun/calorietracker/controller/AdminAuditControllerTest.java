package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminActionAuditDto;
import com.grun.calorietracker.dto.AdminActionAuditPageDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void listAudits_whenAdmin_returnsAuditEntries() throws Exception {
        AdminActionAuditDto audit = new AdminActionAuditDto();
        audit.setId(1L);
        audit.setAdminEmail("admin@test.com");
        audit.setActionType(AdminAuditActionType.SUBSCRIPTION_FEATURE_UPDATE);
        audit.setTargetType(AdminAuditTargetType.SUBSCRIPTION_FEATURE);
        audit.setTargetKey("PLUS:HEALTH_INTEGRATION");
        audit.setCorrelationId("request-1");
        audit.setCreatedAt(LocalDateTime.of(2026, 5, 27, 13, 0));

        AdminActionAuditPageDto page = new AdminActionAuditPageDto();
        page.setContent(List.of(audit));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(adminAuditService.list(
                AdminAuditActionType.SUBSCRIPTION_FEATURE_UPDATE,
                AdminAuditTargetType.SUBSCRIPTION_FEATURE,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audits")
                        .param("actionType", "SUBSCRIPTION_FEATURE_UPDATE")
                        .param("targetType", "SUBSCRIPTION_FEATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].adminEmail").value("admin@test.com"))
                .andExpect(jsonPath("$.content[0].actionType").value("SUBSCRIPTION_FEATURE_UPDATE"))
                .andExpect(jsonPath("$.content[0].targetKey").value("PLUS:HEALTH_INTEGRATION"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void listAudits_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audits"))
                .andExpect(status().isForbidden());
    }
}

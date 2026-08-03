package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductContributionDto;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.service.FoodProductContributionService;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodProductContributionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoodProductContributionService contributionService;

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void submit_whenAuthenticated_returnsCreated() throws Exception {
        FoodProductContributionDto response = new FoodProductContributionDto();
        response.setId(11L);
        response.setStatus(FoodProductContributionStatus.PENDING_REVIEW);
        when(contributionService.submit(eq("user@test.com"), any(), any())).thenReturn(response);
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata.json", MediaType.APPLICATION_JSON_VALUE, """
                {
                  "barcode":"8691234567890",
                  "productName":"Test Biscuit",
                  "brand":"Test Brand",
                  "marketRegion":"TR",
                  "calories":420,
                  "protein":7,
                  "fat":14,
                  "carbs":65,
                  "commercialUseAllowed":true,
                  "persistentStorageAllowed":true
                }
                """.getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "label.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

        mockMvc.perform(multipart("/api/v1/products/contributions").file(metadata).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void ownEvidence_whenAuthenticated_streamsPrivateNoStoreContent() throws Exception {
        when(contributionService.loadEvidenceForUser(11L, "user@test.com"))
                .thenReturn(new EvidenceContent(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG_VALUE));

        mockMvc.perform(get("/api/v1/products/contributions/11/evidence"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void adminList_whenStandardUser_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/contributions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_CATALOG_READ"})
    void adminEvidence_whenAdmin_streamsPrivateContent() throws Exception {
        when(contributionService.loadEvidenceForAdmin(11L))
                .thenReturn(new EvidenceContent(new byte[]{4, 5, 6}, MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(get("/api/v1/admin/products/contributions/11/evidence"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(content().bytes(new byte[]{4, 5, 6}));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_CATALOG_MANAGE"})
    void adminReview_whenAdmin_returnsReviewedContribution() throws Exception {
        FoodProductContributionDto response = new FoodProductContributionDto();
        response.setId(11L);
        response.setStatus(FoodProductContributionStatus.APPROVED);
        when(contributionService.review(eq(11L), eq("admin@test.com"), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/products/contributions/11/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reviewNote\":\"Readable label.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_CATALOG_READ"})
    void evidenceLedger_whenAdmin_returnsTsvAttachment() throws Exception {
        when(contributionService.exportApprovedTrEvidenceLedger()).thenReturn("barcode\tevidenceType\n".getBytes());

        mockMvc.perform(get("/api/v1/admin/products/contributions/evidence-ledger.tsv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/tab-separated-values"))
                .andExpect(content().string("barcode\tevidenceType\n"));
    }
}

package com.grun.calorietracker.security;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"grun.rate-limit.enabled=false", "grun.test-feedback.enabled=true",
        "grun.food-contribution-storage.provider=S3", "grun.product-intake.rollout.enabled=true",
        "grun.product-intake.rollout.percentage=100", "grun.product-intake.rollout.markets=GLOBAL",
        "grun.product-intake.rollout.cohort-salt=synthetic-security-test-cohort"})
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.jdbc.Sql(statements = "CREATE TABLE IF NOT EXISTS user_analytics_cache_revisions (user_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_test_analytics_revision_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)")
class ResourceOwnershipHttpSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired FoodLogsRepository logs;
    @Autowired RecipeRepository recipes;
    @Autowired MealPlanRepository plans;
    @Autowired AiRequestHistoryRepository history;
    @Autowired TestFeedbackSubmissionRepository feedback;
    @Autowired FoodProductReviewCaseRepository reviewCases;
    @Autowired FoodProductUploadSessionRepository uploadSessions;
    @Autowired MealPlanItemRepository planItems;
    @Autowired ProgressLogRepository progress;
    @Autowired SubscriptionRepository subscriptions;
    @MockBean com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage storage;
    @MockBean(name = "foodContributionS3Client") software.amazon.awssdk.services.s3.S3Client s3;
    @MockBean(name = "foodContributionS3Presigner") software.amazon.awssdk.services.s3.presigner.S3Presigner presigner;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @MockBean PasswordResetMailSender resetMail;
    @MockBean MailDeliveryService mail;
    @MockBean AdminAuditService audit;
    @MockBean UserActivityService activity;

    @Test void anotherAccountCannotReadOrDeleteDiaryEntry() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        FoodLogsEntity entry = new FoodLogsEntity();
        entry.setUser(owner);
        entry.setDisplayName("Private diary entry");
        entry.setLogDate(LocalDateTime.now());
        entry.setMealType("LUNCH");
        entry.setSnapshotCalories(100.0);
        entry = logs.saveAndFlush(entry);
        String path = "/api/v1/food-logs/" + entry.getId();
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        assertThat(logs.findById(entry.getId())).isPresent();
    }

    @Test void anotherAccountCannotReadOrArchivePrivateRecipe() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        RecipeEntity recipe = new RecipeEntity();
        recipe.setOwnerUser(owner);
        recipe.setName("Private recipe");
        recipe = recipes.saveAndFlush(recipe);
        String path = "/api/v1/recipes/" + recipe.getId();
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recipes/public/" + recipe.getId()).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        assertThat(recipes.findById(recipe.getId()).orElseThrow().getArchived()).isFalse();
    }

    @Test void anotherAccountCannotReadOrDeleteMealPlan() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        MealPlanEntity plan = new MealPlanEntity();
        plan.setUser(owner);
        plan.setName("Private plan");
        plan.setStartDate(LocalDate.now());
        plan.setEndDate(LocalDate.now().plusDays(1));
        plan = plans.saveAndFlush(plan);
        String path = "/api/v1/meal-plans/" + plan.getId();
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        assertThat(plans.findById(plan.getId())).isPresent();
    }

    @Test void profileCannotAssignRoleCredentialsOrAnotherUserId() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        String password = owner.getPassword();
        String email = owner.getEmail();
        mvc.perform(put("/api/v1/users/me").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Safe name","id":%d,"userId":%d,"email":"attacker@example.com",
                 "role":"OWNER","password":"attacker","authTokenVersion":99,
                 "emailVerified":false,"adminMfaEnabled":true}
                """.formatted(other.getId(), other.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value("Safe name"));
        users.flush();
        assertThat(owner.getRole()).isEqualTo(UserRole.STANDARD);
        assertThat(owner.getEmail()).isEqualTo(email);
        assertThat(owner.getPassword()).isEqualTo(password);
        assertThat(owner.getAuthTokenVersion()).isZero();
        assertThat(owner.getEmailVerified()).isTrue();
        assertThat(owner.getAdminMfaEnabled()).isFalse();
        assertThat(other.getName()).isEqualTo("Test owner");
    }

    @Test void anotherAccountCannotReadOrAcknowledgeAiResult() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        AiRequestHistoryEntity request = new AiRequestHistoryEntity();
        request.setUser(owner);
        request.setRequestType(com.grun.calorietracker.enums.AiRequestType.AI_DAILY_INSIGHT);
        request.setProvider(com.grun.calorietracker.enums.AiProvider.LOG);
        request.setModel("test");
        request.setPromptVersion("test");
        request.setStatus(com.grun.calorietracker.enums.AiRequestStatus.DRAFT_CREATED);
        request.setCreatedAt(LocalDateTime.now());
        request.setOutputPayload("{}");
        request = history.saveAndFlush(request);
        String path = "/api/v1/ai/requests/history/" + request.getId();
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().isBadRequest());
        mvc.perform(post(path + "/completion-seen").header("Authorization", bearer(other)))
                .andExpect(status().isBadRequest());
        assertThat(history.findById(request.getId()).orElseThrow().getCompletionNotifiedAt()).isNull();
        mvc.perform(post(path + "/completion-seen").header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test void unauthenticatedResourceAccessIsRejected() throws Exception {
        mvc.perform(get("/api/v1/food-logs/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/meal-plans/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/recipes/1")).andExpect(status().isUnauthorized());
    }

    @Test void feedbackScreenshotAuthorizationAndCompletionRequireTheOwner() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        TestFeedbackSubmissionEntity entry = new TestFeedbackSubmissionEntity();
        entry.setUser(owner);
        entry.setFeedbackType(com.grun.calorietracker.enums.TestFeedbackType.PROBLEM);
        entry.setPlatform(com.grun.calorietracker.enums.TestFeedbackPlatform.IOS);
        entry.setRoute("/profile");
        entry.setIdempotencyKey(UUID.randomUUID().toString());
        entry = feedback.saveAndFlush(entry);
        String path = "/api/v1/test-feedback/" + entry.getId() + "/screenshot";
        String payload = "{\"contentType\":\"image/png\",\"sizeBytes\":100,\"sha256\":\"" + "a".repeat(64) + "\"}";
        mvc.perform(post(path + "/upload-authorization").header("Authorization", bearer(other))
                        .header("X-App-Environment", "preview").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound());
        mvc.perform(post(path + "/complete").header("Authorization", bearer(other)).header("X-App-Environment", "preview"))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(storage);
        org.mockito.Mockito.when(storage.authorizeUpload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage.UploadAuthorization(
                        java.net.URI.create("https://storage.invalid/synthetic-upload"), "PUT", java.util.Map.of(), java.time.Instant.now().plusSeconds(60)));
        mvc.perform(post(path + "/upload-authorization").header("Authorization", bearer(owner))
                        .header("X-App-Environment", "preview").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        assertThat(entry.getScreenshotStorageKey()).contains("/" + owner.getId() + "/" + entry.getId() + "/");
    }

    @Test void healthExternalIdsReadsAndDeletesAreScopedToAuthenticatedAccount() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        for (UserEntity account : java.util.List.of(owner, other)) {
            SubscriptionEntity subscription = new SubscriptionEntity();
            subscription.setUser(account);
            subscription.setPlanType(com.grun.calorietracker.enums.SubscriptionPlan.PRO);
            subscription.setStatus(com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE);
            subscription.setStartDate(LocalDate.now());
            subscription.setEndDate(LocalDate.now().plusMonths(1));
            subscriptions.saveAndFlush(subscription);
            mvc.perform(put("/api/v1/health/connections/APPLE_HEALTH").header("Authorization", bearer(account)))
                    .andExpect(status().isOk());
        }
        String metric = "{\"externalId\":\"same-provider-id\",\"steps\":%d,\"recordedAt\":\"%sT12:00:00\",\"userId\":%d}";
        mvc.perform(post("/api/v1/health/APPLE_HEALTH/metrics").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(metric.formatted(500, LocalDate.now(), other.getId())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/health/summary").param("date", LocalDate.now().toString())
                .param("userId", owner.getId().toString()).header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("hasHealthData").value(false));
        mvc.perform(post("/api/v1/health/APPLE_HEALTH/metrics").header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content(metric.formatted(700, LocalDate.now(), owner.getId())))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/health/APPLE_HEALTH/data").param("userId", owner.getId().toString())
                .header("Authorization", bearer(other))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/health/summary").param("date", LocalDate.now().toString()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("totalSteps").value(500));
    }

    @Test void exportCannotBeRedirectedToAnotherAccountWithQueryParameters() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        FoodLogsEntity entry = new FoodLogsEntity();
        entry.setUser(owner);
        entry.setDisplayName("SECRET-OTHER-ACCOUNT-DIARY");
        entry.setMealType("LUNCH");
        entry.setLogDate(LocalDateTime.now());
        entry.setSnapshotCalories(100.0);
        logs.saveAndFlush(entry);
        mvc.perform(get("/api/v1/account/gdpr/export").header("Authorization", bearer(other))
                .param("userId", owner.getId().toString()).param("email", owner.getEmail()))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(other.getEmail()))
                .andExpect(jsonPath("foodLogs").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET-OTHER-ACCOUNT-DIARY"))));
        mvc.perform(get("/api/v1/account/gdpr/export").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("foodLogCount").value(1));
    }

    @Test void productEvidenceSessionCannotBeReadOrFinalizedByAnotherAccount() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        FoodProductUploadSessionEntity session = new FoodProductUploadSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setCreatedBy(owner);
        session.setIdempotencyKey(UUID.randomUUID().toString());
        session.setStatus(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.CREATED);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        uploadSessions.saveAndFlush(session);
        String path = "/api/v1/products/review-cases/upload-sessions/" + session.getId();
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().is4xxClientError());
        mvc.perform(post(path + "/finalize").header("Authorization", bearer(other))).andExpect(status().is4xxClientError());
        assertThat(uploadSessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.CREATED);
        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test void productReviewEvidenceAndWithdrawCannotTargetAnotherSubmitter() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        FoodProductReviewCaseEntity review = new FoodProductReviewCaseEntity();
        review.setSubmittedBy(owner);
        review.setSource(com.grun.calorietracker.enums.FoodProductReviewCaseSource.USER_OCR);
        review.setMarketRegion(com.grun.calorietracker.enums.MarketRegion.GLOBAL);
        review.setResolutionMode(com.grun.calorietracker.enums.FoodProductResolutionMode.NEW_CANDIDATE);
        review.setIdempotencyKey(UUID.randomUUID().toString());
        review.setSubmittedValuesJson("{}");
        review.setAiNutritionLabelProcessingAllowed(false);
        review = reviewCases.saveAndFlush(review);
        String path = "/api/v1/products/review-cases/" + review.getId();
        mvc.perform(get("/api/v1/products/review-cases/mine").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":" + review.getId())));
        mvc.perform(get("/api/v1/products/review-cases/mine").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        mvc.perform(post(path + "/evidence").header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content("{\"uploadSessionId\":\"synthetic\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post(path + "/withdraw").header("Authorization", bearer(other))).andExpect(status().is4xxClientError());
        mvc.perform(patch(path + "/withdraw").header("Authorization", bearer(other))).andExpect(status().is4xxClientError());
        assertThat(reviewCases.findById(review.getId()).orElseThrow().getStatus())
                .isEqualTo(com.grun.calorietracker.enums.FoodProductReviewCaseStatus.SUBMITTED);
        review.setStatus(com.grun.calorietracker.enums.FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION);
        reviewCases.saveAndFlush(review);
        FoodProductUploadSessionEntity foreignSession = new FoodProductUploadSessionEntity();
        foreignSession.setId(UUID.randomUUID().toString());
        foreignSession.setCreatedBy(other);
        foreignSession.setIdempotencyKey(UUID.randomUUID().toString());
        foreignSession.setStatus(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.FINALIZED);
        foreignSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        foreignSession.setCreatedAt(LocalDateTime.now());
        foreignSession.setUpdatedAt(LocalDateTime.now());
        uploadSessions.saveAndFlush(foreignSession);
        mvc.perform(post(path + "/evidence").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadSessionId\":\"" + foreignSession.getId() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(reviewCases.findById(review.getId()).orElseThrow().getStatus())
                .isEqualTo(com.grun.calorietracker.enums.FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION);
        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test void nestedPlanItemCannotBeAccessedThroughAnotherOwnerOrParentPlan() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        MealPlanEntity first = activePlan(owner);
        MealPlanEntity second = activePlan(owner);
        MealPlanItemEntity item = new MealPlanItemEntity();
        item.setMealPlan(first);
        item.setPlanDate(LocalDate.now());
        item.setMealType("LUNCH");
        item.setItemType(com.grun.calorietracker.enums.MealPlanItemType.AI_SNAPSHOT);
        item.setPortionSize(100.0);
        item.setPortionUnit(com.grun.calorietracker.enums.FoodPortionUnit.GRAM);
        item.setSnapshotName("Synthetic item");
        item = planItems.saveAndFlush(item);
        String suffix = "/items/" + item.getId() + "/skip";
        mvc.perform(post("/api/v1/meal-plans/" + first.getId() + suffix).header("Authorization", bearer(other))
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/meal-plans/" + second.getId() + suffix).header("Authorization", bearer(owner))
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/meal-plans/" + first.getId() + suffix).header("Authorization", bearer(owner))
                .header("Idempotency-Key", key)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/meal-plans/" + second.getId() + suffix).header("Authorization", bearer(owner))
                .header("Idempotency-Key", key)).andExpect(status().isNotFound());
    }

    @Test void progressCreationCannotOverwriteAnotherAccountsEntryOrForgeBaseline() throws Exception {
        UserEntity owner = user();
        UserEntity other = user();
        ProgressLogEntity original = new ProgressLogEntity();
        original.setUser(owner);
        original.setWeight(88.0);
        original.setLogDate(LocalDateTime.now().minusDays(2));
        original.setOnboardingBaseline(true);
        original = progress.saveAndFlush(original);
        long originalId = original.getId();
        mvc.perform(post("/api/v1/progress").header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"id":%d,"userId":%d,"user":{"id":%d},"weight":72,
                 "logDate":"1990-01-01T00:00:00","onboardingBaseline":true}
                """.formatted(originalId, owner.getId(), owner.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(org.hamcrest.Matchers.not((int) originalId)))
                .andExpect(jsonPath("onboardingBaseline").value(false));
        ProgressLogEntity unchanged = progress.findById(originalId).orElseThrow();
        assertThat(unchanged.getUser().getId()).isEqualTo(owner.getId());
        assertThat(unchanged.getWeight()).isEqualTo(88.0);
        assertThat(unchanged.isOnboardingBaseline()).isTrue();
        var created = progress.findByUserOrderByLogDateAsc(other);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getLogDate()).isAfter(LocalDateTime.now().minusMinutes(1));
        String path = "/api/v1/progress/" + originalId;
        mvc.perform(get(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(put(path).header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\":72}")).andExpect(status().isNotFound());
    }

    private MealPlanEntity activePlan(UserEntity user) {
        MealPlanEntity plan = new MealPlanEntity();
        plan.setUser(user);
        plan.setName("Synthetic active plan");
        plan.setStartDate(LocalDate.now());
        plan.setEndDate(LocalDate.now());
        plan.setStatus(com.grun.calorietracker.enums.MealPlanStatus.ACTIVE);
        return plans.saveAndFlush(plan);
    }

    @Test void recordsActualRegisteredEndpointsForAuthorizationReview() throws Exception {
        var inventory = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.grun.calorietracker.controller"))
                .map(entry -> {
                    var handler = entry.getValue();
                    var methodPolicy = handler.getMethodAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
                    var classPolicy = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                            handler.getBeanType(), org.springframework.security.access.prepost.PreAuthorize.class);
                    return java.util.Map.of(
                            "paths", entry.getKey().getPatternValues().stream().sorted().toList(),
                            "methods", entry.getKey().getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList(),
                            "handler", handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName(),
                            "preAuthorize", methodPolicy != null ? methodPolicy.value() : classPolicy != null ? classPolicy.value() : "",
                            "requestInputTypes", java.util.Arrays.stream(handler.getMethodParameters())
                                    .filter(parameter -> parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)
                                            || parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestPart.class)
                                            || parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.ModelAttribute.class))
                                    .map(parameter -> parameter.getParameterType().getName()).toList(),
                            "usesAuthenticatedPrincipal", java.util.Arrays.stream(handler.getMethodParameters())
                                    .anyMatch(parameter -> parameter.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class)),
                            "reviewStatus", "PENDING_EFFECTIVE_AUTHORIZATION_REVIEW");
                }).sorted(java.util.Comparator.comparing(row -> row.get("handler").toString())).toList();
        assertThat(inventory).isNotEmpty();
        java.nio.file.Path output = java.nio.file.Path.of("outputs", "security-review", "registered-endpoint-inventory.json");
        java.nio.file.Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), java.util.Map.of(
                "notice", "Mapping/annotation inventory only. Security filters, service ownership, subscription gates and limits require separate review.",
                "endpoints", inventory));
    }

    private String bearer(UserEntity user) { return "Bearer " + jwt.generateUserToken(user); }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setEmail("ownership-" + UUID.randomUUID() + "@example.com");
        user.setName("Test owner");
        user.setRole(UserRole.STANDARD);
        user.setPassword(passwords.encode("StrongTestPass1!"));
        return users.saveAndFlush(user);
    }
}

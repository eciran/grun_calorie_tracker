package com.grun.calorietracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminProductIntakeControllerPermissionTest {

    @Test
    void readOperationsAllowCatalogOwnerAndReadOnlyAdmins() throws Exception {
        assertRoles("list", Set.of("OWNER", "ADMIN_CATALOG", "ADMIN_READ_ONLY"));
        assertRoles("detail", Set.of("OWNER", "ADMIN_CATALOG", "ADMIN_READ_ONLY"));
    }

    @Test
    void assignmentOperationsKeepOwnerAndCatalogBoundaries() throws Exception {
        assertRoles("claim", Set.of("ADMIN_CATALOG"));
        assertRoles("release", Set.of("OWNER", "ADMIN_CATALOG"));
        assertRoles("reassign", Set.of("OWNER"));
    }

    @Test
    void reviewMutationsExcludeReadOnlyAdmins() throws Exception {
        assertRoles("requestBetterEvidence", Set.of("OWNER", "ADMIN_CATALOG"));
        assertRoles("approveEvidence", Set.of("OWNER", "ADMIN_CATALOG"));
        assertRoles("rejectEvidence", Set.of("OWNER", "ADMIN_CATALOG"));
        assertRoles("attachExistingProduct", Set.of("OWNER", "ADMIN_CATALOG"));
        assertRoles("createManual", Set.of("OWNER", "ADMIN_CATALOG"));
    }

    @Test
    void privateEvidenceReadExcludesReadOnlyAdmins() throws Exception {
        assertRoles(AdminFoodProductEvidenceController.class, "authorizeRead",
                Set.of("OWNER", "ADMIN_CATALOG"));
    }

    private void assertRoles(String methodName, Set<String> expectedRoles) throws Exception {
        assertRoles(AdminProductIntakeController.class, methodName, expectedRoles);
    }

    private void assertRoles(Class<?> controller, String methodName, Set<String> expectedRoles) throws Exception {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        String expression = method.getAnnotation(PreAuthorize.class).value();
        Set<String> actualRoles = java.util.Arrays.stream(expression.split("'"))
                .filter(value -> expectedRoles.contains(value))
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expectedRoles, actualRoles);
    }
}

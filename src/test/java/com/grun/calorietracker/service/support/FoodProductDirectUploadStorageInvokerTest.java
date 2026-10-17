package com.grun.calorietracker.service.support;

import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodProductDirectUploadStorageInvokerTest {

    @Test
    void everyObjectStorageCallSuspendsAnyDatabaseTransaction() {
        Arrays.stream(FoodProductDirectUploadStorageInvoker.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .forEach(method -> {
                    Transactional transactional = method.getAnnotation(Transactional.class);
                    assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation(), method.getName());
                });
    }
}

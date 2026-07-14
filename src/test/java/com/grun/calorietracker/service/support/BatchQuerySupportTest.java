package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchQuerySupportTest {

    @Test
    void loadInChunks_limitsEveryRepositoryCallAndPreservesResults() {
        List<Integer> values = IntStream.rangeClosed(1, 1201).boxed().toList();
        List<Integer> chunkSizes = new ArrayList<>();

        List<Integer> result = BatchQuerySupport.loadInChunks(values, chunk -> {
            chunkSizes.add(chunk.size());
            return new ArrayList<>(chunk);
        });

        assertEquals(List.of(500, 500, 201), chunkSizes);
        assertEquals(values, result);
    }
}
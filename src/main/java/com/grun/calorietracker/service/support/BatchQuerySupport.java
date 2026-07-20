package com.grun.calorietracker.service.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class BatchQuerySupport {

    public static final int DEFAULT_CHUNK_SIZE = 500;

    private BatchQuerySupport() {
    }

    public static <T, R> List<R> loadInChunks(
            Collection<T> values,
            Function<List<T>, List<R>> loader
    ) {
        return loadInChunks(values, DEFAULT_CHUNK_SIZE, loader);
    }

    static <T, R> List<R> loadInChunks(
            Collection<T> values,
            int chunkSize,
            Function<List<T>, List<R>> loader
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive.");
        }

        List<T> input = new ArrayList<>(values);
        List<R> result = new ArrayList<>();
        for (int start = 0; start < input.size(); start += chunkSize) {
            List<T> chunk = input.subList(start, Math.min(start + chunkSize, input.size()));
            List<R> loaded = loader.apply(chunk);
            if (loaded != null && !loaded.isEmpty()) {
                result.addAll(loaded);
            }
        }
        return result;
    }
}
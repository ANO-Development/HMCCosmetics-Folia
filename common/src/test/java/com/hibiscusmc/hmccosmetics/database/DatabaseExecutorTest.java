package com.hibiscusmc.hmccosmetics.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseExecutorTest {

    @Test
    void serializesDatabaseOperations() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        List<CompletableFuture<Void>> operations = new ArrayList<>();

        for (int index = 0; index < 100; index++) {
            operations.add(Database.execute(() -> {
                int current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                Thread.yield();
                active.decrementAndGet();
            }));
        }

        CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, maximumActive.get());
    }
}

package com.hibiscusmc.hmccosmetics.packets;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticPacketSnapshotsTest {

    @AfterEach
    void clearRegistry() {
        CosmeticPacketSnapshots.clear();
    }

    @Test
    void indexesEntityIdsByWorld() {
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        CosmeticPacketSnapshot first = snapshot(UUID.randomUUID(), firstWorld, 42);
        CosmeticPacketSnapshot second = snapshot(UUID.randomUUID(), secondWorld, 42);

        CosmeticPacketSnapshots.publish(first);
        CosmeticPacketSnapshots.publish(second);

        assertSame(first, CosmeticPacketSnapshots.get(firstWorld, 42));
        assertSame(second, CosmeticPacketSnapshots.get(secondWorld, 42));
        assertNull(CosmeticPacketSnapshots.findUnique(42));
    }

    @Test
    void copiesMutableIdentifierLists() {
        java.util.ArrayList<Integer> entityIds = new java.util.ArrayList<>(List.of(3, 4));
        CosmeticPacketSnapshot snapshot = snapshot(UUID.randomUUID(), UUID.randomUUID(), 9, entityIds);

        entityIds.add(5);

        assertEquals(List.of(3, 4), snapshot.backpackEntityIds());
    }

    @Test
    void supportsConcurrentSnapshotReplacementAndReads() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        List<CompletableFuture<Void>> futures = IntStream.range(0, 200)
            .mapToObj(index -> CompletableFuture.runAsync(() -> {
                CosmeticPacketSnapshots.publish(snapshot(playerId, worldId, index));
                CosmeticPacketSnapshots.get(playerId);
            }))
            .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        assertEquals(playerId, CosmeticPacketSnapshots.get(playerId).playerId());
    }

    @Test
    void virtualizesOnlyEmptyNonCreativeArmorSlots() {
        assertTrue(CosmeticPacketSnapshot.shouldVirtualizeContainerItem(GameMode.SURVIVAL, true));
        assertTrue(CosmeticPacketSnapshot.shouldVirtualizeContainerItem(GameMode.ADVENTURE, true));
        assertFalse(CosmeticPacketSnapshot.shouldVirtualizeContainerItem(GameMode.SURVIVAL, false));
        assertFalse(CosmeticPacketSnapshot.shouldVirtualizeContainerItem(GameMode.CREATIVE, true));
    }

    private CosmeticPacketSnapshot snapshot(UUID playerId, UUID worldId, int entityId) {
        return snapshot(playerId, worldId, entityId, List.of());
    }

    private CosmeticPacketSnapshot snapshot(UUID playerId, UUID worldId, int entityId, List<Integer> backpackIds) {
        return new CosmeticPacketSnapshot(playerId, worldId, entityId, false, false, false, true, true,
            Map.of(), Map.of(), Set.of(CosmeticSlot.HELMET), null,
            false, -1, backpackIds, false);
    }
}

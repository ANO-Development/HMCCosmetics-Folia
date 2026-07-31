package com.hibiscusmc.hmccosmetics.packets;

import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.GameMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticPacketSnapshots {

    private static final ConcurrentHashMap<UUID, CosmeticPacketSnapshot> BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EntityKey, UUID> BY_ENTITY = new ConcurrentHashMap<>();

    private CosmeticPacketSnapshots() {
    }

    public static void publish(@NotNull CosmeticUser user) {
        publish(user, CosmeticPacketSnapshot.capture(user));
    }

    public static void publish(@NotNull CosmeticUser user, @NotNull GameMode gameMode) {
        publish(user, CosmeticPacketSnapshot.capture(user, gameMode));
    }

    private static void publish(@NotNull CosmeticUser user, @Nullable CosmeticPacketSnapshot snapshot) {
        if (snapshot == null) {
            remove(user.getUniqueId());
            return;
        }

        publish(snapshot);
    }

    static void publish(@NotNull CosmeticPacketSnapshot snapshot) {
        CosmeticPacketSnapshot previous = BY_PLAYER.put(snapshot.playerId(), snapshot);
        if (previous != null) BY_ENTITY.remove(new EntityKey(previous.worldId(), previous.entityId()), previous.playerId());
        BY_ENTITY.put(new EntityKey(snapshot.worldId(), snapshot.entityId()), snapshot.playerId());
    }

    public static @Nullable CosmeticPacketSnapshot get(@NotNull UUID playerId) {
        return BY_PLAYER.get(playerId);
    }

    public static @Nullable CosmeticPacketSnapshot get(@NotNull UUID worldId, int entityId) {
        UUID playerId = BY_ENTITY.get(new EntityKey(worldId, entityId));
        return playerId == null ? null : BY_PLAYER.get(playerId);
    }

    public static @Nullable CosmeticPacketSnapshot findUnique(int entityId) {
        CosmeticPacketSnapshot match = null;
        for (Map.Entry<EntityKey, UUID> entry : BY_ENTITY.entrySet()) {
            if (entry.getKey().entityId() != entityId) continue;
            if (match != null) return null;
            match = BY_PLAYER.get(entry.getValue());
        }
        return match;
    }

    public static void remove(@NotNull UUID playerId) {
        CosmeticPacketSnapshot snapshot = BY_PLAYER.remove(playerId);
        if (snapshot != null) BY_ENTITY.remove(new EntityKey(snapshot.worldId(), snapshot.entityId()), playerId);
    }

    public static void clear() {
        BY_PLAYER.clear();
        BY_ENTITY.clear();
    }

    record EntityKey(UUID worldId, int entityId) {
    }
}

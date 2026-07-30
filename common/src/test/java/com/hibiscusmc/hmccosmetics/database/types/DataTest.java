package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTest {

    private final Data data = new TestData();

    @Test
    void separatesMultiplePersistentHiddenReasons() {
        UserData snapshot = new UserData(UUID.randomUUID());
        snapshot.addHiddenReason(CosmeticUser.HiddenReason.PLUGIN);
        snapshot.addHiddenReason(CosmeticUser.HiddenReason.COMMAND);

        assertEquals("HIDDEN=PLUGIN,HIDDEN=COMMAND", data.serializeData(snapshot));
    }

    @Test
    void restoresPersistentHiddenReasons() {
        UserData restored = data.deserializeUserData(UUID.randomUUID(), "HIDDEN=PLUGIN,HIDDEN=COMMAND");

        assertEquals(
            java.util.List.of(CosmeticUser.HiddenReason.PLUGIN, CosmeticUser.HiddenReason.COMMAND),
            restored.getHiddenReasons()
        );
    }

    private static final class TestData extends Data {
        @Override public void setup() {}
        @Override public void save(CosmeticUser user) {}
        @Override public CompletableFuture<UserData> get(UUID uniqueId) { return CompletableFuture.completedFuture(null); }
        @Override public void clear(UUID uniqueId) {}
    }
}

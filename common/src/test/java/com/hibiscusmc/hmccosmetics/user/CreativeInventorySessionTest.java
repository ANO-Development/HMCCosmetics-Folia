package com.hibiscusmc.hmccosmetics.user;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeInventorySessionTest {

    @Test
    void coalescesRepeatedEditRequests() {
        CosmeticUser user = new CosmeticUser(UUID.randomUUID());
        int revision = user.getCreativeInventoryRevision();

        assertTrue(user.beginCreativeInventoryEdit(revision));
        assertFalse(user.beginCreativeInventoryEdit(revision));
        assertTrue(user.isCreativeInventoryEditing());
    }

    @Test
    void invalidatesPendingRequestsWhenInventoryCloses() {
        CosmeticUser user = new CosmeticUser(UUID.randomUUID());
        int staleRevision = user.getCreativeInventoryRevision();

        assertFalse(user.finishCreativeInventoryEdit());
        assertFalse(user.beginCreativeInventoryEdit(staleRevision));
        assertTrue(user.beginCreativeInventoryEdit(user.getCreativeInventoryRevision()));
        assertTrue(user.finishCreativeInventoryEdit());
        assertFalse(user.isCreativeInventoryEditing());
    }
}

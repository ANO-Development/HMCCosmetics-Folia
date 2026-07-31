package com.hibiscusmc.hmccosmetics.packets;

import org.junit.jupiter.api.Test;

import static com.hibiscusmc.hmccosmetics.packets.CreativeInventoryPolicy.Action.CANCEL_AND_EDIT;
import static com.hibiscusmc.hmccosmetics.packets.CreativeInventoryPolicy.Action.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CreativeInventoryPolicyTest {

    @Test
    void ignoresNonCreativeInventoryActions() {
        assertEquals(PASS, CreativeInventoryPolicy.evaluate(false, true, true));
    }

    @Test
    void cancelsVirtualCosmeticWritesToEveryCreativeSlot() {
        assertEquals(CANCEL_AND_EDIT, CreativeInventoryPolicy.evaluate(true, false, true));
        assertEquals(CANCEL_AND_EDIT, CreativeInventoryPolicy.evaluate(true, true, true));
    }

    @Test
    void cancelsEveryWriteToVirtualEquipmentSlots() {
        assertEquals(CANCEL_AND_EDIT, CreativeInventoryPolicy.evaluate(true, true, false));
    }

    @Test
    void passesUnrelatedCreativeInventoryActions() {
        assertEquals(PASS, CreativeInventoryPolicy.evaluate(true, false, false));
    }
}

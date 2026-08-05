package com.hibiscusmc.hmccosmetics.user.manager;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HatOverlayPolicyTest {

    @Test
    void rendersAlongsidePhysicalHelmetsInSurvivalModes() {
        assertTrue(HatOverlayPolicy.shouldRender(GameMode.SURVIVAL, false, false, false, false));
        assertTrue(HatOverlayPolicy.shouldRender(GameMode.ADVENTURE, false, false, false, false));
    }

    @Test
    void preservesExistingModesWhenAnOverlayIsNotRequired() {
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.CREATIVE, false, false, false, false));
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.SPECTATOR, false, false, false, false));
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.SURVIVAL, true, false, false, false));
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.SURVIVAL, false, true, false, false));
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.SURVIVAL, false, false, true, false));
        assertFalse(HatOverlayPolicy.shouldRender(GameMode.SURVIVAL, false, false, false, true));
    }
}

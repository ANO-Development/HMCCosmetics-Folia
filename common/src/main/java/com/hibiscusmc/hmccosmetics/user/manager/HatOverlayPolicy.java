package com.hibiscusmc.hmccosmetics.user.manager;

import org.bukkit.GameMode;
import org.jetbrains.annotations.NotNull;

public final class HatOverlayPolicy {

    private HatOverlayPolicy() {
    }

    public static boolean shouldRender(@NotNull GameMode gameMode, boolean inWardrobe, boolean hidden,
                                       boolean requireEmpty, boolean physicalSlotEmpty) {
        boolean supportedGameMode = gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
        return supportedGameMode && !inWardrobe && !hidden && !requireEmpty && !physicalSlotEmpty;
    }
}

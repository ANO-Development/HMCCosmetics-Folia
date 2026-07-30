package com.hibiscusmc.hmccosmetics.api;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import me.lojosho.hibiscuscommons.util.FoliaScheduler;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public final class CosmeticUserOperations {

    private CosmeticUserOperations() {
    }

    public static boolean execute(@NotNull UUID playerId, @NotNull Consumer<CosmeticUser> operation) {
        CosmeticUser user = CosmeticUsers.getUser(playerId);
        if (user == null) return false;
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return false;

        return FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), player, () -> {
            CosmeticUser currentUser = CosmeticUsers.getUser(playerId);
            if (currentUser != null) operation.accept(currentUser);
        }, null) != null;
    }
}

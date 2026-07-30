package com.hibiscusmc.hmccosmetics.util.search;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import lombok.Getter;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerSearchManager {

    @Getter
    private final HMCCosmeticsPlugin plugin;
    @Getter
    private final PlayerSearchEngine engine;

    public PlayerSearchManager(@NotNull SearchEngine engine, @NotNull HMCCosmeticsPlugin plugin) {
        this.plugin = plugin;

        if (HibiscusCommonsPlugin.isOnFolia() && engine == SearchEngine.OCTREE) {
            plugin.getLogger().warning("The shared octree search engine is disabled on Folia; using region-owned nearby-player queries instead.");
            this.engine = new BukkitPlayerSearchEngine(plugin);
            return;
        }

        switch (engine) {
            case OCTREE -> this.engine = new OctreePlayerSearchEngine(plugin);
            default -> this.engine = new BukkitPlayerSearchEngine(plugin);
        }
    }

    public @NotNull List<Player> getPlayersInRange(@NotNull Location location, double range) {
        return engine.getPlayersInRange(location, range);
    }

    public enum SearchEngine {
        BUKKIT,
        OCTREE
    }
}

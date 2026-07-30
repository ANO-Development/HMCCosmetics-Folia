package com.hibiscusmc.hmccosmetics.cosmetic;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import lombok.extern.slf4j.Slf4j;
import me.lojosho.shaded.configurate.CommentedConfigurationNode;
import me.lojosho.shaded.configurate.ConfigurateException;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.yaml.YamlConfigurationLoader;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

@Slf4j
public class Cosmetics {
    private static volatile Map<String, Cosmetic> cosmetics = Map.of();
    private static final ThreadLocal<Map<String, Cosmetic>> LOADING_COSMETICS = new ThreadLocal<>();

    private static CosmeticProvider PROVIDER = CosmeticProvider.Default.INSTANCE;

    public static synchronized void addCosmetic(Cosmetic cosmetic) {
        Map<String, Cosmetic> loading = LOADING_COSMETICS.get();
        if (loading != null) {
            loading.put(cosmetic.getId(), cosmetic);
            return;
        }
        Map<String, Cosmetic> updated = new HashMap<>(cosmetics);
        updated.put(cosmetic.getId(), cosmetic);
        cosmetics = Map.copyOf(updated);
    }

    public static synchronized void removeCosmetic(String id) {
        Map<String, Cosmetic> updated = new HashMap<>(cosmetics);
        updated.remove(id);
        cosmetics = Map.copyOf(updated);
    }

    public static void removeCosmetic(Cosmetic cosmetic) {
        removeCosmetic(cosmetic.getId());
    }

    @Nullable
    public static Cosmetic getCosmetic(String id) {
        return cosmetics.get(id);
    }

    @Contract(pure = true)
    @NotNull
    public static Set<Cosmetic> values() {
        return Set.copyOf(cosmetics.values());
    }

    @Contract(pure = true)
    @NotNull
    public static Set<String> keys() {
        return cosmetics.keySet();
    }

    public static boolean hasCosmetic(String id) {
        return cosmetics.containsKey(id);
    }

    public static boolean hasCosmetic(Cosmetic cosmetic) {
        return cosmetics.containsValue(cosmetic);
    }

    public static void setup() {
        Map<String, Cosmetic> loadedCosmetics = new HashMap<>();
        LOADING_COSMETICS.set(loadedCosmetics);

        File cosmeticFolder = new File(HMCCosmeticsPlugin.getInstance().getDataFolder() + "/cosmetics");
        if (!cosmeticFolder.exists()) cosmeticFolder.mkdir();

        try (Stream<Path> walkStream = Files.walk(cosmeticFolder.toPath())) {
            walkStream.filter(p -> p.toFile().isFile()).forEach(child -> {
                if (child.toString().contains(".yml") || child.toString().contains(".yaml")) {
                    MessagesUtil.sendDebugMessages("Scanning " + child);
                    // Loads file
                    YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(child).build();
                    CommentedConfigurationNode root;
                    try {
                        root = loader.load();
                    } catch (ConfigurateException e) {
                        throw new RuntimeException(e);
                    }
                    setupCosmetics(root);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load cosmetics.", e);
        } finally {
            LOADING_COSMETICS.remove();
        }

        cosmetics = Map.copyOf(loadedCosmetics);
        refreshPermissions();
    }

    /**
     * Register a custom {@link CosmeticProvider} to provide your own user implementation to
     * be used and queried.
     * @param provider the provider to register
     * @throws IllegalArgumentException if the provider is already registered by another plugin
     */
    public static void registerProvider(final CosmeticProvider provider) {
        if(PROVIDER != CosmeticProvider.Default.INSTANCE) {
            throw new IllegalArgumentException("CosmeticProvider already registered by %s, this conflicts with %s attempting to register their own.".formatted(
                PROVIDER.getProviderPlugin().getName(),
                provider.getProviderPlugin().getName()
            ));
        }

        PROVIDER = provider;
    }

    /**
     * Fetch the current {@link CosmeticProvider} being used.
     * @return the current {@link CosmeticProvider} being used
     */
    public static CosmeticProvider getProvider() {
        return PROVIDER;
    }

    private static void setupCosmetics(@NotNull CommentedConfigurationNode config) {
        for (ConfigurationNode cosmeticConfig : config.childrenMap().values()) {
            String id = cosmeticConfig.key().toString();
            MessagesUtil.sendDebugMessages("Attempting to add " + id);
            ConfigurationNode slotNode = cosmeticConfig.node("slot");
            if (slotNode.virtual()) {
                MessagesUtil.sendDebugMessages("Unable to create " + id + " because there is no slot defined!", Level.WARNING);
                continue;
            }
            String slot = slotNode.getString("");
            CosmeticSlot cosmeticSlot = CosmeticSlot.valueOf(slot);
            if (cosmeticSlot == null) {
                MessagesUtil.sendDebugMessages("Unable to create " + id + " because " + slotNode.getString() + " is not a valid slot!", Level.WARNING);
                continue;
            }

            try {
                addCosmetic(PROVIDER.createCosmetic(id, cosmeticConfig, cosmeticSlot));
            } catch(Exception ex) {
                log.error("Unable to construct cosmetic for {}, skipping processing it.", id, ex);
            }
        }
    }

    public static void refreshPermissions() {
        final HMCCosmeticsPlugin instance = HMCCosmeticsPlugin.getInstance();
        for (Cosmetic cosmetic : Cosmetics.values()) {
            if (cosmetic.getPermission() == null) continue;
            if (instance.getServer().getPluginManager().getPermission(cosmetic.getPermission()) != null) continue;
            instance.getServer().getPluginManager().addPermission(new Permission(cosmetic.getPermission()));
        }
    }
}

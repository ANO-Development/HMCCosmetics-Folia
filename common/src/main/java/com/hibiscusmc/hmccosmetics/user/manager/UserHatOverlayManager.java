package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticArmorType;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.hibiscuscommons.util.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.logging.Level;

public final class UserHatOverlayManager {

    // Display passengers attach at player height, while the vanilla head model pivots 0.3 blocks lower.
    private static final float HEAD_PIVOT_OFFSET = -0.3F;

    private final CosmeticUser user;
    private ItemDisplay display;
    private double renderedScale = Double.NaN;
    private boolean spawnFailureLogged;

    public UserHatOverlayManager(@NotNull CosmeticUser user) {
        this.user = user;
    }

    public boolean update(@NotNull CosmeticArmorType cosmetic) {
        Player player = user.getPlayer();
        if (player == null || cosmetic.getEquipSlot() != EquipmentSlot.HEAD) {
            remove();
            return false;
        }

        ItemStack physicalHelmet = player.getInventory().getHelmet();
        boolean physicalSlotEmpty = physicalHelmet == null || physicalHelmet.getType().isAir();
        boolean requireEmpty = Settings.getSlotOption(EquipmentSlot.HEAD).isRequireEmpty();
        boolean shouldRender = HatOverlayPolicy.shouldRender(player.getGameMode(), user.isInWardrobe(), user.isHidden(),
            requireEmpty, physicalSlotEmpty);
        if (!shouldRender) {
            remove();
            return false;
        }

        ItemStack cosmeticItem = user.getUserCosmeticItem(cosmetic);
        if (cosmeticItem.getType().isAir()) {
            remove();
            return false;
        }

        ItemDisplay currentDisplay = getOrCreateDisplay(player);
        if (currentDisplay == null) return false;

        if (!cosmeticItem.equals(currentDisplay.getItemStack())) currentDisplay.setItemStack(cosmeticItem);
        updateTransformation(currentDisplay, player);
        updateRotation(player.getLocation());
        return true;
    }

    public void updateRotation(@NotNull Location location) {
        ItemDisplay currentDisplay = display;
        if (currentDisplay == null || !currentDisplay.isValid()) return;
        if (location.getWorld() == null || !currentDisplay.getWorld().equals(location.getWorld())) return;

        currentDisplay.setRotation(location.getYaw(), location.getPitch());
    }

    public void remove() {
        ItemDisplay currentDisplay = display;
        display = null;
        renderedScale = Double.NaN;
        if (currentDisplay == null) return;

        FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), currentDisplay, () -> {
            if (currentDisplay.isValid()) currentDisplay.remove();
        }, null);
    }

    private @Nullable ItemDisplay getOrCreateDisplay(@NotNull Player player) {
        ItemDisplay currentDisplay = display;
        if (currentDisplay != null && currentDisplay.isValid() && currentDisplay.getWorld().equals(player.getWorld())) {
            if (currentDisplay.getVehicle() == player || player.addPassenger(currentDisplay)) return currentDisplay;
            remove();
            return null;
        }

        if (currentDisplay != null && currentDisplay.isValid()) remove();
        display = null;
        try {
            ItemDisplay createdDisplay = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                entity.setInterpolationDelay(0);
                entity.setInterpolationDuration(1);
                entity.setTeleportDuration(1);
                entity.setShadowRadius(0.0F);
                entity.setShadowStrength(0.0F);
            });
            if (!player.addPassenger(createdDisplay)) {
                createdDisplay.remove();
                logSpawnFailure(null);
                return null;
            }

            display = createdDisplay;
            renderedScale = Double.NaN;
            spawnFailureLogged = false;
            return createdDisplay;
        } catch (RuntimeException exception) {
            logSpawnFailure(exception);
            return null;
        }
    }

    private void updateTransformation(@NotNull ItemDisplay currentDisplay, @NotNull Player player) {
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        double playerScale = scaleAttribute == null ? 1.0D : scaleAttribute.getValue();
        if (Double.compare(renderedScale, playerScale) == 0) return;

        float displayScale = (float) playerScale;
        Vector3f translation = new Vector3f(0.0F, HEAD_PIVOT_OFFSET * displayScale, 0.0F);
        Vector3f scale = new Vector3f(displayScale, displayScale, displayScale);
        currentDisplay.setTransformation(new Transformation(translation, new Quaternionf(), scale, new Quaternionf()));
        renderedScale = playerScale;
    }

    private void logSpawnFailure(@Nullable RuntimeException exception) {
        if (spawnFailureLogged) return;
        spawnFailureLogged = true;

        String message = "Unable to create the hat overlay for player " + user.getUniqueId();
        if (exception == null) {
            HMCCosmeticsPlugin.getInstance().getLogger().warning(message + ": the display could not be attached.");
            return;
        }
        HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, message, exception);
    }
}

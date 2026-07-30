package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.packets.CosmeticPacketSnapshot;
import com.hibiscusmc.hmccosmetics.packets.CosmeticPacketSnapshots;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.data.BukkitEntityData;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import lombok.Getter;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.util.FoliaScheduler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class UserBalloonManager {

    private final CosmeticUser user;
    @Getter
    private BalloonType balloonType;
    private CosmeticBalloonType cosmeticBalloonType;
    @Getter
    private UserBalloonPufferfish pufferfish;
    private final ArmorStand modelEntity;
    private volatile Location location;
    private volatile Vector velocity = new Vector();
    private volatile boolean removed;

    public UserBalloonManager(CosmeticUser user, @NotNull Location location) {
        this.user = user;
        this.location = location.clone();
        this.pufferfish = new UserBalloonPufferfish(user.getUniqueId(), NMSHandlers.getHandler().getUtilHandler().getNextEntityId(location.getWorld()), UUID.randomUUID());
        this.modelEntity = location.getWorld().spawn(location, ArmorStand.class, (e) -> {
            e.setInvisible(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setSmall(true);
            e.setMarker(true);
            e.setPersistent(false);
            e.setAI(false);
            e.getPersistentDataContainer().set(HMCCServerUtils.getCosmemeticMobKey(), PersistentDataType.BOOLEAN, true);
        });
    }

    public void spawnModel(@NotNull CosmeticBalloonType cosmeticBalloonType, Color color) {
        // redo this
        if (cosmeticBalloonType.getModelName() != null && Hooks.isActiveHook("ModelEngine")) {
            balloonType = BalloonType.MODELENGINE;
        } else {
            if (cosmeticBalloonType.getItem() != null) {
                balloonType = BalloonType.ITEM;
            } else {
                balloonType = BalloonType.NONE;
            }
        }
        this.cosmeticBalloonType = cosmeticBalloonType;
        MessagesUtil.sendDebugMessages("balloontype is " + balloonType);

        if (balloonType == BalloonType.MODELENGINE) {
            executeModel(() -> spawnModelEngineModel(cosmeticBalloonType, color));
            return;
        }
        if (balloonType == BalloonType.ITEM) {
            executeModel(() -> modelEntity.getEquipment().setHelmet(cosmeticBalloonType.getItem()));
        }
    }

    private void spawnModelEngineModel(@NotNull CosmeticBalloonType cosmeticBalloonType, Color color) {
        if (removed) return;
            String id = cosmeticBalloonType.getModelName();
            MessagesUtil.sendDebugMessages("Attempting Spawning for " + id);
            if (ModelEngineAPI.getBlueprint(id) == null) {
                MessagesUtil.sendDebugMessages("Invalid Model Engine Blueprint " + id, Level.SEVERE);
                return;
            }
            ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(modelEntity);
            ActiveModel model = ModelEngineAPI.createActiveModel(ModelEngineAPI.getBlueprint(id));
            model.setCanHurt(false);
            modeledEntity.addModel(model, false);

            if (color != null) {
                modeledEntity.getModels().forEach((d, singleModel) -> {
                    if (cosmeticBalloonType.isDyeablePart(d)) {
                        singleModel.setDefaultTint(color);
                        singleModel.getModelRenderer().sendToClient(ModelEngineAPI.getNMSHandler().createParsers());
                    }
                });
            }

            BukkitEntityData data = (BukkitEntityData) modeledEntity.getBase().getData();
            data.setBlockedCullIgnoreRadius((double) Settings.getViewDistance());
            data.getTracked().setPlayerPredicate(this::playerCheck);
    }

    public void remove() {
        // This code is like a brick road, always bumpy.
        // Basically, the balloon viewers ignore people in wardrobe, which well, if your the user in the wardrobe, ain't including you.
        // This manually passes in the viewers for it to destroy, which includes the person in the wardrobe
        if (user.getPlayer() != null && user.isInWardrobe()) pufferfish.destroyPufferfish(List.of(user.getPlayer()));
        else pufferfish.destroyPufferfish();

        removed = true;
        executeModel(() -> {
            if (balloonType == BalloonType.MODELENGINE) {
                ModeledEntity entity = ModelEngineAPI.getModeledEntity(modelEntity);
                if (entity != null) {
                    entity.destroy();
                    MessagesUtil.sendDebugMessages("Balloon Model Engine Removal");
                }
            }
            modelEntity.remove();
        });
        cosmeticBalloonType = null;
        MessagesUtil.sendDebugMessages("Balloon Entity Removed");
    }

    public void addPlayerToModel(final CosmeticUser user, final CosmeticBalloonType cosmeticBalloonType) {
        addPlayerToModel(user, cosmeticBalloonType, null);
    }

    public void addPlayerToModel(final CosmeticUser user, final CosmeticBalloonType cosmeticBalloonType, Color color) {
        executeModel(() -> addPlayerToModelOwned(user, cosmeticBalloonType, color));
    }

    private void addPlayerToModelOwned(final CosmeticUser user, final CosmeticBalloonType cosmeticBalloonType, Color color) {
        if (removed) return;
        if (balloonType == BalloonType.MODELENGINE) {
            final ModeledEntity model = ModelEngineAPI.getModeledEntity(modelEntity);
            if (model == null) {
                spawnModel(cosmeticBalloonType, color);
                MessagesUtil.sendDebugMessages("model is null");
                return;
            }

            MessagesUtil.sendDebugMessages("Show to player");
            return;
        }
        if (balloonType == BalloonType.ITEM) {
            modelEntity.getEquipment().setHelmet(user.getUserCosmeticItem(cosmeticBalloonType));
        }
    }
    public void removePlayerFromModel(final Player viewer) {
        executeModel(this::removePlayerFromModelOwned);
    }

    private void removePlayerFromModelOwned() {
        if (removed) return;
        if (balloonType == BalloonType.MODELENGINE) {
            final ModeledEntity model = ModelEngineAPI.getModeledEntity(modelEntity);
            if (model == null) return;

            MessagesUtil.sendDebugMessages("Hidden from player");
            return;
        }
        if (balloonType == BalloonType.ITEM) {
            modelEntity.getEquipment().clear();
            return;
        }
    }

    public Entity getModelEntity() {
        return this.modelEntity;
    }


    public int getPufferfishBalloonId() {
        return pufferfish.getPufferFishEntityId();
    }

    public UUID getPufferfishBalloonUniqueId() {
        return pufferfish.getUuid();
    }

    public UUID getModelUnqiueId() {
        return getModelEntity().getUniqueId();
    }

    public int getModelId() {
        return getModelEntity().getEntityId();
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        Location target = location.clone();
        Location previous = this.location;
        this.location = target;
        executeModel(() -> modelEntity.teleportAsync(target).whenComplete((success, throwable) -> {
            if (throwable == null && success) return;
            if (this.location == target) this.location = previous;
            HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, "Unable to move balloon for " + user.getUniqueId(), throwable);
        }));
    }

    public Vector getVelocity() {
        return velocity.clone();
    }

    public void setVelocity(Vector vector) {
        Vector target = vector.clone();
        this.velocity = target;
        executeModel(() -> modelEntity.setVelocity(target));
    }

    public void sendRemoveLeashPacket(List<Player> viewer) {
        HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), -1, viewer);
    }

    public void sendRemoveLeashPacket() {
        HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), -1, getLocation());
    }

    public void sendLeashPacket(int entityId) {
        if (cosmeticBalloonType.isShowLead()) {
            HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), entityId, getLocation());
        }
    }

    public enum BalloonType {
        MODELENGINE,
        ITEM,
        NONE
    }

    private boolean playerCheck(final Player player) {
        CosmeticPacketSnapshot owner = CosmeticPacketSnapshots.get(user.getUniqueId());
        if (owner == null || owner.hidden()) return false;
        if (user.getUniqueId().equals(player.getUniqueId())) return true;
        if (owner.inWardrobe()) return false;
        CosmeticPacketSnapshot viewer = CosmeticPacketSnapshots.get(player.getUniqueId());
        return viewer == null || !viewer.inWardrobe();
    }

    public boolean isModelAvailable() {
        return !removed;
    }

    private void executeModel(@NotNull Runnable task) {
        FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), modelEntity, task, () -> removed = true);
    }
}

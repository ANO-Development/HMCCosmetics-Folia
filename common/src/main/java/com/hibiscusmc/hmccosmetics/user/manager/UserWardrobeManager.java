package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.config.section.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.section.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import me.lojosho.hibiscuscommons.nms.NMSPacketSender;
import me.lojosho.hibiscuscommons.packets.wrapper.PacketWrapper;
import me.lojosho.hibiscuscommons.util.FoliaScheduler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class UserWardrobeManager {

    @Getter
    private final int NPC_ID;
    @Getter
    private final int ARMORSTAND_ID;
    @Getter
    private final UUID WARDROBE_UUID;
    @Getter
    private String npcName;
    @Getter
    private GameMode originalGamemode;
    @Getter
    private final CosmeticUser user;
    @Getter
    private final Wardrobe wardrobe;
    @Getter
    private final WardrobeLocation wardrobeLocation;
    @Getter
    private final Location viewingLocation;
    @Getter
    private final Location npcLocation;
    @Getter
    private Location exitLocation;
    @Getter
    private BossBar bossBar;
    @Getter
    private boolean active;
    @Setter
    @Getter
    private WardrobeStatus wardrobeStatus;
    @Getter
    @Setter
    private Menu lastOpenMenu;
    private ScheduledTask updateTask;

    private NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
    private NMSPacketSender packetSender = NMSHandlers.getHandler().getPacketSender();

    public UserWardrobeManager(CosmeticUser user, Wardrobe wardrobe) {
        World world = user.getEntity().getWorld();
        NPC_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId(world);
        ARMORSTAND_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId(world);
        WARDROBE_UUID = UUID.randomUUID();
        this.user = user;

        this.wardrobe = wardrobe;
        this.wardrobeLocation = wardrobe.getLocation();

        this.exitLocation = wardrobeLocation.getLeaveLocation();
        this.viewingLocation = wardrobeLocation.getViewerLocation();
        this.npcLocation = wardrobeLocation.getNpcLocation();

        String defaultMenu = wardrobe.getDefaultMenu();
        if (defaultMenu != null) {
            // User has defined a custom menu in the wardrobe config
            Menu menu = Menus.getMenu(defaultMenu);
            if (menu != null) {
                // User provided a good, valid menu
                this.lastOpenMenu = Menus.getMenu(defaultMenu);
            } else {
                // User provided a menu that does not exist in HMCC
                this.lastOpenMenu = Menus.getDefaultMenu();
                MessagesUtil.sendDebugMessages("Unable to set menu (" + defaultMenu + ") in wardrobe " + getWardrobe().getId() + ". Defaulting to default menu defined in config.yml", Level.WARNING);
                if (this.lastOpenMenu == null) {
                    // That means that even the default menu is null in the config.
                    MessagesUtil.sendDebugMessages("Unable to set any menu in wardrobe " + getWardrobe().getId() + " as the fallback default menu (defined in config.yml) is invalid.", Level.WARNING);
                }
            }
        }

        wardrobeStatus = WardrobeStatus.SETUP;
    }

    public void start() {
        setWardrobeStatus(WardrobeStatus.STARTING);
        user.refreshPacketSnapshot();
        Player player = user.getPlayer();

        this.originalGamemode = player.getGameMode();
        if (WardrobeSettings.isReturnLastLocation()) {
            this.exitLocation = player.getLocation().clone();
        }

        user.hidePlayer();
        if (!Bukkit.getServer().getAllowFlight()) player.setAllowFlight(true);
        MessagesUtil.sendMessage(player, "opened-wardrobe");

        Runnable run = () -> beginStart(player);


        if (WardrobeSettings.isEnabledTransition()) {
            MessagesUtil.sendTitle(
                    user.getPlayer(),
                    WardrobeSettings.getTransitionText(),
                    WardrobeSettings.getTransitionFadeIn(),
                    WardrobeSettings.getTransitionStay(),
                    WardrobeSettings.getTransitionFadeOut()
            );
            FoliaScheduler.runEntityLater(HMCCosmeticsPlugin.getInstance(), player, run, this::retire, WardrobeSettings.getTransitionDelay());
        } else {
            run.run();
        }

    }

    public void end() {
        setWardrobeStatus(WardrobeStatus.STOPPING);
        user.refreshPacketSnapshot();
        Player player = user.getPlayer();
        if (player == null) return;
        ScheduledTask runningTask = updateTask;
        updateTask = null;
        if (runningTask != null) runningTask.cancel();

        List<Player> viewer = Collections.singletonList(player);
        if (!Bukkit.getServer().getAllowFlight()) player.setAllowFlight(false);
        MessagesUtil.sendMessage(player, "closed-wardrobe");

        Runnable run = () -> {
            this.active = false;

            // For Wardrobe Temp Cosmetics
            for (Cosmetic cosmetic : user.getCosmetics()) {
                MessagesUtil.sendDebugMessages("Checking... " + cosmetic.getId());
                if (!user.canEquipCosmetic(cosmetic)) {
                    MessagesUtil.sendDebugMessages("Unable to keep " + cosmetic.getId());
                    user.removeCosmeticSlot(cosmetic.getSlot());
                }
            }

            // NPC
            if (user.isBalloonSpawned()) user.getBalloonManager().sendRemoveLeashPacket();
            HMCCPacketManager.sendEntityDestroyPacket(NPC_ID, viewer); // Success
            HMCCPacketManager.sendRemovePlayerPacket(player, WARDROBE_UUID, viewer); // Success

            // Player
            packetBuilder.buildEntityCameraPacket(player.getEntityId()).sendPacket(viewer);
            user.getPlayer().setInvisible(false);

            // Armorstand
            HMCCPacketManager.sendEntityDestroyPacket(ARMORSTAND_ID, viewer); // Sucess

            //PacketManager.sendEntityDestroyPacket(player.getEntityId(), viewer); // Success
            if (WardrobeSettings.isForceExitGamemode()) {
                MessagesUtil.sendDebugMessages("Force Exit Gamemode " + WardrobeSettings.getExitGamemode());
                player.setGameMode(WardrobeSettings.getExitGamemode());
                packetBuilder.buildPlayerGamemodeChangePacket(WardrobeSettings.getExitGamemode()).sendPacket(viewer);
            } else {
                MessagesUtil.sendDebugMessages("Original Gamemode " + this.originalGamemode);
                player.setGameMode(this.originalGamemode);
                packetBuilder.buildPlayerGamemodeChangePacket(this.originalGamemode).sendPacket(viewer);
            }
            user.showPlayer();

            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                user.respawnBackpack();
                //PacketManager.ridingMountPacket(player.getEntityId(), VIEWER.getBackpackEntity().getEntityId(), viewer);
            }

            if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
                //user.respawnBalloon();
                //PacketManager.sendLeashPacket(VIEWER.getBalloonEntity().getPufferfishBalloonId(), player.getEntityId(), viewer);
            }

            if (WardrobeSettings.isEnabledBossbar()) {
                player.hideBossBar(bossBar);
            }

            Location target = Objects.requireNonNullElseGet(exitLocation, () -> player.getWorld().getSpawnLocation()).clone();
            player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((success, exception) -> {
                if (exception != null || !success) {
                    HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, "Unable to teleport " + user.getUniqueId() + " out of a wardrobe.", exception);
                }
                FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), player, () -> completeEnd(player, viewer), this::retire);
            });
        };
        run.run();
    }

    private void completeEnd(Player player, List<Player> viewer) {
        HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
        for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
            items.put(slot, player.getInventory().getItem(slot));
        }
        packetBuilder.buildEntityEquipmentSlotUpdatePacket(player.getEntityId(), items).sendPacket(viewer);
        user.updateCosmetic();
    }

    private void update() {
        final AtomicInteger data = new AtomicInteger();

        Runnable runnable = () -> {
                Player player = user.getPlayer();
                if (!active || player == null) {
                    MessagesUtil.sendDebugMessages("WardrobeEnd[user=" + user.getUniqueId() + ",reason=Active is false]");
                    ScheduledTask task = updateTask;
                    if (task != null) task.cancel();
                    updateTask = null;
                    return;
                }
                MessagesUtil.sendDebugMessages("WardrobeUpdate[user=" + user.getUniqueId() + ",status=" + getWardrobeStatus() + "]");
                List<Player> viewer = Collections.singletonList(player);
                List<Player> outsideViewers = HMCCPacketManager.getViewers(viewingLocation);
                outsideViewers.remove(player);

                Location location = npcLocation.clone();
                int yaw = data.get();
                location.setYaw(yaw);

                HMCCPacketManager.sendRotateHeadPacket(NPC_ID, location, viewer);
                user.hidePlayer();
                int rotationSpeed = WardrobeSettings.getRotationSpeed();
                int newYaw = HMCCServerUtils.getNextYaw(yaw - 30, rotationSpeed);
                location.setYaw(newYaw);
                packetBuilder.buildEntityRotatePacket(NPC_ID, newYaw, 0, false).sendPacket(viewer);
                int nextyaw = HMCCServerUtils.getNextYaw(yaw, rotationSpeed);
                data.set(nextyaw);

                for (CosmeticSlot slot : CosmeticSlot.values().values()) {
                    HMCCPacketManager.equipmentSlotUpdate(NPC_ID, user, slot, viewer);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK) && user.getUserBackpackManager() != null) {
                    HMCCPacketManager.sendTeleportPacket(user.getUserBackpackManager().getFirstArmorStandId(), location, false, viewer);
                    packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}).sendPacket(viewer);
                    user.getUserBackpackManager().getEntityManager().setRotation(nextyaw);
                    HMCCPacketManager.sendEntityDestroyPacket(user.getUserBackpackManager().getFirstArmorStandId(), outsideViewers);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON) && user.isBalloonSpawned()) {
                    user.getBalloonManager().sendRemoveLeashPacket(outsideViewers);
                    if (user.getBalloonManager().getBalloonType() != UserBalloonManager.BalloonType.MODELENGINE) {
                        HMCCPacketManager.sendEntityDestroyPacket(user.getBalloonManager().getModelId(), outsideViewers);
                    }
                    user.getBalloonManager().sendLeashPacket(NPC_ID);
                }

                if (WardrobeSettings.isEquipPumpkin()) {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer().getEntityId(), EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN), viewer);
                } else {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer(), true, viewer); // Optifine dumbassery
                }
        };

        updateTask = FoliaScheduler.runEntityAtFixedRate(HMCCosmeticsPlugin.getInstance(), user.getPlayer(), runnable, this::retire, 1L, 2L);
    }

    private void beginStart(Player player) {
        if (!player.isOnline()) {
            retire();
            return;
        }
        player.teleportAsync(viewingLocation.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((success, exception) -> {
            if (exception != null || !success) {
                HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, "Unable to teleport " + user.getUniqueId() + " into a wardrobe.", exception);
                FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), player, () -> {
                    player.setInvisible(false);
                    setWardrobeStatus(WardrobeStatus.SETUP);
                    retire();
                }, this::retire);
                return;
            }
            FoliaScheduler.runEntity(HMCCosmeticsPlugin.getInstance(), player, () -> activate(player), this::retire);
        });
    }

    private void activate(Player player) {
        List<Player> viewer = Collections.singletonList(player);
        List<PacketWrapper> viewerPackets = new ArrayList<>();

        viewerPackets.add(packetBuilder.buildEntitySpawnPacket(ARMORSTAND_ID, UUID.randomUUID(), EntityType.ARMOR_STAND, viewingLocation));
        viewerPackets.add(packetBuilder.buildEntityMetadataPacket(ARMORSTAND_ID, HMCCPacketManager.getInvisibleArmorStandData()));
        viewerPackets.add(packetBuilder.buildEntityTeleportPacket(ARMORSTAND_ID, viewingLocation.getX(), viewingLocation.getY(), viewingLocation.getZ(), viewingLocation.getYaw(), viewingLocation.getPitch(), false));
        viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(ARMORSTAND_ID, viewingLocation));

        player.setInvisible(true);
        viewerPackets.add(packetBuilder.buildPlayerGamemodeChangePacket(GameMode.SPECTATOR));
        viewerPackets.add(packetBuilder.buildEntityCameraPacket(ARMORSTAND_ID));

        npcName = "Mannequin";
        viewerPackets.add(packetBuilder.buildPlayerInfoAddPacket(player, NPC_ID, WARDROBE_UUID, npcName));
        viewerPackets.add(packetBuilder.buildEntitySpawnPacket(NPC_ID, WARDROBE_UUID, EntityType.PLAYER, npcLocation));
        viewerPackets.add(packetBuilder.buildEntityMetadataPacket(NPC_ID, HMCCPacketManager.getPlayerOverlayMetaData()));
        viewerPackets.add(packetBuilder.buildPlayerScoreboardRemovePacket(player, npcName));
        viewerPackets.add(packetBuilder.buildPlayerScoreboardCreatePacket(player, npcName));
        viewerPackets.add(packetBuilder.buildPlayerScoreboardAddPlayersPacket(player, npcName));
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) viewerPackets.add(packetBuilder.buildEntityAttributePacket(NPC_ID, Attribute.SCALE, scaleAttribute.getValue()));
        viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(NPC_ID, npcLocation));
        viewerPackets.add(packetBuilder.buildEntityRotatePacket(NPC_ID, npcLocation, true));

        if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
            if (user.getUserBackpackManager() == null) user.respawnBackpack();
            if (user.isBackpackSpawned()) {
                user.getUserBackpackManager().getEntityManager().teleport(npcLocation.clone().add(0, 2, 0));
                viewerPackets.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(user.getUserBackpackManager().getFirstArmorStandId(), Map.of(EquipmentSlot.HEAD, user.getUserCosmeticItem(user.getCosmetic(CosmeticSlot.BACKPACK)))));
                viewerPackets.add(packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}));
            }
        }

        packetSender.sendBundle(viewerPackets, viewer);

        if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
            if (user.getBalloonManager() == null) user.respawnBalloon();
            if (user.isBalloonSpawned()) {
                CosmeticBalloonType cosmetic = (CosmeticBalloonType) user.getCosmetic(CosmeticSlot.BALLOON);
                user.getBalloonManager().sendRemoveLeashPacket(viewer);
                user.getBalloonManager().sendLeashPacket(NPC_ID);
                Location balloonLocation = npcLocation.clone().add(cosmetic.getBalloonOffset());
                HMCCPacketManager.sendTeleportPacket(user.getBalloonManager().getPufferfishBalloonId(), balloonLocation, false, viewer);
                user.getBalloonManager().setLocation(balloonLocation);
            }
        }

        if (WardrobeSettings.isEnabledBossbar()) {
            float progress = WardrobeSettings.getBossbarProgress();
            Component message = MessagesUtil.processStringNoKey(player, WardrobeSettings.getBossbarMessage());
            bossBar = BossBar.bossBar(message, progress, WardrobeSettings.getBossbarColor(), WardrobeSettings.getBossbarOverlay());
            player.showBossBar(bossBar);
        }

        if (WardrobeSettings.isEnterOpenMenu()) {
            Menu menu = Menus.getDefaultMenu();
            if (menu != null) menu.openMenu(user);
        }

        this.active = true;
        update();
        setWardrobeStatus(WardrobeStatus.RUNNING);
        user.refreshPacketSnapshot();
    }

    private void retire() {
        active = false;
        updateTask = null;
        user.refreshPacketSnapshot();
    }

    public enum WardrobeStatus {
        SETUP,
        STARTING,
        RUNNING,
        STOPPING,
    }

}

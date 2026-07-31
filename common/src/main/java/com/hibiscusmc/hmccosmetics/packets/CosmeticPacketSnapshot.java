package com.hibiscusmc.hmccosmetics.packets;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticArmorType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.manager.UserBackpackManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CosmeticPacketSnapshot {

    private final UUID playerId;
    private final UUID worldId;
    private final int entityId;
    private final boolean inWardrobe;
    private final boolean wardrobeRunning;
    private final boolean hidden;
    private final boolean preventOffhandSwapping;
    private final boolean interceptPassengerPackets;
    private final Map<Integer, ItemStack> containerItems;
    private final Map<EquipmentSlot, ItemStack> equipmentItems;
    private final Map<EquipmentSlot, ItemStack> ownerEquipmentItems;
    private final Set<CosmeticSlot> equippedSlots;
    private final ItemStack mainHand;
    private final boolean invisible;
    private final int backpackEntityId;
    private final List<Integer> backpackEntityIds;
    private final boolean firstPersonBackpack;

    CosmeticPacketSnapshot(UUID playerId, UUID worldId, int entityId, boolean inWardrobe, boolean wardrobeRunning, boolean hidden,
                           boolean preventOffhandSwapping, boolean interceptPassengerPackets,
                           Map<Integer, ItemStack> containerItems, Map<EquipmentSlot, ItemStack> equipmentItems,
                           Map<EquipmentSlot, ItemStack> ownerEquipmentItems, Set<CosmeticSlot> equippedSlots,
                           ItemStack mainHand, boolean invisible, int backpackEntityId,
                           List<Integer> backpackEntityIds, boolean firstPersonBackpack) {
        this.playerId = playerId;
        this.worldId = worldId;
        this.entityId = entityId;
        this.inWardrobe = inWardrobe;
        this.wardrobeRunning = wardrobeRunning;
        this.hidden = hidden;
        this.preventOffhandSwapping = preventOffhandSwapping;
        this.interceptPassengerPackets = interceptPassengerPackets;
        this.containerItems = cloneItems(containerItems);
        this.equipmentItems = cloneEquipment(equipmentItems);
        this.ownerEquipmentItems = cloneEquipment(ownerEquipmentItems);
        this.equippedSlots = Set.copyOf(equippedSlots);
        this.mainHand = cloneItem(mainHand);
        this.invisible = invisible;
        this.backpackEntityId = backpackEntityId;
        this.backpackEntityIds = List.copyOf(backpackEntityIds);
        this.firstPersonBackpack = firstPersonBackpack;
    }

    public static @Nullable CosmeticPacketSnapshot capture(@NotNull CosmeticUser user) {
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return null;

        return capture(user, player, player.getGameMode());
    }

    static @Nullable CosmeticPacketSnapshot capture(@NotNull CosmeticUser user, @NotNull GameMode gameMode) {
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return null;

        return capture(user, player, gameMode);
    }

    private static CosmeticPacketSnapshot capture(@NotNull CosmeticUser user, @NotNull Player player, @NotNull GameMode gameMode) {
        Location location = player.getLocation();
        boolean inWardrobe = user.isInWardrobe();
        UserWardrobeManager wardrobe = user.getWardrobeManager();
        boolean wardrobeRunning = wardrobe != null && wardrobe.getWardrobeStatus() == UserWardrobeManager.WardrobeStatus.RUNNING;
        Map<Integer, ItemStack> containerItems = new HashMap<>();
        Map<EquipmentSlot, ItemStack> equipmentItems = new EnumMap<>(EquipmentSlot.class);
        Map<EquipmentSlot, ItemStack> ownerEquipmentItems = new EnumMap<>(EquipmentSlot.class);

        if (!inWardrobe) {
            for (Cosmetic cosmetic : user.getCosmetics()) {
                if (!(cosmetic instanceof CosmeticArmorType armorType)) continue;

                EquipmentSlot equipmentSlot = armorType.getEquipSlot();
                ItemStack physicalItem = player.getInventory().getItem(equipmentSlot);
                if (physicalItem == null) physicalItem = new ItemStack(Material.AIR);
                boolean physicalSlotEmpty = physicalItem.getType().isAir();
                boolean emptyRequired = Settings.getSlotOption(equipmentSlot).isRequireEmpty();
                if (emptyRequired && !physicalSlotEmpty) continue;

                ItemStack cosmeticItem = user.getUserCosmeticItem(armorType);
                if (shouldVirtualizeContainerItem(gameMode, physicalSlotEmpty)) {
                    containerItems.put(HMCCInventoryUtils.getPacketArmorSlot(equipmentSlot), cosmeticItem);
                    ownerEquipmentItems.put(equipmentSlot, cosmeticItem);
                } else {
                    ownerEquipmentItems.put(equipmentSlot, physicalItem);
                }
                equipmentItems.put(equipmentSlot, cosmeticItem);
            }
        }

        UserBackpackManager backpack = user.getUserBackpackManager();
        Cosmetic backpackCosmetic = user.getCosmetic(CosmeticSlot.BACKPACK);
        boolean firstPersonBackpack = backpackCosmetic instanceof CosmeticBackpackType backpackType && backpackType.isFirstPersonCompadible();
        int backpackEntityId = backpack == null ? -1 : backpack.getFirstArmorStandId();
        List<Integer> backpackEntityIds = backpack == null ? List.of() : List.copyOf(backpack.getEntityManager().getIds());

        return new CosmeticPacketSnapshot(user.getUniqueId(), location.getWorld().getUID(), player.getEntityId(), inWardrobe,
            wardrobeRunning, user.isHidden(), Settings.isPreventOffhandSwapping(), Settings.isBackpackInterceptPassengerPacket(),
            containerItems, equipmentItems, ownerEquipmentItems, user.getSlotsWithCosmetics(),
            player.getInventory().getItemInMainHand(), player.isInvisible(), backpackEntityId,
            backpackEntityIds, firstPersonBackpack);
    }

    static boolean shouldVirtualizeContainerItem(@NotNull GameMode gameMode, boolean physicalSlotEmpty) {
        return physicalSlotEmpty && gameMode != GameMode.CREATIVE;
    }

    static boolean shouldUseOwnerEquipment(@NotNull UUID ownerId, @NotNull UUID viewerId) {
        return ownerId.equals(viewerId);
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID worldId() {
        return worldId;
    }

    public int entityId() {
        return entityId;
    }

    public boolean inWardrobe() {
        return inWardrobe;
    }

    public boolean wardrobeRunning() {
        return wardrobeRunning;
    }

    public boolean hidden() {
        return hidden;
    }

    public boolean preventOffhandSwapping() {
        return preventOffhandSwapping;
    }

    public boolean interceptPassengerPackets() {
        return interceptPassengerPackets;
    }

    public @Nullable ItemStack containerItem(int slot) {
        return cloneItem(containerItems.get(slot));
    }

    public boolean hasContainerItem(int slot) {
        return containerItems.containsKey(slot);
    }

    public Map<EquipmentSlot, ItemStack> equipmentItemsFor(@NotNull UUID viewerId) {
        Map<EquipmentSlot, ItemStack> selectedItems = shouldUseOwnerEquipment(playerId, viewerId) ? ownerEquipmentItems : equipmentItems;
        return cloneEquipment(selectedItems);
    }

    public boolean hasCosmetic(CosmeticSlot slot) {
        return equippedSlots.contains(slot);
    }

    public @Nullable ItemStack mainHand() {
        return cloneItem(mainHand);
    }

    public boolean invisible() {
        return invisible;
    }

    public int backpackEntityId() {
        return backpackEntityId;
    }

    public List<Integer> backpackEntityIds() {
        return backpackEntityIds;
    }

    public boolean firstPersonBackpack() {
        return firstPersonBackpack;
    }

    private static Map<Integer, ItemStack> cloneItems(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> copy = new HashMap<>();
        source.forEach((slot, item) -> copy.put(slot, cloneItem(item)));
        return Map.copyOf(copy);
    }

    private static Map<EquipmentSlot, ItemStack> cloneEquipment(Map<EquipmentSlot, ItemStack> source) {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        source.forEach((slot, item) -> copy.put(slot, cloneItem(item)));
        return Map.copyOf(copy);
    }

    private static @Nullable ItemStack cloneItem(@Nullable ItemStack item) {
        return item == null ? null : item.clone();
    }
}

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
import org.bukkit.Location;
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
    private final Set<CosmeticSlot> equippedSlots;
    private final ItemStack mainHand;
    private final boolean invisible;
    private final int backpackEntityId;
    private final List<Integer> backpackEntityIds;
    private final boolean firstPersonBackpack;

    CosmeticPacketSnapshot(UUID playerId, UUID worldId, int entityId, boolean inWardrobe, boolean wardrobeRunning, boolean hidden,
                                   boolean preventOffhandSwapping, boolean interceptPassengerPackets,
                                   Map<Integer, ItemStack> containerItems, Map<EquipmentSlot, ItemStack> equipmentItems,
                                   Set<CosmeticSlot> equippedSlots, ItemStack mainHand, boolean invisible,
                                   int backpackEntityId, List<Integer> backpackEntityIds, boolean firstPersonBackpack) {
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

        Location location = player.getLocation();
        boolean inWardrobe = user.isInWardrobe();
        UserWardrobeManager wardrobe = user.getWardrobeManager();
        boolean wardrobeRunning = wardrobe != null && wardrobe.getWardrobeStatus() == UserWardrobeManager.WardrobeStatus.RUNNING;
        Map<Integer, ItemStack> containerItems = new HashMap<>();
        Map<EquipmentSlot, ItemStack> equipmentItems = new EnumMap<>(EquipmentSlot.class);

        if (!inWardrobe) {
            for (Cosmetic cosmetic : user.getCosmetics()) {
                if (!(cosmetic instanceof CosmeticArmorType armorType)) continue;

                EquipmentSlot equipmentSlot = armorType.getEquipSlot();
                boolean emptyRequired = Settings.getSlotOption(equipmentSlot).isRequireEmpty();
                if (emptyRequired && !player.getInventory().getItem(equipmentSlot).getType().isAir()) continue;

                ItemStack cosmeticItem = user.getUserCosmeticItem(armorType);
                containerItems.put(HMCCInventoryUtils.getPacketArmorSlot(equipmentSlot), cosmeticItem);
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
            containerItems, equipmentItems, user.getSlotsWithCosmetics(), player.getInventory().getItemInMainHand(),
            player.isInvisible(), backpackEntityId, backpackEntityIds, firstPersonBackpack);
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

    public Map<EquipmentSlot, ItemStack> equipmentItems() {
        return cloneEquipment(equipmentItems);
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

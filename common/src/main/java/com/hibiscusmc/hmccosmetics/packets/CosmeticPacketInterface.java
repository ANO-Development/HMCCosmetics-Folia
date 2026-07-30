package com.hibiscusmc.hmccosmetics.packets;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import me.lojosho.hibiscuscommons.packets.PacketAction;
import me.lojosho.hibiscuscommons.packets.PacketContext;
import me.lojosho.hibiscuscommons.packets.PacketInterface;
import me.lojosho.hibiscuscommons.packets.data.ContainerContentWrapper;
import me.lojosho.hibiscuscommons.packets.data.EntityEquipmentWrapper;
import me.lojosho.hibiscuscommons.packets.data.InventoryClickWrapper;
import me.lojosho.hibiscuscommons.packets.data.PassengerWrapper;
import me.lojosho.hibiscuscommons.packets.data.PlayerActionWrapper;
import me.lojosho.hibiscuscommons.packets.data.PlayerInputWrapper;
import me.lojosho.hibiscuscommons.packets.data.PlayerInteractWrapper;
import me.lojosho.hibiscuscommons.packets.data.PlayerScaleWrapper;
import me.lojosho.hibiscuscommons.packets.data.PlayerSwingWrapper;
import me.lojosho.hibiscuscommons.packets.data.SlotContentWrapper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CosmeticPacketInterface implements PacketInterface {

    @Override
    public @NotNull PacketAction writeContainerContent(@NotNull PacketContext context, @NotNull ContainerContentWrapper wrapper) {
        if (wrapper.getWindowId() != 0) return PacketAction.NOTHING;
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        if (snapshot == null || snapshot.inWardrobe()) return PacketAction.NOTHING;

        List<ItemStack> items = new ArrayList<>(wrapper.getSlotData());
        boolean changed = false;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack cosmeticItem = snapshot.containerItem(slot);
            if (cosmeticItem == null || cosmeticItem.getType().isAir()) continue;
            items.set(slot, cosmeticItem);
            changed = true;
        }
        if (!changed) return PacketAction.NOTHING;

        wrapper.setSlotData(items);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeSlotContent(@NotNull PacketContext context, @NotNull SlotContentWrapper wrapper) {
        if (wrapper.getWindowId() != 0) return PacketAction.NOTHING;
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        if (snapshot == null || snapshot.inWardrobe()) return PacketAction.NOTHING;

        ItemStack cosmeticItem = snapshot.containerItem(wrapper.getSlot());
        if (cosmeticItem == null || cosmeticItem.getType().isAir()) return PacketAction.NOTHING;
        wrapper.setItemStack(cosmeticItem);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeEquipmentContent(@NotNull PacketContext context, @NotNull EntityEquipmentWrapper wrapper) {
        CosmeticPacketSnapshot owner = CosmeticPacketSnapshots.get(context.worldId(), wrapper.getEntityId());
        if (owner == null || owner.inWardrobe()) return PacketAction.NOTHING;

        Map<EquipmentSlot, ItemStack> equipment = wrapper.getArmor();
        boolean changed = false;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : owner.equipmentItems().entrySet()) {
            if (!equipment.containsKey(entry.getKey())) continue;
            equipment.put(entry.getKey(), entry.getValue());
            changed = true;
        }
        if (equipment.containsKey(EquipmentSlot.HAND) && !owner.playerId().equals(context.playerId()) && !owner.invisible()) {
            ItemStack mainHand = owner.mainHand();
            if (mainHand != null) {
                equipment.put(EquipmentSlot.HAND, mainHand);
                changed = true;
            }
        }
        if (!changed) return PacketAction.NOTHING;

        wrapper.setArmor(equipment);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writePassengerContent(@NotNull PacketContext context, @NotNull PassengerWrapper wrapper) {
        CosmeticPacketSnapshot viewer = CosmeticPacketSnapshots.get(context.playerId());
        if (viewer == null || viewer.inWardrobe() || !viewer.interceptPassengerPackets()) return PacketAction.NOTHING;

        CosmeticPacketSnapshot owner = CosmeticPacketSnapshots.get(context.worldId(), wrapper.getOwner());
        if (owner == null || owner.backpackEntityId() < 0) return PacketAction.NOTHING;
        if (owner.playerId().equals(context.playerId()) && owner.firstPersonBackpack()) return PacketAction.NOTHING;

        List<Integer> passengers = new ArrayList<>(wrapper.getPassengers());
        if (passengers.contains(owner.backpackEntityId())) return PacketAction.NOTHING;
        passengers.addFirst(owner.backpackEntityId());
        wrapper.setPassengers(passengers);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction readPlayerScale(@NotNull PacketContext context, @NotNull PlayerScaleWrapper wrapper) {
        CosmeticPacketSnapshot owner = CosmeticPacketSnapshots.get(context.worldId(), wrapper.getEntityId());
        if (owner == null || owner.inWardrobe() || owner.backpackEntityIds().isEmpty()) return PacketAction.NOTHING;

        context.execute(HMCCosmeticsPlugin.getInstance(), () -> {
            Player viewer = HMCCosmeticsPlugin.getInstance().getServer().getPlayer(context.playerId());
            if (viewer == null || !viewer.isOnline()) return;
            for (int cosmeticId : owner.backpackEntityIds()) {
                HMCCPacketManager.sendEntityScalePacket(cosmeticId, wrapper.getScale(), List.of(viewer));
            }
        });
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readInventoryClick(@NotNull PacketContext context, @NotNull InventoryClickWrapper wrapper) {
        if (wrapper.getClickType() != 0 || wrapper.getSlotNumber() == -999) return PacketAction.NOTHING;

        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        CosmeticSlot slot = HMCCInventoryUtils.NMSCosmeticSlot(wrapper.getSlotNumber());
        if (snapshot == null || snapshot.inWardrobe() || slot == null || !snapshot.hasCosmetic(slot)) return PacketAction.NOTHING;

        context.execute(HMCCosmeticsPlugin.getInstance(), () -> {
            CosmeticUser user = CosmeticUsers.getUser(context.playerId());
            if (user != null) user.updateCosmetic(slot);
        }, null, 1L);
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerAction(@NotNull PacketContext context, @NotNull PlayerActionWrapper wrapper) {
        if (!"SWAP_ITEM_WITH_OFFHAND".equalsIgnoreCase(wrapper.getActionType())) return PacketAction.NOTHING;
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        if (snapshot == null || !snapshot.preventOffhandSwapping()) return PacketAction.NOTHING;
        return snapshot.hasCosmetic(CosmeticSlot.OFFHAND) ? PacketAction.CANCELLED : PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerArm(@NotNull PacketContext context, @NotNull PlayerSwingWrapper wrapper) {
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        if (snapshot == null || !snapshot.inWardrobe() || !snapshot.wardrobeRunning()) return PacketAction.NOTHING;
        reopenWardrobeMenu(context);
        return PacketAction.CANCELLED;
    }

    @Override
    public @NotNull PacketAction readEntityHandle(@NotNull PacketContext context, @NotNull PlayerInteractWrapper wrapper) {
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        return snapshot != null && snapshot.inWardrobe() ? PacketAction.CANCELLED : PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerInput(@NotNull PacketContext context, @NotNull PlayerInputWrapper wrapper) {
        if (!wrapper.jump()) return PacketAction.NOTHING;
        CosmeticPacketSnapshot snapshot = CosmeticPacketSnapshots.get(context.playerId());
        if (snapshot != null && snapshot.inWardrobe() && snapshot.wardrobeRunning()) reopenWardrobeMenu(context);
        return PacketAction.NOTHING;
    }

    private void reopenWardrobeMenu(PacketContext context) {
        context.execute(HMCCosmeticsPlugin.getInstance(), () -> {
            CosmeticUser user = CosmeticUsers.getUser(context.playerId());
            if (user == null || !user.isInWardrobe() || user.getWardrobeManager() == null) return;
            Menu menu = user.getWardrobeManager().getLastOpenMenu();
            if (menu != null) menu.openMenu(user);
        });
    }
}

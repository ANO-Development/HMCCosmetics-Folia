package com.hibiscusmc.hmccosmetics.database;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserData {

    @Getter
    private UUID owner;
    @Setter
    @Getter
    private HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics;
    @Getter
    private ArrayList<CosmeticUser.HiddenReason> hiddenReasons;

    public UserData(UUID owner) {
        this.owner = owner;
        this.cosmetics = new HashMap<>();
        this.hiddenReasons = new ArrayList<>();
    }

    public UserData(UserData source) {
        this.owner = source.owner;
        this.cosmetics = new HashMap<>(source.cosmetics);
        this.hiddenReasons = new ArrayList<>(source.hiddenReasons);
    }

    public static UserData snapshot(CosmeticUser user) {
        UserData data = new UserData(user.getUniqueId());
        for (Cosmetic cosmetic : user.getCosmetics()) {
            org.bukkit.Color color = user.getCosmeticColor(cosmetic.getSlot());
            data.addCosmetic(cosmetic.getSlot(), cosmetic, color == null ? -1 : color.asRGB());
        }
        user.getHiddenReasons().forEach(data::addHiddenReason);
        return data;
    }

    public void addCosmetic(CosmeticSlot slot, Cosmetic cosmetic, Integer color) {
        cosmetics.put(slot, Map.entry(cosmetic, color));
    }

    public void addHiddenReason(CosmeticUser.HiddenReason reason) {
        hiddenReasons.add(reason);
    }
}

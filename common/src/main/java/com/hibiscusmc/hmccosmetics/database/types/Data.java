package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public abstract class Data {

    public abstract void setup();

    public abstract void save(CosmeticUser user);

    public void save(UserData userData) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support immutable user snapshots.");
    }

    @Nullable
    public abstract CompletableFuture<UserData> get(UUID uniqueId);

    public abstract void clear(UUID uniqueId);

    public void close() {
    }

    // BACKPACK=colorfulbackpack&RRGGBB,HELMET=niftyhat,BALLOON=colorfulballoon,CHESTPLATE=niftychestplate
    @NotNull
    public final String serializeData(@NotNull CosmeticUser user) {
        return serializeData(UserData.snapshot(user));
    }

    @NotNull
    public final String serializeData(@NotNull UserData userData) {
        StringBuilder data = new StringBuilder();
        for (CosmeticUser.HiddenReason reason : userData.getHiddenReasons()) {
            if (!shouldHiddenSave(reason)) continue;
            appendEntry(data, "HIDDEN=" + reason);
        }
        for (Map.Entry<CosmeticSlot, Map.Entry<Cosmetic, Integer>> entry : userData.getCosmetics().entrySet()) {
            Cosmetic cosmetic = entry.getValue().getKey();
            int color = entry.getValue().getValue();
            String input = entry.getKey() + "=" + cosmetic.getId();
            if (color != -1) input = input + "&" + color;
            appendEntry(data, input);
        }
        return data.toString();
    }

    @NotNull
    public final HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> deserializeData(@NotNull String raw) {
        return deserializeUserData(UUID.randomUUID(), raw).getCosmetics();
    }

    @NotNull
    public final UserData deserializeUserData(@NotNull UUID owner, @Nullable String raw) {
        UserData userData = new UserData(owner);
        if (raw == null || raw.isBlank()) return userData;
        HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics = new HashMap<>();

        String[] rawData = raw.split(",");
        for (String a : rawData) {
            if (a == null || a.isBlank()) continue;
            String[] splitData = a.split("=", 2);
            if (splitData.length != 2 || splitData[0].isBlank() || splitData[1].isBlank()) continue;
            MessagesUtil.sendDebugMessages("First split (suppose slot) " + splitData[0]);
            if (splitData[0].equalsIgnoreCase("HIDDEN")) {
                if (!Settings.isForceShowOnJoin() && EnumUtils.isValidEnum(CosmeticUser.HiddenReason.class, splitData[1]))
                    userData.addHiddenReason(CosmeticUser.HiddenReason.valueOf(splitData[1]));
                continue;
            }

            CosmeticSlot slot = CosmeticSlot.valueOf(splitData[0]);
            if (slot == null) continue;
            Cosmetic cosmetic = null;
            int color = -1;
            if (splitData[1].contains("&")) {
                String[] colorSplitData = splitData[1].split("&", 2);
                if (colorSplitData.length != 2) continue;
                if (Cosmetics.hasCosmetic(colorSplitData[0])) cosmetic = Cosmetics.getCosmetic(colorSplitData[0]);
                if (cosmetic == null) continue;
                try {
                    color = Integer.parseInt(colorSplitData[1]);
                } catch (NumberFormatException exception) {
                    continue;
                }
            } else {
                if (Cosmetics.hasCosmetic(splitData[1])) cosmetic = Cosmetics.getCosmetic(splitData[1]);
            }
            if (cosmetic != null) cosmetics.put(slot, Map.entry(cosmetic, color));
        }

        userData.setCosmetics(cosmetics);
        return userData;
    }

    private boolean shouldHiddenSave(CosmeticUser.HiddenReason reason) {
        switch (reason) {
            case EMOTE, NONE, GAMEMODE, WORLD, DISABLED, POTION -> {
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    private void appendEntry(StringBuilder data, String entry) {
        if (!data.isEmpty()) data.append(',');
        data.append(entry);
    }
}

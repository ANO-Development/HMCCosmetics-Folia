package com.hibiscusmc.hmccosmetics.util;

import me.lojosho.shaded.configurate.ConfigurationNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TranslationUtil {

    // unlocked-cosmetic -> true -> True
    private static volatile Map<String, List<TranslationPair>> keys = Map.of();

    public static void setup(@NotNull ConfigurationNode config) {
        java.util.HashMap<String, List<TranslationPair>> loadedKeys = new java.util.HashMap<>();
        for (ConfigurationNode node : config.childrenMap().values()) {
            final ArrayList<TranslationPair> pairs = new ArrayList<>();
            for (ConfigurationNode translatableMessage : node.childrenMap().values()) {
                String key = translatableMessage.key().toString();
                key = key.replaceAll("'", ""); // Autoupdater adds ' to it? Removes it from the key
                TranslationPair pair = new TranslationPair(key, translatableMessage.getString());
                pairs.add(pair);
                MessagesUtil.sendDebugMessages("setupTranslation key:" + node.key().toString() + " | " + node);
                MessagesUtil.sendDebugMessages("Overall Key " + node.key().toString());
                MessagesUtil.sendDebugMessages("Key '" + pair.key() + "' Value '" + pair.value() + "'");
            }
            loadedKeys.put(node.key().toString().toLowerCase(), List.copyOf(pairs));
        }
        keys = Map.copyOf(loadedKeys);
    }

    public static String getTranslation(@NotNull String key, @NotNull String message) {
        List<TranslationPair> pairs = keys.get(key.toLowerCase());
        if (pairs == null) return message;
        for (TranslationPair pair : pairs) {
            if (pair.key().equals(message.toLowerCase())) return pair.value();
        }

        return message;
    }
}

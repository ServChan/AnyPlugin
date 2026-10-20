package org.lts.anyplugin.hooks;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;

public final class Papi {

    private static boolean enabled;

    private Papi() {
    }

    public static void register(AnyPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new PapiExpansion(plugin).register();
            enabled = true;
            plugin.getLogger().info("Registered PlaceholderAPI expansion %anyplugin_...%");
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not register PlaceholderAPI expansion", e);
        }
    }

    public static String apply(Player player, String text) {
        if (!enabled || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException | LinkageError e) {
            return text;
        }
    }
}

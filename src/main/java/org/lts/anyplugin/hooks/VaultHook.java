package org.lts.anyplugin.hooks;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;

public final class VaultHook {

    private VaultHook() {
    }

    public static boolean present() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }

    public static void register(AnyPlugin plugin) {
        if (!present()) {
            return;
        }
        try {
            VaultBridge.register(plugin);
            plugin.getLogger().info("Registered Vault permission and chat providers");
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not register Vault providers", e);
        }
    }

    public static void unregister(AnyPlugin plugin) {
        if (present()) {
            try {
                VaultBridge.unregister(plugin);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
    }

    public static boolean available() {
        if (!present()) {
            return false;
        }
        try {
            return VaultBridge.externalChat() != null;
        } catch (LinkageError e) {
            return false;
        }
    }

    public static String prefix(Player player) {
        try {
            return VaultBridge.externalChat().getPlayerPrefix(player);
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    public static String suffix(Player player) {
        try {
            return VaultBridge.externalChat().getPlayerSuffix(player);
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    public static String primaryGroup(Player player) {
        try {
            return VaultBridge.externalChat().getPrimaryGroup(player);
        } catch (RuntimeException | LinkageError e) {
            return "default";
        }
    }
}

package org.lts.anyplugin.hooks;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.lts.anyplugin.AnyPlugin;

final class VaultBridge {

    private static VaultPermission permission;
    private static VaultChat chat;

    private VaultBridge() {
    }

    static void register(AnyPlugin plugin) {
        permission = new VaultPermission(plugin);
        chat = new VaultChat(plugin, permission);
        Bukkit.getServicesManager().register(Permission.class, permission, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(Chat.class, chat, plugin, ServicePriority.High);
    }

    static void unregister(AnyPlugin plugin) {
        if (permission != null) {
            Bukkit.getServicesManager().unregister(Permission.class, permission);
        }
        if (chat != null) {
            Bukkit.getServicesManager().unregister(Chat.class, chat);
        }
    }

    static Chat externalChat() {
        RegisteredServiceProvider<Chat> provider = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (provider == null || provider.getProvider() instanceof VaultChat) {
            return null;
        }
        return provider.getProvider();
    }
}

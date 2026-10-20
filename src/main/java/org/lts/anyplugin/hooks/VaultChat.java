package org.lts.anyplugin.hooks;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.PlayerData;

final class VaultChat extends Chat {

    private final AnyPlugin plugin;

    VaultChat(AnyPlugin plugin, Permission permission) {
        super(permission);
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "AnyPlugin";
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    private Player online(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    @Override
    public String getPlayerPrefix(String world, String player) {
        Player p = online(player);
        return p == null ? "" : plugin.perms().prefix(p);
    }

    @Override
    public void setPlayerPrefix(String world, String player, String prefix) {
        setMeta(player, "prefix", prefix);
    }

    @Override
    public String getPlayerSuffix(String world, String player) {
        Player p = online(player);
        return p == null ? "" : plugin.perms().suffix(p);
    }

    @Override
    public void setPlayerSuffix(String world, String player, String suffix) {
        setMeta(player, "suffix", suffix);
    }

    private void setMeta(String player, String kind, String value) {
        Player p = online(player);
        if (p == null) {
            return;
        }
        PlayerData data = plugin.players().data(p);
        if (kind.equals("prefix")) {
            data.prefix = value;
        } else {
            data.suffix = value;
        }
        plugin.players().setText(p.getUniqueId(), kind, value);
        plugin.perms().applyLater(p.getUniqueId());
    }

    @Override
    public String getGroupPrefix(String world, String group) {
        Group g = plugin.perms().groups().get(group);
        return g == null ? "" : g.prefix;
    }

    @Override
    public void setGroupPrefix(String world, String group, String prefix) {
        Group g = plugin.perms().groups().get(group);
        if (g != null) {
            g.prefix = prefix == null ? "" : prefix;
            plugin.perms().groups().save();
            plugin.perms().applyAll();
        }
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        Group g = plugin.perms().groups().get(group);
        return g == null ? "" : g.suffix;
    }

    @Override
    public void setGroupSuffix(String world, String group, String suffix) {
        Group g = plugin.perms().groups().get(group);
        if (g != null) {
            g.suffix = suffix == null ? "" : suffix;
            plugin.perms().groups().save();
            plugin.perms().applyAll();
        }
    }

    @Override
    public int getPlayerInfoInteger(String world, String player, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoInteger(String world, String player, String node, int value) {
    }

    @Override
    public int getGroupInfoInteger(String world, String group, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoInteger(String world, String group, String node, int value) {
    }

    @Override
    public double getPlayerInfoDouble(String world, String player, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoDouble(String world, String player, String node, double value) {
    }

    @Override
    public double getGroupInfoDouble(String world, String group, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoDouble(String world, String group, String node, double value) {
    }

    @Override
    public boolean getPlayerInfoBoolean(String world, String player, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoBoolean(String world, String player, String node, boolean value) {
    }

    @Override
    public boolean getGroupInfoBoolean(String world, String group, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoBoolean(String world, String group, String node, boolean value) {
    }

    @Override
    public String getPlayerInfoString(String world, String player, String node, String defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoString(String world, String player, String node, String value) {
    }

    @Override
    public String getGroupInfoString(String world, String group, String node, String defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoString(String world, String group, String node, String value) {
    }
}

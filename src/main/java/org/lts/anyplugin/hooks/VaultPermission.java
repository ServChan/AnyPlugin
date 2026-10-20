package org.lts.anyplugin.hooks;

import java.util.Locale;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.Node;
import org.lts.anyplugin.player.PlayerData;

final class VaultPermission extends Permission {

    private final AnyPlugin anyPlugin;

    VaultPermission(AnyPlugin plugin) {
        this.anyPlugin = plugin;
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "AnyPlugin";
    }

    @Override
    public boolean isEnabled() {
        return anyPlugin.isEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    private static Player online(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    @Override
    public boolean playerHas(String world, String player, String permission) {
        Player p = online(player);
        return p != null && p.hasPermission(permission);
    }

    @Override
    public boolean playerAdd(String world, String player, String permission) {
        Player p = online(player);
        if (p == null) {
            return false;
        }
        anyPlugin.perms().setNode(p.getUniqueId(), new Node(Node.PERM, permission.toLowerCase(Locale.ROOT), true, null));
        return true;
    }

    @Override
    public boolean playerRemove(String world, String player, String permission) {
        Player p = online(player);
        if (p == null) {
            return false;
        }
        anyPlugin.perms().unsetNode(p.getUniqueId(), Node.PERM, permission.toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public boolean groupHas(String world, String group, String permission) {
        Group g = anyPlugin.perms().groups().get(group);
        return g != null && Boolean.TRUE.equals(g.permissions.get(permission.toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean groupAdd(String world, String group, String permission) {
        Group g = anyPlugin.perms().groups().get(group);
        if (g == null) {
            return false;
        }
        g.permissions.put(permission.toLowerCase(Locale.ROOT), true);
        anyPlugin.perms().groups().save();
        anyPlugin.perms().applyAll();
        return true;
    }

    @Override
    public boolean groupRemove(String world, String group, String permission) {
        Group g = anyPlugin.perms().groups().get(group);
        if (g == null) {
            return false;
        }
        g.permissions.remove(permission.toLowerCase(Locale.ROOT));
        anyPlugin.perms().groups().save();
        anyPlugin.perms().applyAll();
        return true;
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        Player p = online(player);
        if (p == null) {
            return false;
        }
        PlayerData data = anyPlugin.players().data(p);
        return anyPlugin.perms().groupNames(data).contains(group.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        Player p = online(player);
        Group g = anyPlugin.perms().groups().get(group);
        if (p == null || g == null) {
            return false;
        }
        anyPlugin.perms().setNode(p.getUniqueId(), new Node(Node.GROUP, g.name, true, null));
        return true;
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        Player p = online(player);
        if (p == null) {
            return false;
        }
        anyPlugin.perms().unsetNode(p.getUniqueId(), Node.GROUP, group.toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        Player p = online(player);
        if (p == null) {
            return new String[] {anyPlugin.perms().groups().defaultGroup().name};
        }
        return anyPlugin.perms().groupNames(anyPlugin.players().data(p)).toArray(String[]::new);
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        Player p = online(player);
        if (p == null) {
            return anyPlugin.perms().groups().defaultGroup().name;
        }
        return anyPlugin.perms().primary(p).name;
    }

    @Override
    public String[] getGroups() {
        return anyPlugin.perms().groups().names().toArray(String[]::new);
    }
}

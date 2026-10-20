package org.lts.anyplugin.hooks;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.player.PlayerData;

final class PapiExpansion extends PlaceholderExpansion {

    private final AnyPlugin plugin;

    PapiExpansion(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "anyplugin";
    }

    @Override
    public String getAuthor() {
        return "LTS";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("online")) {
            return String.valueOf(plugin.players().visibleCount());
        }
        if (key.equals("unique")) {
            return String.valueOf(plugin.players().uniquePlayers());
        }
        if (!(offline instanceof Player player)) {
            return "";
        }
        PlayerData data = plugin.players().data(player);
        return switch (key) {
            case "prefix" -> Text.legacyToMini(plugin.perms().prefix(player));
            case "suffix" -> Text.legacyToMini(plugin.perms().suffix(player));
            case "group" -> plugin.perms().primary(player).name;
            case "nick" -> data.nick == null ? player.getName() : Text.stripTags(data.nick);
            case "afk" -> data.afk ? plugin.msg().raw("papi.afk") : "";
            case "afk_bool" -> String.valueOf(data.afk);
            case "vanished" -> String.valueOf(data.vanished);
            case "playtime" -> plugin.msg().duration(data.totalPlaytime());
            case "playtime_hours" -> String.valueOf(data.totalPlaytime() / 3_600_000L);
            case "homes" -> String.valueOf(data.homes.size());
            case "muted" -> String.valueOf(data.muted());
            case "god" -> String.valueOf(data.god);
            default -> null;
        };
    }
}

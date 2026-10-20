package org.lts.anyplugin.tab;

import io.papermc.paper.adventure.PaperAdventure;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;

public final class NametagService {

    private record Tag(String team, Component prefix, Component suffix, String color) {
    }

    private final AnyPlugin plugin;
    private final Scoreboard board = new Scoreboard();
    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private volatile boolean broken;

    public NametagService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, Component prefix, Component suffix, String color, int weight) {
        if (broken) {
            return;
        }
        String team = teamName(player.getUniqueId(), weight);
        Tag tag = new Tag(team, prefix, suffix, color);
        Tag old = tags.put(player.getUniqueId(), tag);
        try {
            if (old != null && !old.team().equals(team)) {
                broadcast(removePacket(old.team()));
                broadcast(addPacket(player.getName(), tag));
            } else if (old == null) {
                broadcast(addPacket(player.getName(), tag));
            } else if (!old.equals(tag)) {
                broadcast(modifyPacket(player.getName(), tag));
            }
        } catch (LinkageError | RuntimeException e) {
            disable(e);
        }
    }

    public void sendAllTo(Player viewer) {
        if (broken) {
            return;
        }
        try {
            for (Map.Entry<UUID, Tag> entry : tags.entrySet()) {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner != null && !owner.equals(viewer)) {
                    send(viewer, addPacket(owner.getName(), entry.getValue()));
                }
            }
        } catch (LinkageError | RuntimeException e) {
            disable(e);
        }
    }

    public void remove(Player player) {
        Tag old = tags.remove(player.getUniqueId());
        if (old == null || broken) {
            return;
        }
        try {
            ClientboundSetPlayerTeamPacket packet = removePacket(old.team());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    send(viewer, packet);
                }
            }
        } catch (LinkageError | RuntimeException e) {
            disable(e);
        }
    }

    public void clear() {
        if (broken) {
            tags.clear();
            return;
        }
        try {
            for (Tag tag : tags.values()) {
                broadcast(removePacket(tag.team()));
            }
        } catch (LinkageError | RuntimeException ignored) {
        }
        tags.clear();
    }

    private void disable(Throwable e) {
        broken = true;
        plugin.getLogger().log(Level.WARNING, "Name tags disabled: this server build does not match the packet code", e);
    }

    private static String teamName(UUID uuid, int weight) {
        int order = Math.max(0, Math.min(999, 500 - weight));
        return String.format(Locale.ROOT, "ap%03d%s", order, uuid.toString().replace("-", "").substring(0, 11));
    }

    private PlayerTeam team(String playerName, Tag tag) {
        PlayerTeam team = new PlayerTeam(board, tag.team());
        team.setPlayerPrefix(PaperAdventure.asVanilla(tag.prefix()));
        team.setPlayerSuffix(PaperAdventure.asVanilla(tag.suffix()));
        TeamColor color = null;
        if (tag.color() != null && !tag.color().isEmpty()) {
            try {
                color = TeamColor.valueOf(tag.color().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        team.setColor(Optional.ofNullable(color));
        team.getPlayers().add(playerName);
        return team;
    }

    private ClientboundSetPlayerTeamPacket addPacket(String playerName, Tag tag) {
        return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(playerName, tag), true);
    }

    private ClientboundSetPlayerTeamPacket modifyPacket(String playerName, Tag tag) {
        return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(playerName, tag), false);
    }

    private ClientboundSetPlayerTeamPacket removePacket(String teamName) {
        return ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(board, teamName));
    }

    private void broadcast(ClientboundSetPlayerTeamPacket packet) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            send(viewer, packet);
        }
    }

    private static void send(Player viewer, ClientboundSetPlayerTeamPacket packet) {
        if (viewer instanceof CraftPlayer craft && craft.getHandle().connection != null) {
            craft.getHandle().connection.send(packet);
        }
    }
}

package org.lts.anyplugin.player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Location;
import org.lts.anyplugin.moderation.Punishment;

public final class PlayerData {

    public final UUID uuid;
    public volatile String name;
    public volatile String nick;
    public volatile String prefix;
    public volatile String suffix;
    public volatile long firstJoin;
    public volatile long lastSeen;
    public volatile long playtime;
    public volatile long sessionStart;
    public volatile String lastIp;
    public volatile boolean isNew;

    public volatile boolean vanished;
    public volatile boolean god;
    public volatile boolean fly;
    public volatile boolean tpToggle;
    public volatile boolean msgToggle;
    public volatile boolean socialSpy;
    public volatile boolean commandSpy;
    public volatile boolean kitReceived;

    public final Map<String, Home> homes = new ConcurrentHashMap<>();
    public final Set<UUID> ignores = ConcurrentHashMap.newKeySet();
    public final List<Node> nodes = new CopyOnWriteArrayList<>();
    public volatile Punishment mute;

    public volatile long lastActivity = System.currentTimeMillis();
    public volatile boolean afk;
    public volatile Location back;
    public volatile UUID lastMessaged;
    public volatile long lastChat;
    public volatile String lastChatText = "";
    public volatile boolean staffChat;
    public volatile boolean frozen;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public long totalPlaytime() {
        long session = sessionStart > 0 ? System.currentTimeMillis() - sessionStart : 0;
        return playtime + session;
    }

    public boolean muted() {
        Punishment current = mute;
        if (current == null) {
            return false;
        }
        if (current.expired(System.currentTimeMillis())) {
            mute = null;
            return false;
        }
        return true;
    }
}

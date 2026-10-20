package org.lts.anyplugin.player;

import java.util.UUID;

public record OfflineRecord(UUID uuid, String name, String nick, long firstJoin, long lastSeen, long playtime, String lastIp) {
}

package org.lts.anyplugin.moderation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import org.lts.anyplugin.storage.Database;

public record Punishment(long id, UUID uuid, String name, String ip, Type type, String reason, String actor, long created,
                         Long expires, boolean active, String removedBy, Long removedAt) {

    public enum Type {
        BAN, IPBAN, MUTE, WARN, KICK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public boolean expired(long now) {
        return expires != null && expires <= now;
    }

    public boolean inEffect(long now) {
        return active && !expired(now);
    }

    public static Punishment read(ResultSet rs) throws SQLException {
        String uuid = rs.getString("uuid");
        return new Punishment(
                rs.getLong("id"),
                uuid == null ? null : UUID.fromString(uuid),
                rs.getString("name"),
                rs.getString("ip"),
                Type.valueOf(rs.getString("type").toUpperCase(Locale.ROOT)),
                rs.getString("reason"),
                rs.getString("actor"),
                rs.getLong("created"),
                Database.nullableLong(rs, "expires"),
                rs.getInt("active") == 1,
                rs.getString("removed_by"),
                Database.nullableLong(rs, "removed_at"));
    }
}

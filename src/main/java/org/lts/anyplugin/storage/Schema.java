package org.lts.anyplugin.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

final class Schema {

    private static final List<String[]> MIGRATIONS = List.<String[]>of(
            new String[] {
                    """
                    CREATE TABLE players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        name_lower TEXT NOT NULL,
                        nick TEXT,
                        prefix TEXT,
                        suffix TEXT,
                        first_join INTEGER NOT NULL,
                        last_seen INTEGER NOT NULL,
                        playtime INTEGER NOT NULL DEFAULT 0,
                        last_ip TEXT,
                        vanished INTEGER NOT NULL DEFAULT 0,
                        god INTEGER NOT NULL DEFAULT 0,
                        fly INTEGER NOT NULL DEFAULT 0,
                        tptoggle INTEGER NOT NULL DEFAULT 0,
                        msgtoggle INTEGER NOT NULL DEFAULT 0,
                        socialspy INTEGER NOT NULL DEFAULT 0,
                        commandspy INTEGER NOT NULL DEFAULT 0,
                        kit_received INTEGER NOT NULL DEFAULT 0
                    )
                    """,
                    "CREATE INDEX players_name ON players(name_lower)",
                    "CREATE TABLE ignores (uuid TEXT NOT NULL, ignored TEXT NOT NULL, PRIMARY KEY (uuid, ignored))",
                    """
                    CREATE TABLE homes (
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        yaw REAL NOT NULL, pitch REAL NOT NULL,
                        PRIMARY KEY (uuid, name)
                    )
                    """,
                    """
                    CREATE TABLE warps (
                        name TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        yaw REAL NOT NULL, pitch REAL NOT NULL,
                        icon TEXT,
                        description TEXT,
                        created_by TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """,
                    """
                    CREATE TABLE user_nodes (
                        uuid TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        node TEXT NOT NULL,
                        value INTEGER NOT NULL,
                        expires INTEGER,
                        PRIMARY KEY (uuid, kind, node)
                    )
                    """,
                    """
                    CREATE TABLE punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT,
                        name TEXT,
                        ip TEXT,
                        type TEXT NOT NULL,
                        reason TEXT,
                        actor TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        expires INTEGER,
                        active INTEGER NOT NULL DEFAULT 1,
                        removed_by TEXT,
                        removed_at INTEGER
                    )
                    """,
                    "CREATE INDEX punishments_uuid ON punishments(uuid)",
                    "CREATE INDEX punishments_ip ON punishments(ip)",
                    """
                    CREATE TABLE reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        reporter TEXT NOT NULL,
                        reporter_name TEXT NOT NULL,
                        target TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        location TEXT,
                        created INTEGER NOT NULL,
                        closed_by TEXT,
                        closed_at INTEGER
                    )
                    """,
                    "CREATE TABLE staff_log (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL)",
                    "CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT)"
            }
    );

    private Schema() {
    }

    static void migrate(Connection connection, Logger logger) throws SQLException {
        int version;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        for (int i = version; i < MIGRATIONS.size(); i++) {
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                for (String sql : MIGRATIONS.get(i)) {
                    st.execute(sql);
                }
                st.execute("PRAGMA user_version = " + (i + 1));
                connection.commit();
                logger.info("Database migrated to version " + (i + 1));
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}

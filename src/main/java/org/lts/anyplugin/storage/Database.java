package org.lts.anyplugin.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {

    @FunctionalInterface
    public interface SqlCall<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface Row<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AnyPlugin-SQLite");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public Database(Logger logger) {
        this.logger = logger;
    }

    public void open(File file) throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA foreign_keys=ON");
                    st.execute("PRAGMA busy_timeout=5000");
                }
                Schema.migrate(connection, logger);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).get(30, TimeUnit.SECONDS);
    }

    public <T> CompletableFuture<T> call(SqlCall<T> call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call.run(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Integer> update(String sql, Object... args) {
        return call(c -> update(c, sql, args)).exceptionally(e -> {
            logger.log(Level.WARNING, "SQL update failed: " + sql, e);
            return 0;
        });
    }

    public CompletableFuture<Long> insert(String sql, Object... args) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, args);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    public <T> CompletableFuture<List<T>> list(String sql, Row<T> row, Object... args) {
        return call(c -> list(c, sql, row, args));
    }

    public <T> CompletableFuture<Optional<T>> one(String sql, Row<T> row, Object... args) {
        return call(c -> {
            List<T> rows = list(c, sql, row, args);
            return rows.isEmpty() ? Optional.<T>empty() : Optional.of(rows.get(0));
        });
    }

    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    public static <T> List<T> list(Connection c, String sql, Row<T> row, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(row.map(rs));
                }
                return out;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (value instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Database queue did not drain in 15 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not close database", e);
        }
    }
}

package io.axor.persistence.jdbc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigOrigin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.axor.runtime.Serde;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;

public class JdbcStore {
    private static final Map<ConfigOrigin, JdbcStore> STORE_CACHE = new HashMap<>();

    private final DataSource dataSource;
    private final String ddl;
    private final String query;
    private final String queryForUpdate;
    private final String insert;
    private final String upsert;
    private final String update;
    private final String delete;

    private JdbcStore(Config config) {
        String tableName = config.getString("storeTableName");
        Properties properties = new Properties();
        for (var e : config.getConfig("properties").entrySet()) {
            properties.put(e.getKey(), e.getValue().unwrapped().toString());
        }
        HikariConfig hikariConfig = new HikariConfig(properties);
        hikariConfig.setAutoCommit(true);
        this.dataSource = new HikariDataSource(hikariConfig);
        String dialect = "default";
        if (hikariConfig.getJdbcUrl().startsWith("jdbc:mysql")) {
            dialect = "mysql";
        } else if (hikariConfig.getJdbcUrl().startsWith("jdbc:postgresql")) {
            dialect = "postgresql";
        }
        Config dConfig = config.getConfig("dbDialect." + dialect);
        this.ddl = MessageFormat.format(dConfig.getString("ddl"), tableName);
        this.query = MessageFormat.format(dConfig.getString("query"), tableName);
        this.queryForUpdate =
                MessageFormat.format(dConfig.getString("queryForUpdate"), tableName);
        this.insert = MessageFormat.format(dConfig.getString("insert"), tableName);
        this.upsert = MessageFormat.format(dConfig.getString("upsert"), tableName);
        this.update = MessageFormat.format(dConfig.getString("update"), tableName);
        this.delete = MessageFormat.format(dConfig.getString("delete"), tableName);
        autoCreateTable();
    }

    public static JdbcStore get(Config config) {
        return STORE_CACHE.computeIfAbsent(config.origin(), k -> new JdbcStore(config));
    }

    public <K, V> JdbcStoreInstance<K, V> getInstance(String name,
                                                      Serde<K> keySerde,
                                                      Serde<V> valueSerde,
                                                      boolean multiWriter) {
        return new JdbcStoreInstanceImpl<>(name, keySerde, valueSerde, multiWriter);
    }

    private void autoCreateTable() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(ddl)) {
            stmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public class JdbcStoreInstanceImpl<K, V> implements JdbcStoreInstance<K, V> {
        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;
        private final boolean txnEnabled;

        public JdbcStoreInstanceImpl(String name, Serde<K> keySerde,
                                     Serde<V> valueSerde, boolean txnEnabled) {
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
            this.txnEnabled = txnEnabled;
        }

        private Stmts newStmts() throws SQLException {
            return new Stmts();
        }

        @Override
        public @Nullable V get(K key) {
            try (var stmts = newStmts()) {
                V ret = stmts.doQuery(key);
                stmts.setSuccess();
                return ret;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void upsert(K key, @Nullable V value) {
            if (value == null) {
                delete(key);
                return;
            }
            try (var stmts = newStmts()) {
                stmts.doUpsert(key, value);
                stmts.setSuccess();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void upsert(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc) {
            getAndUpsert(key, valueFunc);
        }

        @Override
        public @Nullable V getAndUpsert(K key, @Nullable V value) {
            if (value == null) {
                return getAndDelete(key);
            }
            try (var stmts = newStmts()) {
                V prev = stmts.doQueryForUpdate(key);
                if (prev == null) {
                    stmts.doInsert(key, value);
                } else {
                    stmts.doUpdate(key, value);
                }
                stmts.setSuccess();
                return prev;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public @Nullable V getAndUpsert(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc) {
            try (var stmts = newStmts()) {
                V prev = stmts.doQueryForUpdate(key);
                V now = valueFunc.apply(key, prev);
                if (prev == null) {
                    if (now != null) {
                        stmts.doInsert(key, now);
                    }
                } else {
                    if (now == null) {
                        stmts.doDelete(key);
                    } else if (!now.equals(prev)) {
                        stmts.doUpdate(key, now);
                    }
                }
                stmts.setSuccess();
                return prev;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public @Nullable V upsertAndGet(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc) {
            try (var stmts = newStmts()) {
                V prev = stmts.doQueryForUpdate(key);
                V now = valueFunc.apply(key, prev);
                if (prev == null) {
                    if (now != null) {
                        stmts.doInsert(key, now);
                    }
                } else {
                    if (now == null) {
                        stmts.doDelete(key);
                    } else if (!now.equals(prev)) {
                        stmts.doUpdate(key, now);
                    }
                }
                stmts.setSuccess();
                return now;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void delete(K key) {
            try (var stmts = newStmts()) {
                stmts.doDelete(key);
                stmts.setSuccess();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public @Nullable V getAndDelete(K key) {
            return getAndUpsert(key, (k, v) -> null);
        }

        @Override
        public List<@Nullable V> getBatch(List<K> keys) {
            try (var stmts = newStmts()) {
                List<V> list = new ArrayList<>();
                for (K key : keys) {
                    list.add(stmts.doQuery(key));
                }
                stmts.setSuccess();
                return list;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void upsertBatch(List<K> keys, List<@Nullable V> values) {
            try (var stmts = newStmts()) {
                for (int i = 0, l = keys.size(); i < l; i++) {
                    K key = keys.get(i);
                    V value = values.get(i);
                    if (value == null) {
                        stmts.doDelete(key);
                    } else {
                        stmts.doUpsert(key, value);
                    }
                }
                stmts.setSuccess();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void upsertBatch(List<K> keys, BiFunction<K, @Nullable V, @Nullable V> valueFunc) {
            try (var stmts = newStmts()) {
                for (K key : keys) {
                    V prev = stmts.doQuery(key);
                    V now = valueFunc.apply(key, prev);
                    if (prev == null) {
                        if (now != null) {
                            stmts.doInsert(key, now);
                        }
                    } else {
                        if (now == null) {
                            stmts.doDelete(key);
                        } else if (!now.equals(prev)) {
                            stmts.doUpdate(key, now);
                        }
                    }
                }
                stmts.setSuccess();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<@Nullable V> getAndUpsertBatch(List<K> keys, List<@Nullable V> values) {
            try (var stmts = newStmts()) {
                List<V> list = new ArrayList<>(keys.size());
                for (int i = 0, l = keys.size(); i < l; i++) {
                    K key = keys.get(i);
                    list.add(stmts.doQueryForUpdate(key));
                    V value = values.get(i);
                    if (value == null) {
                        stmts.doDelete(key);
                    } else {
                        stmts.doUpsert(key, value);
                    }
                }
                stmts.setSuccess();
                return list;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<@Nullable V> getAndUpsertBatch(List<K> keys, BiFunction<K, @Nullable V,
                @Nullable V> valueFunc) {
            try (var stmts = newStmts()) {
                List<V> list = new ArrayList<>(keys.size());
                for (K key : keys) {
                    V prev = stmts.doQueryForUpdate(key);
                    list.add(prev);
                    V value = valueFunc.apply(key, prev);
                    if (value == null) {
                        stmts.doDelete(key);
                    } else {
                        stmts.doUpsert(key, value);
                    }
                }
                stmts.setSuccess();
                return list;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<@Nullable V> upsertAndGetBatch(List<K> keys, BiFunction<K, @Nullable V,
                @Nullable V> valueFunc) {
            try (var stmts = newStmts()) {
                List<V> list = new ArrayList<>(keys.size());
                for (K key : keys) {
                    V prev = stmts.doQueryForUpdate(key);
                    V value = valueFunc.apply(key, prev);
                    list.add(value);
                    if (value == null) {
                        stmts.doDelete(key);
                    } else {
                        stmts.doUpsert(key, value);
                    }
                }
                stmts.setSuccess();
                return list;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void deleteBatch(List<K> keys) {
            try (var stmts = newStmts()) {
                for (K key : keys) {
                    stmts.doDelete(key);
                }
                stmts.setSuccess();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<@Nullable V> getAndDeleteBatch(List<K> keys) {
            try (var stmts = newStmts()) {
                List<V> list = new ArrayList<>(keys.size());
                for (K key : keys) {
                    V prev = stmts.doQueryForUpdate(key);
                    list.add(prev);
                    stmts.doDelete(key);
                }
                stmts.setSuccess();
                return list;
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        private class Stmts implements AutoCloseable {
            private final Connection conn;
            private boolean success;
            private PreparedStatement query;
            private PreparedStatement insert;
            private PreparedStatement upsert;
            private PreparedStatement update;
            private PreparedStatement delete;

            private Stmts() throws SQLException {
                this.conn = dataSource.getConnection();
                conn.setAutoCommit(!txnEnabled);
            }

            private V doQuery(K key) throws SQLException, IOException {
                var stmt = query = conn.prepareStatement(JdbcStore.this.query);
                stmt.setString(1, name);
                stmt.setBinaryStream(2, keySerde.serialize(key));
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? valueSerde.deserialize(rs.getBinaryStream(1)) : null;
                }
            }

            private V doQueryForUpdate(K key) throws SQLException, IOException {
                if (txnEnabled) {
                    var stmt = query = conn.prepareStatement(JdbcStore.this.queryForUpdate);
                    stmt.setString(1, name);
                    stmt.setBinaryStream(2, keySerde.serialize(key));
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? valueSerde.deserialize(rs.getBinaryStream(1)) : null;
                    }
                } else {
                    return doQuery(key);
                }
            }

            private void doInsert(K key, @NotNull V value) throws SQLException, IOException {
                var stmt = insert = conn.prepareStatement(JdbcStore.this.insert);
                stmt.setString(1, name);
                stmt.setBinaryStream(2, keySerde.serialize(key));
                stmt.setBinaryStream(3, valueSerde.serialize(value));
                stmt.execute();
            }

            private void doUpsert(K key, @NotNull V value) throws SQLException, IOException {
                var stmt = upsert = conn.prepareStatement(JdbcStore.this.upsert);
                stmt.setString(1, name);
                stmt.setBinaryStream(2, keySerde.serialize(key));
                stmt.setBinaryStream(3, valueSerde.serialize(value));
                stmt.setBinaryStream(4, valueSerde.serialize(value));
                stmt.execute();
            }

            private void doUpdate(K key, @NotNull V value) throws SQLException, IOException {
                var stmt = update = conn.prepareStatement(JdbcStore.this.update);
                stmt.setBinaryStream(1, valueSerde.serialize(value));
                stmt.setString(2, name);
                stmt.setBinaryStream(3, keySerde.serialize(key));
                stmt.execute();
            }

            private void doDelete(K key) throws SQLException, IOException {
                var stmt = delete = conn.prepareStatement(JdbcStore.this.delete);
                stmt.setString(1, name);
                stmt.setBinaryStream(2, keySerde.serialize(key));
                stmt.execute();
            }

            public void setSuccess() {
                success = true;
            }

            public void close() throws SQLException {
                if (txnEnabled)
                    if (success) {
                        conn.commit();
                    } else {
                        conn.rollback();
                    }
                if (query != null) {
                    query.close();
                }
                if (insert != null) {
                    insert.close();
                }
                if (upsert != null) {
                    upsert.close();
                }
                if (update != null) {
                    update.close();
                }
                if (delete != null) {
                    delete.close();
                }
                conn.close();
            }
        }
    }
}

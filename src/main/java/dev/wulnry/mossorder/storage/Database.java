package dev.wulnry.mossorder.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * HikariCP tabanlı veritabanı bağlantı havuzu. SQLite ve MySQL destekler.
 */
public final class Database {

    private final MossOrder plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public Database(MossOrder plugin) {
        this.plugin = plugin;
    }

    /**
     * Yapılandırmaya göre bağlantı havuzunu kurar.
     *
     * @throws SQLException bağlantı kurulamazsa
     */
    public void connect() throws SQLException {
        PluginConfig cfg = plugin.config();
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("MossOrder-Pool");
        hikari.setMinimumIdle(cfg.poolMinIdle());
        hikari.setConnectionTimeout(cfg.poolConnectionTimeout());

        if (cfg.storageType() == PluginConfig.StorageType.MYSQL) {
            this.mysql = true;
            hikari.setMaximumPoolSize(cfg.poolMaxSize());
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            StringBuilder url = new StringBuilder("jdbc:mysql://")
                    .append(cfg.mysqlHost()).append(':').append(cfg.mysqlPort())
                    .append('/').append(cfg.mysqlDatabase())
                    .append("?useSSL=").append(cfg.mysqlUseSsl());
            List<String> props = cfg.mysqlProperties();
            if (props != null) {
                for (String property : props) {
                    if (property != null && !property.isBlank()) {
                        url.append('&').append(property);
                    }
                }
            }
            hikari.setJdbcUrl(url.toString());
            hikari.setUsername(cfg.mysqlUsername());
            hikari.setPassword(cfg.mysqlPassword());
        } else {
            this.mysql = false;
            // SQLite tek yazıcıdır; havuzu küçük tut ve kilitlenmeyi bekleyerek çöz.
            hikari.setMaximumPoolSize(Math.min(cfg.poolMaxSize(), 4));
            hikari.setDriverClassName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), cfg.sqliteFile());
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setConnectionInitSql("PRAGMA busy_timeout = 5000;");
            hikari.addDataSourceProperty("journal_mode", "WAL");
        }

        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isMySql() {
        return mysql;
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

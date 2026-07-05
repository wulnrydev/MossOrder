package dev.wulnry.mossorder.config;

import dev.wulnry.mossorder.MossOrder;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * config.yml içeriğini okuyan ve tür güvenli erişim sağlayan sarmalayıcı.
 */
public final class PluginConfig {

    public enum StorageType {SQLITE, MYSQL}

    public enum InputMethod {SIGN, CHAT, AUTO}

    private final MossOrder plugin;

    // Veritabanı
    private StorageType storageType;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private boolean mysqlUseSsl;
    private List<String> mysqlProperties;
    private int poolMaxSize;
    private int poolMinIdle;
    private long poolConnectionTimeout;

    // Genel
    private int cacheSyncIntervalSeconds;
    private int maxOrdersPerPlayer;
    private int maxAmountPerOrder;
    private double minUnitPrice;
    private double maxUnitPrice;
    private double creationTax;
    private String moneyFormat;

    // Giriş
    private InputMethod inputMethod;
    private String chatCancelWord;
    private int inputTimeoutSeconds;

    public PluginConfig(MossOrder plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        this.storageType = parseEnum(StorageType.class, c.getString("database.type", "SQLITE"), StorageType.SQLITE);
        this.sqliteFile = c.getString("database.sqlite-file", "data.db");
        this.mysqlHost = c.getString("database.mysql.host", "127.0.0.1");
        this.mysqlPort = c.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = c.getString("database.mysql.database", "mossorder");
        this.mysqlUsername = c.getString("database.mysql.username", "root");
        this.mysqlPassword = c.getString("database.mysql.password", "");
        this.mysqlUseSsl = c.getBoolean("database.mysql.useSSL", false);
        this.mysqlProperties = c.getStringList("database.mysql.properties");
        this.poolMaxSize = Math.max(1, c.getInt("database.pool.maximum-pool-size", 4));
        this.poolMinIdle = Math.max(1, c.getInt("database.pool.minimum-idle", 1));
        this.poolConnectionTimeout = Math.max(2000L, c.getLong("database.pool.connection-timeout-ms", 8000));

        this.cacheSyncIntervalSeconds = Math.max(0, c.getInt("settings.cache-sync-interval-seconds", 0));
        this.maxOrdersPerPlayer = Math.max(0, c.getInt("settings.max-orders-per-player", 10));
        this.maxAmountPerOrder = Math.max(1, c.getInt("settings.max-amount-per-order", 100000));
        this.minUnitPrice = Math.max(0.0001, c.getDouble("settings.min-unit-price", 0.01));
        this.maxUnitPrice = c.getDouble("settings.max-unit-price", 10_000_000.0);
        this.creationTax = Math.max(0.0, Math.min(1.0, c.getDouble("settings.creation-tax", 0.0)));
        this.moneyFormat = c.getString("settings.money-format", "{amount}$");

        this.inputMethod = parseEnum(InputMethod.class, c.getString("input.method", "AUTO"), InputMethod.AUTO);
        this.chatCancelWord = c.getString("input.chat-cancel-word", "iptal");
        this.inputTimeoutSeconds = Math.max(5, c.getInt("input.timeout-seconds", 60));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Geçersiz yapılandırma değeri '" + value + "', varsayılan kullanılıyor: " + fallback);
            return fallback;
        }
    }

    public StorageType storageType() {
        return storageType;
    }

    public String sqliteFile() {
        return sqliteFile;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUsername() {
        return mysqlUsername;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public boolean mysqlUseSsl() {
        return mysqlUseSsl;
    }

    public List<String> mysqlProperties() {
        return mysqlProperties;
    }

    public int poolMaxSize() {
        return poolMaxSize;
    }

    public int poolMinIdle() {
        return poolMinIdle;
    }

    public long poolConnectionTimeout() {
        return poolConnectionTimeout;
    }

    public int cacheSyncIntervalSeconds() {
        return cacheSyncIntervalSeconds;
    }

    public int maxOrdersPerPlayer() {
        return maxOrdersPerPlayer;
    }

    public int maxAmountPerOrder() {
        return maxAmountPerOrder;
    }

    public double minUnitPrice() {
        return minUnitPrice;
    }

    public double maxUnitPrice() {
        return maxUnitPrice;
    }

    public double creationTax() {
        return creationTax;
    }

    public String moneyFormat() {
        return moneyFormat;
    }

    public InputMethod inputMethod() {
        return inputMethod;
    }

    public String chatCancelWord() {
        return chatCancelWord;
    }

    public int inputTimeoutSeconds() {
        return inputTimeoutSeconds;
    }
}

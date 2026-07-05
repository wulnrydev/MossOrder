package dev.wulnry.mossorder.storage;

import dev.wulnry.mossorder.model.Order;
import dev.wulnry.mossorder.model.PendingDelivery;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Siparişlerin ve bekleyen teslimatların veritabanı erişim katmanı.
 * <p>
 * Tüm işlemler {@link CompletableFuture} döndürür ve verilen asenkron
 * {@link Executor} üzerinde çalışır; böylece ana thread hiçbir zaman
 * veritabanı G/Ç'si için bloklanmaz.
 */
public final class OrderRepository {

    private final Database database;
    private final Executor asyncExecutor;
    private final Logger logger;

    public OrderRepository(Database database, Executor asyncExecutor, Logger logger) {
        this.database = database;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    /**
     * Tabloları oluşturur. Eklenti açılışında bir kez çağrılır.
     */
    public CompletableFuture<Void> initSchema() {
        return runAsync(() -> {
            boolean mysql = database.isMySql();
            String orders = mysql
                    ? "CREATE TABLE IF NOT EXISTS mo_orders ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "owner VARCHAR(36) NOT NULL,"
                    + "owner_name VARCHAR(32) NOT NULL,"
                    + "material VARCHAR(64) NOT NULL,"
                    + "amount INT NOT NULL,"
                    + "delivered INT NOT NULL DEFAULT 0,"
                    + "unit_price DOUBLE NOT NULL,"
                    + "created_at BIGINT NOT NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                    : "CREATE TABLE IF NOT EXISTS mo_orders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "owner TEXT NOT NULL,"
                    + "owner_name TEXT NOT NULL,"
                    + "material TEXT NOT NULL,"
                    + "amount INTEGER NOT NULL,"
                    + "delivered INTEGER NOT NULL DEFAULT 0,"
                    + "unit_price REAL NOT NULL,"
                    + "created_at INTEGER NOT NULL"
                    + ");";
            String pending = mysql
                    ? "CREATE TABLE IF NOT EXISTS mo_pending ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "player VARCHAR(36) NOT NULL,"
                    + "material VARCHAR(64) NOT NULL,"
                    + "amount INT NOT NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                    : "CREATE TABLE IF NOT EXISTS mo_pending ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player TEXT NOT NULL,"
                    + "material TEXT NOT NULL,"
                    + "amount INTEGER NOT NULL"
                    + ");";
            try (Connection conn = database.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate(orders);
                st.executeUpdate(pending);
                // Yalnızca fiilen sorgulanan sütun için index tutulur (mo_pending.player).
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mo_pending_player ON mo_pending (player);");
            }
            return null;
        });
    }

    /**
     * Henüz tamamlanmamış (delivered < amount) tüm siparişleri yükler.
     */
    public CompletableFuture<List<Order>> loadActiveOrders() {
        return runAsync(() -> {
            List<Order> result = new ArrayList<>();
            String sql = "SELECT id, owner, owner_name, material, amount, delivered, unit_price, created_at "
                    + "FROM mo_orders WHERE delivered < amount;";
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = readOrder(rs);
                    if (order != null) {
                        result.add(order);
                    }
                }
            }
            return result;
        });
    }

    /**
     * Yeni bir sipariş ekler ve oluşturulan kimliği (id) döndürür.
     */
    public CompletableFuture<Long> insert(UUID owner, String ownerName, Material material,
                                          int amount, double unitPrice, long createdAt) {
        return runAsync(() -> {
            String sql = "INSERT INTO mo_orders (owner, owner_name, material, amount, delivered, unit_price, created_at) "
                    + "VALUES (?, ?, ?, ?, 0, ?, ?);";
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, owner.toString());
                ps.setString(2, ownerName);
                ps.setString(3, material.name());
                ps.setInt(4, amount);
                ps.setDouble(5, unitPrice);
                ps.setLong(6, createdAt);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            return -1L;
        });
    }

    /**
     * Bir siparişin teslim edilen miktarını günceller.
     */
    public CompletableFuture<Void> updateDelivered(long id, int delivered) {
        return runAsync(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE mo_orders SET delivered = ? WHERE id = ?;")) {
                ps.setInt(1, delivered);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Bir siparişi siler.
     *
     * @return silinen satır varsa true
     */
    public CompletableFuture<Boolean> deleteById(long id) {
        return runAsync(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM mo_orders WHERE id = ?;")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    /**
     * Bir oyuncunun sipariş deposuna eşya ekler (satış anında, oyuncu
     * çevrimiçi olsa da olmasa da). Aynı oyuncu + materyal için zaten bir
     * satır varsa üzerine eklenir; böylece depo satırları katlanmadan birikir.
     * <p>
     * Tek iş parçacıklı asenkron yürütücü (bkz. {@link dev.wulnry.mossorder.MossOrder})
     * tüm veritabanı çağrılarını sıraya koyduğundan oku-sonra-yaz burada
     * yarış durumu (race condition) oluşturmaz.
     */
    public CompletableFuture<Void> addPending(UUID player, Material material, int amount) {
        return runAsync(() -> {
            try (Connection conn = database.getConnection()) {
                Long existingId = null;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT id FROM mo_pending WHERE player = ? AND material = ? ORDER BY id ASC LIMIT 1;")) {
                    select.setString(1, player.toString());
                    select.setString(2, material.name());
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getLong("id");
                        }
                    }
                }
                if (existingId != null) {
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE mo_pending SET amount = amount + ? WHERE id = ?;")) {
                        update.setInt(1, amount);
                        update.setLong(2, existingId);
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO mo_pending (player, material, amount) VALUES (?, ?, ?);")) {
                        insert.setString(1, player.toString());
                        insert.setString(2, material.name());
                        insert.setInt(3, amount);
                        insert.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    /**
     * Depodan çekilen (oyuncunun envanterine fiilen verilen) miktarları
     * satır kimliğine göre düşer. Azaltma göreceli (amount = amount - ?)
     * yapıldığından, aynı satıra o sırada yeni bir satış eklenmiş olsa bile
     * (bkz. {@link #addPending}) veri kaybı ya da çakışma oluşmaz. Miktarı
     * sıfırın altına düşen satırlar temizlenir.
     */
    public CompletableFuture<Void> decrementPending(Map<Long, Integer> claimed) {
        if (claimed == null || claimed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE mo_pending SET amount = amount - ? WHERE id = ?;");
                 PreparedStatement cleanup = conn.prepareStatement(
                         "DELETE FROM mo_pending WHERE amount <= 0;")) {
                for (Map.Entry<Long, Integer> entry : claimed.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                        continue;
                    }
                    update.setInt(1, entry.getValue());
                    update.setLong(2, entry.getKey());
                    update.addBatch();
                }
                update.executeBatch();
                cleanup.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Bir oyuncunun sipariş deposundaki satırlarını (satır kimlikleriyle) okur.
     * Depo, oyuncu eşyaları elle çekene kadar kalıcı olarak burada durur;
     * hiçbir satır otomatik silinmez veya envantere zorlanmaz.
     */
    public CompletableFuture<List<PendingDelivery>> readPending(UUID player) {
        return runAsync(() -> {
            List<PendingDelivery> result = new ArrayList<>();
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, material, amount FROM mo_pending WHERE player = ?;")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Material material = Material.getMaterial(rs.getString("material"));
                        int amount = rs.getInt("amount");
                        if (material != null && amount > 0) {
                            result.add(new PendingDelivery(rs.getLong("id"), material, amount));
                        }
                    }
                }
            }
            return result;
        });
    }

    private Order readOrder(ResultSet rs) throws SQLException {
        Material material = Material.getMaterial(rs.getString("material"));
        if (material == null) {
            return null;
        }
        UUID owner;
        try {
            owner = UUID.fromString(rs.getString("owner"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new Order(
                rs.getLong("id"),
                owner,
                rs.getString("owner_name"),
                material,
                rs.getInt("amount"),
                rs.getInt("delivered"),
                rs.getDouble("unit_price"),
                rs.getLong("created_at"));
    }

    private <T> CompletableFuture<T> runAsync(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(wrap(supplier), asyncExecutor);
    }

    private <T> Supplier<T> wrap(SqlSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (SQLException ex) {
                logger.severe("Veritabanı işlemi başarısız: " + ex.getMessage());
                throw new CompletionFailure(ex);
            }
        };
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    /** Asenkron zincirlerde SQL hatasını taşıyan sarmalayıcı. */
    public static final class CompletionFailure extends RuntimeException {
        public CompletionFailure(Throwable cause) {
            super(cause);
        }
    }
}

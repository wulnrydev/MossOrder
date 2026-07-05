package dev.wulnry.mossorder.service;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.config.PluginConfig;
import dev.wulnry.mossorder.economy.EconomyHook;
import dev.wulnry.mossorder.model.Order;
import dev.wulnry.mossorder.model.PendingDelivery;
import dev.wulnry.mossorder.model.SortMode;
import dev.wulnry.mossorder.storage.OrderRepository;
import dev.wulnry.mossorder.util.Inventories;
import dev.wulnry.mossorder.util.ItemNames;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sipariş yaşam döngüsünün iş mantığını yönetir: oluşturma (Vault ile ücret
 * tahsili), iptal/iade, yönetici kaldırma, sipariş karşılama (teslimat) ve
 * asenkron sıralama/filtreleme.
 * <p>
 * Ekonomi ve envanter işlemleri ana thread'de; veritabanı işlemleri asenkron
 * yürütülür.
 */
public final class OrderService {

    private final MossOrder plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final EconomyHook economy;
    private final OrderRepository repository;
    private final OrderCache cache;

    public OrderService(MossOrder plugin, PluginConfig config, Messages messages,
                        EconomyHook economy, OrderRepository repository, OrderCache cache) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.economy = economy;
        this.repository = repository;
        this.cache = cache;
    }

    public OrderCache cache() {
        return cache;
    }

    /**
     * Aktif siparişleri veritabanından yükleyip cache'e alır. Yalnızca açılışta
     * (cache boşken) çağrılır; tam değiştirme burada güvenlidir.
     */
    public void reloadCache() {
        repository.loadActiveOrders().whenComplete((orders, error) -> plugin.sync(() -> {
            if (error != null) {
                plugin.getLogger().severe("Siparişler yüklenemedi: " + error.getMessage());
                return;
            }
            cache.replaceAll(orders);
        }));
    }

    /**
     * Çalışma sırasında cache'i veritabanıyla <b>yıkıcı olmadan</b> uzlaştırır
     * (opsiyonel periyodik eşitleme). Uçuştaki yazmalar nedeniyle ilerlemenin
     * gerilemesine veya siparişlerin kaybolmasına yol açmaz.
     *
     * @see OrderCache#reconcile(java.util.Collection)
     */
    public void syncCache() {
        repository.loadActiveOrders().whenComplete((orders, error) -> plugin.sync(() -> {
            if (error != null) {
                plugin.getLogger().severe("Önbellek eşitlenemedi: " + error.getMessage());
                return;
            }
            cache.reconcile(orders);
        }));
    }

    // --------------------------------------------------------------- Oluşturma

    /**
     * Yeni bir alım siparişi oluşturur. Tüm doğrulamalar ve mesajlar burada
     * yönetilir. {@code onComplete} işlem sonunda ana thread'de çağrılır;
     * parametresi işlemin başarılı olup olmadığını belirtir.
     */
    public void createOrder(Player player, Material material, int amount, double unitPrice, Consumer<Boolean> onComplete) {
        if (material == null || !material.isItem()) {
            messages.send(player, "order.not-an-item", "{item}",
                    material == null ? "?" : ItemNames.display(material));
            complete(onComplete, false);
            return;
        }
        if (amount < 1 || amount > config.maxAmountPerOrder()) {
            messages.send(player, "order.invalid-amount", "{max}", config.maxAmountPerOrder());
            complete(onComplete, false);
            return;
        }
        if (unitPrice < config.minUnitPrice() || unitPrice > config.maxUnitPrice()) {
            messages.send(player, "order.invalid-price",
                    "{min}", messages.money(config.minUnitPrice()),
                    "{max}", messages.money(config.maxUnitPrice()));
            complete(onComplete, false);
            return;
        }
        int max = config.maxOrdersPerPlayer();
        if (max > 0 && cache.snapshotByOwner(player.getUniqueId()).size() >= max) {
            messages.send(player, "order.limit-reached", "{max}", max);
            complete(onComplete, false);
            return;
        }

        double total = amount * unitPrice;
        double tax = total * config.creationTax();
        double cost = total + tax;

        if (!economy.has(player, cost) || !economy.withdraw(player, cost)) {
            messages.send(player, "order.insufficient-funds",
                    "{total}", messages.money(cost),
                    "{balance}", messages.money(economy.balance(player)));
            complete(onComplete, false);
            return;
        }

        UUID owner = player.getUniqueId();
        String ownerName = player.getName();
        long now = System.currentTimeMillis();

        repository.insert(owner, ownerName, material, amount, unitPrice, now)
                .whenComplete((id, error) -> plugin.sync(() -> {
                    if (error != null || id == null || id <= 0) {
                        // Ekleme başarısız: parayı geri ver.
                        economy.deposit(player, cost);
                        messages.send(player, "general.cancelled");
                        plugin.getLogger().severe("Sipariş eklenemedi, ücret iade edildi.");
                        complete(onComplete, false);
                        return;
                    }
                    Order order = new Order(id, owner, ownerName, material, amount, 0, unitPrice, now);
                    cache.put(order);
                    messages.send(player, "order.created",
                            "{amount}", amount,
                            "{item}", ItemNames.display(material),
                            "{unit}", messages.money(unitPrice),
                            "{id}", id);
                    messages.send(player, "order.create-charged", "{total}", messages.money(cost));
                    if (tax > 0) {
                        messages.send(player, "order.create-tax", "{tax}", messages.money(tax));
                    }
                    complete(onComplete, true);
                }));
    }

    private void complete(Consumer<Boolean> onComplete, boolean success) {
        if (onComplete != null) {
            onComplete.accept(success);
        }
    }

    // ------------------------------------------------------------------ İptal

    /**
     * Oyuncunun kendi siparişini iptal eder ve teslim edilmemiş kısmın ücretini
     * iade eder.
     */
    public void cancelOwnOrder(Player player, long orderId, Runnable onComplete) {
        Order order = cache.get(orderId);
        if (order == null) {
            messages.send(player, "order.not-found", "{id}", orderId);
            runLater(onComplete);
            return;
        }
        if (!order.getOwner().equals(player.getUniqueId())) {
            messages.send(player, "order.not-owner");
            runLater(onComplete);
            return;
        }
        double refund = order.remainingValue();
        cache.remove(orderId);
        economy.deposit(player, refund);
        repository.deleteById(orderId);
        messages.send(player, "order.cancelled-refund",
                "{id}", orderId, "{refund}", messages.money(refund));
        runLater(onComplete);
    }

    /**
     * Yönetici bir siparişi zorla kaldırır; teslim edilmemiş kısmın ücreti
     * sahibine iade edilir.
     */
    public void adminDelete(CommandSender admin, long orderId, Runnable onComplete) {
        Order order = cache.get(orderId);
        if (order == null) {
            messages.send(admin, "order.not-found", "{id}", orderId);
            runLater(onComplete);
            return;
        }
        double refund = order.remainingValue();
        cache.remove(orderId);

        OfflinePlayer owner = Bukkit.getOfflinePlayer(order.getOwner());
        economy.deposit(owner, refund);
        repository.deleteById(orderId);

        messages.send(admin, "order.admin-removed",
                "{id}", orderId, "{refund}", messages.money(refund));

        Player onlineOwner = owner.getPlayer();
        if (onlineOwner != null && !onlineOwner.equals(admin)) {
            messages.send(onlineOwner, "order.admin-removed-target",
                    "{id}", orderId, "{refund}", messages.money(refund));
        }
        runLater(onComplete);
    }

    // -------------------------------------------------------------- Teslimat

    /**
     * Bir oyuncunun envanterindeki eşyaları bir siparişe satmasını (teslim
     * etmesini) sağlar. Satıcıya anında ödeme yapılır; eşyalar alıcıya
     * (çevrimiçiyse envanterine, değilse bekleyen teslimata) aktarılır.
     */
    public void fulfill(Player seller, long orderId, Runnable onComplete) {
        Order order = cache.get(orderId);
        if (order == null) {
            messages.send(seller, "order.not-found", "{id}", orderId);
            runLater(onComplete);
            return;
        }
        if (order.getOwner().equals(seller.getUniqueId())) {
            messages.send(seller, "delivery.own-order");
            runLater(onComplete);
            return;
        }

        Material material = order.getMaterial();
        int remaining = order.getRemaining();
        if (remaining <= 0) {
            cache.remove(orderId);
            messages.send(seller, "order.not-found", "{id}", orderId);
            runLater(onComplete);
            return;
        }

        int held = Inventories.countPlain(seller.getInventory(), material);
        int deliverable = Math.min(remaining, held);
        if (deliverable <= 0) {
            messages.send(seller, "delivery.no-items", "{item}", ItemNames.display(material));
            runLater(onComplete);
            return;
        }

        int removed = Inventories.removePlain(seller.getInventory(), material, deliverable);
        if (removed <= 0) {
            messages.send(seller, "delivery.no-items", "{item}", ItemNames.display(material));
            runLater(onComplete);
            return;
        }

        double earned = removed * order.getUnitPrice();
        economy.deposit(seller, earned);
        order.setDelivered(order.getDelivered() + removed);

        // Eşyalar her zaman alıcının sipariş deposuna düşer; çevrimiçi/çevrimdışı
        // fark etmez ve asla yere bırakılmaz. Alıcı istediği an /siparis depo
        // ile elle çeker.
        repository.addPending(order.getOwner(), material, removed);
        Player buyer = Bukkit.getPlayer(order.getOwner());
        if (buyer != null) {
            messages.send(buyer, "delivery.buyer-received",
                    "{amount}", removed, "{item}", ItemNames.display(material), "{id}", orderId);
        }

        boolean completed = order.isComplete();
        if (completed) {
            cache.remove(orderId);
            repository.deleteById(orderId);
        } else {
            repository.updateDelivered(orderId, order.getDelivered());
        }

        messages.send(seller, "delivery.sold",
                "{amount}", removed, "{item}", ItemNames.display(material),
                "{earned}", messages.money(earned), "{id}", orderId);
        if (completed) {
            messages.send(seller, "delivery.order-completed-seller", "{id}", orderId);
            if (buyer != null) {
                messages.send(buyer, "delivery.order-completed-buyer",
                        "{amount}", order.getAmount(), "{item}", ItemNames.display(material), "{id}", orderId);
            }
        }
        runLater(onComplete);
    }

    // --------------------------------------------------------------- Depo

    /**
     * Bir oyuncunun sipariş deposundaki satırları asenkron olarak yükler.
     */
    public void loadWarehouse(UUID owner, Consumer<List<PendingDelivery>> callback) {
        repository.readPending(owner).whenComplete((rows, error) -> plugin.sync(() -> {
            if (error != null || rows == null) {
                callback.accept(List.of());
                return;
            }
            callback.accept(rows);
        }));
    }

    /**
     * Oyuncunun deposundan envanterine sığdığı kadar eşya çeker.
     * {@code material} belirtilirse yalnızca o eşya çekilir; {@code null} ise
     * depodaki her şey (sığdığı kadarıyla) çekilir. Sığmayan kısım depoda
     * kalır; hiçbir eşya yere bırakılmaz ya da kaybedilmez.
     */
    public void claimWarehouse(Player player, Material material, Runnable onComplete) {
        repository.readPending(player.getUniqueId()).whenComplete((rows, error) -> plugin.sync(() -> {
            if (error != null || rows == null || rows.isEmpty()) {
                messages.send(player, "warehouse.empty");
                runLater(onComplete);
                return;
            }
            Map<Long, Integer> claimed = new HashMap<>();
            int totalGiven = 0;
            for (PendingDelivery row : rows) {
                if (material != null && row.material() != material) {
                    continue;
                }
                int given = Inventories.giveUpTo(player, row.material(), row.amount());
                if (given > 0) {
                    claimed.put(row.id(), given);
                    totalGiven += given;
                }
            }
            if (!claimed.isEmpty()) {
                repository.decrementPending(claimed);
            }
            if (totalGiven > 0) {
                messages.send(player, "warehouse.claimed", "{amount}", totalGiven);
            } else {
                messages.send(player, "warehouse.no-space");
            }
            runLater(onComplete);
        }));
    }

    // ----------------------------------------------------- Sıralama / Filtre

    /**
     * Aktif siparişleri asenkron olarak filtreler ve sıralar; sonuç ana
     * thread'de {@code callback} ile döner.
     */
    public void sortedActive(SortMode mode, String materialFilter, Consumer<List<Order>> callback) {
        plugin.async(() -> {
            List<Order> snapshot = cache.snapshot();
            String filter = normalizeFilter(materialFilter);
            List<Order> filtered = snapshot.stream()
                    .filter(order -> matchesFilter(order, filter))
                    .sorted(mode.comparator())
                    .toList();
            plugin.sync(() -> callback.accept(filtered));
        });
    }

    /**
     * Bir oyuncunun siparişlerini asenkron olarak (en yeni önce) döndürür.
     */
    public void ownerOrders(UUID owner, Consumer<List<Order>> callback) {
        plugin.async(() -> {
            List<Order> orders = cache.snapshotByOwner(owner).stream()
                    .sorted(SortMode.MOST_RECENT.comparator())
                    .toList();
            plugin.sync(() -> callback.accept(orders));
        });
    }

    private String normalizeFilter(String filter) {
        if (filter == null) {
            return null;
        }
        String trimmed = filter.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesFilter(Order order, String filter) {
        if (filter == null) {
            return true;
        }
        String key = order.getMaterial().name().toLowerCase(Locale.ROOT);
        return key.contains(filter);
    }

    private void runLater(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}

package dev.wulnry.mossorder;

import dev.wulnry.mossorder.command.OrderAdminCommand;
import dev.wulnry.mossorder.command.OrderCommand;
import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.config.PluginConfig;
import dev.wulnry.mossorder.economy.EconomyHook;
import dev.wulnry.mossorder.input.TextInputService;
import dev.wulnry.mossorder.listener.PlayerConnectionListener;
import dev.wulnry.mossorder.menu.DraftManager;
import dev.wulnry.mossorder.menu.MenuContext;
import dev.wulnry.mossorder.menu.MenuListener;
import dev.wulnry.mossorder.service.ItemCatalog;
import dev.wulnry.mossorder.service.OrderCache;
import dev.wulnry.mossorder.service.OrderService;
import dev.wulnry.mossorder.storage.Database;
import dev.wulnry.mossorder.storage.OrderRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * MossOrder ana sınıfı.
 * <p>
 * Tüm bağımlılıkları kurar, servisleri bağlar ve yaşam döngüsünü yönetir.
 * Ağır işler (veritabanı, sıralama, katalog) asenkron; yalnızca Bukkit API'nin
 * zorunlu kıldığı işlemler ana thread üzerinde çalışır.
 */
public final class MossOrder extends JavaPlugin {

    private ExecutorService dbExecutor;

    private PluginConfig config;
    private Messages messages;
    private MenuConfig menuConfig;
    private EconomyHook economy;
    private Database database;
    private OrderRepository repository;
    private OrderCache orderCache;
    private OrderService orderService;
    private ItemCatalog itemCatalog;
    private TextInputService textInput;
    private DraftManager draftManager;
    private MenuContext menuContext;

    private BukkitTask cacheSyncTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Yapılandırma dosyaları
        this.config = new PluginConfig(this);
        this.config.load();
        this.messages = new Messages(this);
        this.messages.load();
        this.menuConfig = new MenuConfig(this);
        this.menuConfig.load();

        // Ekonomi (Vault). Ekonomi sağlayıcısı (Essentials/CMI vb.) kendi
        // onEnable'ında Vault'a kaydolduğundan, yükleme sırasına bağlı olarak
        // şu an henüz hazır olmayabilir. Bu durumda plugin'i hemen kapatmak
        // yerine ilk tick'te (tüm eklentiler etkinleştikten sonra) tekrar denenir.
        this.economy = new EconomyHook(this);
        if (!economy.setup()) {
            getServer().getScheduler().runTask(this, () -> {
                if (!economy.setup()) {
                    getLogger().severe("Vault veya bir ekonomi sağlayıcısı bulunamadı! MossOrder devre dışı bırakılıyor.");
                    getServer().getPluginManager().disablePlugin(this);
                } else {
                    getLogger().info("Ekonomi sağlayıcısı gecikmeli olarak bağlandı.");
                }
            });
        }

        // Veritabanı
        this.database = new Database(this);
        try {
            database.connect();
        } catch (Exception ex) {
            getLogger().severe("Veritabanına bağlanılamadı: " + ex.getMessage());
            if (config.storageType() == PluginConfig.StorageType.MYSQL) {
                getLogger().severe("MySQL sürücüsü eksik olabilir. SQLITE kullanmayı deneyin.");
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Veritabanı işlemleri için ayrılmış tek iş parçacıklı havuz. Bukkit
        // zamanlayıcısından bağımsızdır; böylece kapanışta bekleyen yazmalar
        // iptal edilmeden tamamlanır ve SQLite'ın tek-yazıcı kilidi için tüm
        // işlemler doğal olarak sıralanır (SQLITE_BUSY çakışması olmaz).
        this.dbExecutor = Executors.newSingleThreadExecutor(dbThreadFactory());
        this.repository = new OrderRepository(database, dbExecutor, getLogger());
        this.orderCache = new OrderCache();
        this.orderService = new OrderService(this, config, messages, economy, repository, orderCache);
        this.itemCatalog = new ItemCatalog();
        this.draftManager = new DraftManager();
        this.textInput = new TextInputService(this, config, messages);
        this.menuContext = new MenuContext(this, config, messages, menuConfig,
                orderService, itemCatalog, textInput, draftManager);

        // Şema oluşturma -> önbelleği doldur (tamamı asenkron)
        repository.initSchema().whenComplete((ignored, error) -> {
            if (error != null) {
                sync(() -> {
                    getLogger().severe("Veritabanı şeması oluşturulamadı: " + error.getMessage());
                    getServer().getPluginManager().disablePlugin(this);
                });
                return;
            }
            orderService.reloadCache();
        });

        // Eşya kataloğunu açılışta bir kez asenkron oluştur
        async(() -> {
            itemCatalog.build();
            getLogger().info("Eşya kataloğu oluşturuldu: " + itemCatalog.size() + " eşya.");
        });

        registerListeners();
        registerCommands();
        startCacheSyncTask();

        getLogger().info("MossOrder etkinleştirildi. Depolama: " + config.storageType()
                + (textInputReady() ? " | Tabela girişi hazır" : " | Sohbet girişi"));
    }

    @Override
    public void onDisable() {
        if (cacheSyncTask != null) {
            cacheSyncTask.cancel();
            cacheSyncTask = null;
        }
        // Bukkit görevlerini (sıralama, katalog, zamanlayıcı) iptal et. Bunlar
        // salt-okunur olduğundan iptalleri veri kaybına yol açmaz.
        getServer().getScheduler().cancelTasks(this);

        // Bekleyen veritabanı yazmalarının bitmesini bekle, sonra havuzu kapat.
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Bazı veritabanı işlemleri zamanında tamamlanamadı.");
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                dbExecutor.shutdownNow();
            }
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("MossOrder devre dışı bırakıldı.");
    }

    /**
     * config.yml, messages.yml ve menus.yml dosyalarını yeniden yükler.
     * (Veritabanı ayarları değişiklikleri yeniden başlatma gerektirir.)
     */
    public void reload() {
        config.load();
        messages.load();
        menuConfig.load();
        startCacheSyncTask();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, repository, messages, draftManager), this);
    }

    private void registerCommands() {
        OrderCommand orderCommand = new OrderCommand(menuContext);
        PluginCommand siparis = getCommand("siparis");
        if (siparis != null) {
            siparis.setExecutor(orderCommand);
            siparis.setTabCompleter(orderCommand);
        }

        OrderAdminCommand adminCommand = new OrderAdminCommand(this, orderService, messages);
        PluginCommand siparisadmin = getCommand("siparisadmin");
        if (siparisadmin != null) {
            siparisadmin.setExecutor(adminCommand);
            siparisadmin.setTabCompleter(adminCommand);
        }
    }

    private void startCacheSyncTask() {
        if (cacheSyncTask != null) {
            cacheSyncTask.cancel();
            cacheSyncTask = null;
        }
        int interval = config.cacheSyncIntervalSeconds();
        if (interval <= 0) {
            return;
        }
        long ticks = interval * 20L;
        // Yıkıcı olmayan uzlaştırma kullanır (ilerlemeyi geri sarmaz, sipariş kaybetmez).
        cacheSyncTask = getServer().getScheduler().runTaskTimer(this,
                () -> orderService.syncCache(), ticks, ticks);
    }

    private boolean textInputReady() {
        return getServer().getPluginManager().isPluginEnabled("ProtocolLib")
                && config.inputMethod() != PluginConfig.InputMethod.CHAT;
    }

    // ----------------------------------------------------- Zamanlama yardımcıları

    /** Görevi asenkron çalıştırır (eklenti kapanıyorsa doğrudan çalıştırır). */
    public void async(Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    /** Görevi ana thread'de çalıştırır. */
    public void sync(Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }
        if (getServer().isPrimaryThread()) {
            runnable.run();
        } else {
            getServer().getScheduler().runTask(this, runnable);
        }
    }

    /** Görevi belirtilen tick sonra ana thread'de çalıştırır. */
    public BukkitTask syncLater(long ticks, Runnable runnable) {
        return getServer().getScheduler().runTaskLater(this, runnable, Math.max(1L, ticks));
    }

    /** Veritabanı havuzu için adlandırılmış, tekil iş parçacığı üretir. */
    private ThreadFactory dbThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "MossOrder-DB");
            thread.setDaemon(false);
            return thread;
        };
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }
}

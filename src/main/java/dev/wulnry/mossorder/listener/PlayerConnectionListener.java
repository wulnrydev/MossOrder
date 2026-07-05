package dev.wulnry.mossorder.listener;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.menu.DraftManager;
import dev.wulnry.mossorder.storage.OrderRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Oyuncu bağlantı olaylarını yönetir:
 * <ul>
 *   <li><b>Giriş:</b> sipariş deposunda bekleyen eşya varsa oyuncuya bilgi verir
 *       (eşyalar otomatik verilmez; /siparis depo ile elle çekilir).</li>
 *   <li><b>Çıkış:</b> yarım kalan sipariş taslaklarını temizler (bellek sızıntısını önler).</li>
 * </ul>
 */
public final class PlayerConnectionListener implements Listener {

    private final MossOrder plugin;
    private final OrderRepository repository;
    private final Messages messages;
    private final DraftManager drafts;

    public PlayerConnectionListener(MossOrder plugin, OrderRepository repository,
                                    Messages messages, DraftManager drafts) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
        this.drafts = drafts;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        repository.readPending(player.getUniqueId()).whenComplete((pending, error) -> plugin.sync(() -> {
            if (error != null || pending == null || pending.isEmpty() || !player.isOnline()) {
                return;
            }
            messages.send(player, "warehouse.has-items");
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drafts.clear(event.getPlayer().getUniqueId());
    }
}

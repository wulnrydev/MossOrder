package dev.wulnry.mossorder.input;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.config.PluginConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Sohbet tabanlı metin girişi. ProtocolLib olmadan da her koşulda çalışır.
 */
public final class ChatInput implements TextInputProvider, Listener {

    private final MossOrder plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInput(MossOrder plugin, PluginConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public void request(Player player, InputRequest request, Consumer<String> onResult, Runnable onCancel) {
        clear(player.getUniqueId());
        if (request.chatPrompt() != null && !request.chatPrompt().isEmpty()) {
            player.sendMessage(request.chatPrompt());
        }
        BukkitTask timeout = plugin.syncLater(config.inputTimeoutSeconds() * 20L, () -> {
            Session session = sessions.remove(player.getUniqueId());
            if (session != null) {
                messages.send(player, "input.timeout");
                session.onCancel.run();
            }
        });
        sessions.put(player.getUniqueId(), new Session(onResult, onCancel, timeout));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Session session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (session.timeout != null) {
            session.timeout.cancel();
        }
        String message = event.getMessage().trim();
        Player player = event.getPlayer();
        // Sohbet olayı asenkrondur; ana thread'e dönülür.
        plugin.sync(() -> {
            if (message.equalsIgnoreCase(config.chatCancelWord())) {
                messages.send(player, "general.cancelled");
                session.onCancel.run();
            } else {
                session.onResult.accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    public boolean isWaiting(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /** Oyuncunun bekleyen giriş oturumunu (varsa) kaldırır ve zaman aşımını iptal eder. */
    private void clear(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null && session.timeout != null) {
            session.timeout.cancel();
        }
    }

    private static final class Session {
        private final Consumer<String> onResult;
        private final Runnable onCancel;
        private final BukkitTask timeout;

        private Session(Consumer<String> onResult, Runnable onCancel, BukkitTask timeout) {
            this.onResult = onResult;
            this.onCancel = onCancel;
            this.timeout = timeout;
        }
    }
}

package dev.wulnry.mossorder.input;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.PluginConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ProtocolLib tabanlı Tabela (Sign) GUI metin girişi.
 * <p>
 * Oyuncuya istemci tarafında sahte bir tabela gösterilir, tabela editörü açılır
 * ve oyuncunun yazdığı metin geri okunur. Herhangi bir hata olursa {@link ChatInput}
 * yedeğine düşülür; böylece giriş her koşulda tamamlanır.
 */
public final class SignInput implements TextInputProvider, Listener {

    private final MossOrder plugin;
    private final PluginConfig config;
    private final ChatInput fallback;
    private final ProtocolManager protocol;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SignInput(MossOrder plugin, PluginConfig config, ChatInput fallback) {
        this.plugin = plugin;
        this.config = config;
        this.fallback = fallback;
        this.protocol = ProtocolLibrary.getProtocolManager();
        registerReceiveListener();
    }

    @Override
    public void request(Player player, InputRequest request, Consumer<String> onResult, Runnable onCancel) {
        UUID uuid = player.getUniqueId();
        clear(uuid);
        try {
            Location location = signLocation(player);
            player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
            // Yönlendirme satırlarını sahte tabelaya yaz (istemcide görünür).
            applySignText(player, location, request.signLines());

            PacketContainer open = protocol.createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
            open.getBlockPositionModifier().write(0,
                    new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            // 1.20+ ön/arka yüz baytı (varsa).
            try {
                open.getBooleans().write(0, true);
            } catch (RuntimeException ignored) {
                // Bu sürümde alan yoksa yok say.
            }
            protocol.sendServerPacket(player, open);

            // Tabela boş açıldığından yönlendirmeyi sohbetten de gönder.
            if (request.chatPrompt() != null && !request.chatPrompt().isEmpty()) {
                player.sendMessage(request.chatPrompt());
            }

            BukkitTask timeout = plugin.syncLater(config.inputTimeoutSeconds() * 20L, () -> {
                Session session = sessions.remove(uuid);
                if (session != null) {
                    revert(player, session.location);
                    session.onCancel.run();
                }
            });
            sessions.put(uuid, new Session(location, onResult, onCancel, timeout));
        } catch (Exception ex) {
            plugin.getLogger().warning("Tabela açılamadı, sohbet girişine geçiliyor: " + ex.getMessage());
            fallback.request(player, request, onResult, onCancel);
        }
    }

    private void registerReceiveListener() {
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.UPDATE_SIGN) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) {
                    return;
                }
                Session session = sessions.remove(player.getUniqueId());
                if (session == null) {
                    return;
                }
                if (session.timeout != null) {
                    session.timeout.cancel();
                }
                String input = readFirstLine(event.getPacket());
                SignInput.this.plugin.sync(() -> {
                    revert(player, session.location);
                    if (input == null || input.isBlank()) {
                        session.onCancel.run();
                    } else {
                        session.onResult.accept(input.trim());
                    }
                });
            }
        });
    }

    private String readFirstLine(PacketContainer packet) {
        try {
            String[] lines = packet.getStringArrays().read(0);
            if (lines != null && lines.length > 0) {
                return lines[0];
            }
        } catch (RuntimeException ignored) {
            // String[] alanı yoksa 4 ayrı String'i dene.
        }
        try {
            StructureModifier<String> strings = packet.getStrings();
            if (strings.size() > 0) {
                return strings.read(0);
            }
        } catch (RuntimeException ignored) {
            // yok say
        }
        return null;
    }

    /** Yönlendirme satırlarını istemciye gönderilen sahte tabelaya yazar. */
    @SuppressWarnings("deprecation")
    private void applySignText(Player player, Location location, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String[] four = {"", "", "", ""};
        for (int i = 0; i < four.length && i < lines.size(); i++) {
            String line = lines.get(i);
            four[i] = line == null ? "" : line;
        }
        try {
            player.sendSignChange(location, four);
        } catch (RuntimeException ex) {
            // Bazı sürüm/varyantlar desteklemeyebilir; tabela boş açılır,
            // sohbet yönlendirmesi yine gönderildiği için giriş çalışmaya devam eder.
        }
    }

    private Location signLocation(Player player) {
        Location location = player.getLocation().getBlock().getLocation();
        int min = player.getWorld().getMinHeight();
        int max = player.getWorld().getMaxHeight() - 1;
        int y = Math.max(min, Math.min(max, location.getBlockY()));
        location.setY(y);
        return location;
    }

    private void revert(Player player, Location location) {
        if (player.isOnline()) {
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void clear(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null && session.timeout != null) {
            session.timeout.cancel();
        }
    }

    private static final class Session {
        private final Location location;
        private final Consumer<String> onResult;
        private final Runnable onCancel;
        private final BukkitTask timeout;

        private Session(Location location, Consumer<String> onResult, Runnable onCancel, BukkitTask timeout) {
            this.location = location;
            this.onResult = onResult;
            this.onCancel = onCancel;
            this.timeout = timeout;
        }
    }
}

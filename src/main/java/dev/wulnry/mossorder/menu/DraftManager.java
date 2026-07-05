package dev.wulnry.mossorder.menu;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyunculara ait aktif sipariş taslaklarını yönetir.
 */
public final class DraftManager {

    private final ConcurrentHashMap<UUID, OrderDraft> drafts = new ConcurrentHashMap<>();

    /**
     * Oyuncunun taslağını döndürür; yoksa yeni bir tane oluşturur.
     */
    public OrderDraft getOrCreate(UUID uuid) {
        return drafts.computeIfAbsent(uuid, k -> new OrderDraft());
    }

    public OrderDraft get(UUID uuid) {
        return drafts.get(uuid);
    }

    public void clear(UUID uuid) {
        drafts.remove(uuid);
    }
}

package dev.wulnry.mossorder.model;

import org.bukkit.Material;

/**
 * Bir oyuncunun sipariş deposunda bekleyen eşya yığını (satılıp da henüz
 * envantere çekilmemiş eşyalar).
 * <p>
 * {@code id}, veritabanı satırının birincil anahtarıdır; eşya envantere
 * verildiğinde yalnızca gerçekten verilen miktar kadarı bu satırdan düşülür
 * (bkz. {@link dev.wulnry.mossorder.storage.OrderRepository#decrementPending}).
 */
public record PendingDelivery(long id, Material material, int amount) {
}

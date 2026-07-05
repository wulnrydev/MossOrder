package dev.wulnry.mossorder.model;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Bir alım siparişini (buy order) temsil eder.
 * <p>
 * {@code delivered} alanı ana thread üzerinde güncellenir; ancak asenkron
 * sıralama sırasında (ör. {@code MOST_DELIVERED}) okunabildiğinden görünürlüğü
 * garanti etmek için {@code volatile} olarak işaretlenmiştir.
 */
public final class Order {

    private final long id;
    private final UUID owner;
    private final String ownerName;
    private final Material material;
    private final int amount;
    private final double unitPrice;
    private final long createdAt;

    private volatile int delivered;

    public Order(long id, UUID owner, String ownerName, Material material,
                 int amount, int delivered, double unitPrice, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.material = material;
        this.amount = amount;
        this.delivered = delivered;
        this.unitPrice = unitPrice;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public int getDelivered() {
        return delivered;
    }

    public void setDelivered(int delivered) {
        this.delivered = Math.max(0, Math.min(delivered, amount));
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Henüz teslim edilmesi gereken kalan miktar.
     */
    public int getRemaining() {
        return Math.max(0, amount - delivered);
    }

    /**
     * Sipariş tamamen karşılandı mı?
     */
    public boolean isComplete() {
        return delivered >= amount;
    }

    /**
     * Siparişin toplam değeri (miktar * birim fiyat).
     */
    public double totalValue() {
        return amount * unitPrice;
    }

    /**
     * Kalan miktarın parasal karşılığı (iade tutarı için kullanılır).
     */
    public double remainingValue() {
        return getRemaining() * unitPrice;
    }
}

package dev.wulnry.mossorder.model;

import java.util.Comparator;

/**
 * Ana menüdeki siparişlerin sıralama modları.
 * "Sırala" butonuna her tıklandığında sıradaki moda geçilir.
 */
public enum SortMode {

    /** En çok ödenen: toplam sipariş değeri (miktar * birim fiyat) büyükten küçüğe. */
    MOST_PAID("En çok ödenen",
            Comparator.comparingDouble(Order::totalValue).reversed()
                    .thenComparingLong(Order::getId)),

    /** En çok teslim eden: teslim edilen miktar büyükten küçüğe. */
    MOST_DELIVERED("En çok teslim eden",
            Comparator.comparingInt(Order::getDelivered).reversed()
                    .thenComparingLong(Order::getId)),

    /** En son eklenen: oluşturulma zamanı yeniden eskiye. */
    MOST_RECENT("En son eklenen",
            Comparator.comparingLong(Order::getCreatedAt).reversed()
                    .thenComparingLong(Order::getId)),

    /** Birim başına en çok para: birim fiyat büyükten küçüğe. */
    HIGHEST_UNIT_PRICE("Birim başına en çok para",
            Comparator.comparingDouble(Order::getUnitPrice).reversed()
                    .thenComparingLong(Order::getId));

    private final String displayName;
    private final Comparator<Order> comparator;

    SortMode(String displayName, Comparator<Order> comparator) {
        this.displayName = displayName;
        this.comparator = comparator;
    }

    public String displayName() {
        return displayName;
    }

    public Comparator<Order> comparator() {
        return comparator;
    }

    /**
     * Bu moddan bir sonraki moda geçer (döngüsel).
     */
    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

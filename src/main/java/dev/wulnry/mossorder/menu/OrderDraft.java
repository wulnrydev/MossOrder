package dev.wulnry.mossorder.menu;

import org.bukkit.Material;

/**
 * "Yeni Sipariş Oluştur" akışında oyuncunun seçtiği geçici değerleri tutar.
 */
public final class OrderDraft {

    private Material material;
    private int amount;
    private double unitPrice;

    public Material material() {
        return material;
    }

    public void material(Material material) {
        this.material = material;
    }

    public int amount() {
        return amount;
    }

    public void amount(int amount) {
        this.amount = amount;
    }

    public double unitPrice() {
        return unitPrice;
    }

    public void unitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * Sipariş oluşturmak için tüm alanlar dolduruldu mu?
     */
    public boolean isComplete() {
        return material != null && amount > 0 && unitPrice > 0;
    }

    public double total() {
        return amount * unitPrice;
    }
}

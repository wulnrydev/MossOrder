package dev.wulnry.mossorder.service;

import dev.wulnry.mossorder.util.ItemNames;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 1.20.4 sürümündeki sipariş edilebilecek tüm eşyaların önbelleği.
 * <p>
 * Liste sunucu açılışında asenkron olarak <b>bir kez</b> oluşturulur; eşya
 * seçme menüsü her açıldığında yeniden hesaplanmaz.
 */
public final class ItemCatalog {

    private volatile List<Material> items = List.of();

    /**
     * Kataloğu oluşturur. Asenkron thread üzerinde çağrılmalıdır.
     */
    public void build() {
        List<Material> collected = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                continue;
            }
            if (!material.isItem()) {
                continue;
            }
            collected.add(material);
        }
        collected.sort(Comparator.comparing(m -> ItemNames.display(m).toLowerCase(Locale.ROOT)));
        this.items = List.copyOf(collected);
    }

    /**
     * Katalogdaki tüm eşyalar (değiştirilemez liste).
     */
    public List<Material> all() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public boolean isReady() {
        return !items.isEmpty();
    }
}

package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.model.PendingDelivery;
import dev.wulnry.mossorder.util.ItemNames;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Sipariş Deposu" menüsü: satılıp da henüz çekilmemiş eşyaları listeler.
 * Eşyalar buraya satış anında (oyuncu çevrimiçi olsa da olmasa da) düşer ve
 * yalnızca oyuncu burada elle çekince envantere geçer; hiçbir zaman otomatik
 * verilmez veya yere bırakılmaz.
 */
public final class WarehouseMenu extends Menu {

    private final MenuConfig.MenuLayout layout;

    private int page = 0;
    private List<PendingDelivery> data = new ArrayList<>();
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();
    private boolean claiming = false;

    public WarehouseMenu(MenuContext ctx, Player viewer) {
        super(ctx, viewer);
        this.layout = ctx.menuConfig().layout("warehouse");
    }

    @Override
    protected String title() {
        String base = layout != null ? layout.title() : "&8Sipariş Deposu";
        return base.replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(totalPages()));
    }

    @Override
    protected int size() {
        return layout != null ? layout.size() : 54;
    }

    @Override
    public void open() {
        super.open();
        reload();
    }

    @Override
    protected void build() {
        renderControls();
        renderContent();
        fillEmpty(layout != null ? layout.filler() : null);
    }

    public void reload() {
        ctx.orders().loadWarehouse(viewer.getUniqueId(), result -> {
            this.data = result;
            claimPage();
            if (isViewing()) {
                rebuild();
            }
        });
    }

    private void renderContent() {
        slotToMaterial.clear();
        int perPage = contentCount();
        if (perPage <= 0) {
            return;
        }
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= data.size()) {
                break;
            }
            PendingDelivery row = data.get(index);
            set(i, ctx.menuConfig().warehouseDisplay().build(row.material(),
                    "{item}", ItemNames.display(row.material()),
                    "{amount}", row.amount()));
            slotToMaterial.put(i, row.material());
        }
    }

    private void renderControls() {
        int pages = totalPages();
        place(button("back"));
        place(button("claim-all"));
        place(button("refresh"));
        if (page > 0) {
            place(button("previous-page"), "{page}", page + 1, "{pages}", pages);
        }
        if (page < pages - 1) {
            place(button("next-page"), "{page}", page + 1, "{pages}", pages);
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size()) {
            return;
        }

        if (slot == slotOf("back")) {
            openLater(new MainMenu(ctx, viewer));
            return;
        }
        if (slot == slotOf("refresh")) {
            reload();
            return;
        }
        if (slot == slotOf("previous-page")) {
            if (page > 0) {
                page--;
                rebuild();
            }
            return;
        }
        if (slot == slotOf("next-page")) {
            if (page < totalPages() - 1) {
                page++;
                rebuild();
            }
            return;
        }
        if (slot == slotOf("claim-all")) {
            claim(null);
            return;
        }

        Material material = slotToMaterial.get(slot);
        if (material != null) {
            claim(material);
        }
    }

    /** Tek bir eşyayı ({@code material}) ya da tamamını ({@code null}) çeker. */
    private void claim(Material material) {
        // Asenkron çekim tamamlanana kadar tekrar tıklamayı yok say (çift verme/çekimi önler).
        if (claiming) {
            return;
        }
        claiming = true;
        ctx.orders().claimWarehouse(viewer, material, () -> {
            claiming = false;
            reload();
        });
    }

    private void claimPage() {
        int pages = totalPages();
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
    }

    private int totalPages() {
        int perPage = Math.max(1, contentCount());
        return Math.max(1, (int) Math.ceil(data.size() / (double) perPage));
    }

    private MenuConfig.MenuButton button(String key) {
        return layout != null ? layout.button(key) : null;
    }

    private int slotOf(String key) {
        MenuConfig.MenuButton b = button(key);
        return b != null ? b.slot() : -1;
    }
}

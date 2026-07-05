package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.util.ItemNames;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Eşya seçme menüsü: 1.20.4'teki tüm sipariş edilebilir eşyaları önbellekten
 * sayfalı olarak listeler. Bir eşyaya tıklandığında taslağa eklenir.
 */
public final class ItemSelectMenu extends Menu {

    private final MenuConfig.MenuLayout layout;
    private final List<Material> catalog;
    private int page = 0;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();

    public ItemSelectMenu(MenuContext ctx, Player viewer) {
        super(ctx, viewer);
        this.layout = ctx.menuConfig().layout("item-select");
        this.catalog = ctx.catalog().all();
    }

    @Override
    protected String title() {
        String base = layout != null ? layout.title() : "&8Eşya Seç";
        return base.replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(totalPages()));
    }

    @Override
    protected int size() {
        return layout != null ? layout.size() : 54;
    }

    @Override
    protected void build() {
        renderContent();
        renderControls();
        fillEmpty(layout != null ? layout.filler() : null);
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
            if (index >= catalog.size()) {
                break;
            }
            Material material = catalog.get(index);
            set(i, ctx.menuConfig().itemDisplay().build(material,
                    "{item}", ItemNames.display(material),
                    "{key}", material.name().toLowerCase(Locale.ROOT)));
            slotToMaterial.put(i, material);
        }
    }

    private void renderControls() {
        int pages = totalPages();
        place(button("back"));
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
            openLater(new CreateOrderMenu(ctx, viewer));
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

        Material material = slotToMaterial.get(slot);
        if (material != null) {
            ctx.drafts().getOrCreate(viewer.getUniqueId()).material(material);
            openLater(new CreateOrderMenu(ctx, viewer));
        }
    }

    private int totalPages() {
        int perPage = Math.max(1, contentCount());
        return Math.max(1, (int) Math.ceil(catalog.size() / (double) perPage));
    }

    private MenuConfig.MenuButton button(String key) {
        return layout != null ? layout.button(key) : null;
    }

    private int slotOf(String key) {
        MenuConfig.MenuButton b = button(key);
        return b != null ? b.slot() : -1;
    }
}

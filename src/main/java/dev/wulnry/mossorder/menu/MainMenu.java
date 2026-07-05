package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.model.Order;
import dev.wulnry.mossorder.model.SortMode;
import dev.wulnry.mossorder.util.ItemNames;
import dev.wulnry.mossorder.util.Time;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ana menü: tüm aktif siparişleri sayfalı olarak listeler ve alt satırda
 * kontrol panelini (sırala, yenile, ara, senin siparişlerin) barındırır.
 */
public final class MainMenu extends Menu {

    private final MenuConfig.MenuLayout layout;

    private int page = 0;
    private SortMode sortMode = SortMode.MOST_RECENT;
    private String search = null;
    private List<Order> data = new ArrayList<>();
    private final Map<Integer, Long> slotToOrderId = new HashMap<>();

    public MainMenu(MenuContext ctx, Player viewer) {
        super(ctx, viewer);
        this.layout = ctx.menuConfig().layout("main");
    }

    @Override
    protected String title() {
        int pages = totalPages();
        String base = layout != null ? layout.title() : "&8Siparişler";
        return base.replace("{page}", String.valueOf(page + 1)).replace("{pages}", String.valueOf(pages));
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

    /** Siparişleri asenkron yeniden çeker ve menüyü günceller. */
    public void reload() {
        ctx.orders().sortedActive(sortMode, search, result -> {
            this.data = result;
            clampPage();
            if (isViewing()) {
                rebuild();
            }
        });
    }

    private void renderContent() {
        slotToOrderId.clear();
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
            Order order = data.get(index);
            set(i, renderCard(order));
            slotToOrderId.put(i, order.getId());
        }
    }

    private org.bukkit.inventory.ItemStack renderCard(Order order) {
        int pct = order.getAmount() > 0 ? (int) Math.floor(order.getDelivered() * 100.0 / order.getAmount()) : 0;
        String progress = order.getDelivered() + "/" + order.getAmount() + " (%" + pct + ")";
        return ctx.menuConfig().orderDisplay().build(order.getMaterial(),
                "{id}", order.getId(),
                "{owner}", order.getOwnerName(),
                "{item}", ItemNames.display(order.getMaterial()),
                "{amount}", order.getAmount(),
                "{delivered}", order.getDelivered(),
                "{remaining}", order.getRemaining(),
                "{unit}", ctx.messages().money(order.getUnitPrice()),
                "{total}", ctx.messages().money(order.totalValue()),
                "{progress}", progress,
                "{time}", Time.ago(order.getCreatedAt()));
    }

    private void renderControls() {
        int pages = totalPages();
        place(button("sort"), "{mode}", sortMode.displayName());
        place(button("refresh"));
        place(button("search"));
        place(button("my-orders"));
        place(button("warehouse"));
        place(button("close"));
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

        if (slot == slotOf("sort")) {
            sortMode = sortMode.next();
            ctx.messages().send(viewer, "menu.sort-changed", "{mode}", sortMode.displayName());
            page = 0;
            reload();
            return;
        }
        if (slot == slotOf("refresh")) {
            ctx.messages().send(viewer, "menu.refreshed");
            reload();
            return;
        }
        if (slot == slotOf("search")) {
            if (event.getClick() == ClickType.RIGHT && search != null) {
                search = null;
                ctx.messages().send(viewer, "search.cleared");
                page = 0;
                reload();
            } else {
                promptSearch();
            }
            return;
        }
        if (slot == slotOf("my-orders")) {
            openLater(new MyOrdersMenu(ctx, viewer));
            return;
        }
        if (slot == slotOf("warehouse")) {
            openLater(new WarehouseMenu(ctx, viewer));
            return;
        }
        if (slot == slotOf("close")) {
            closeLater();
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

        Long orderId = slotToOrderId.get(slot);
        if (orderId == null) {
            return;
        }
        if (event.getClick() == ClickType.RIGHT && viewer.hasPermission("mossorder.admin")) {
            ctx.orders().adminDelete(viewer, orderId, this::reload);
        } else {
            ctx.orders().fulfill(viewer, orderId, this::reload);
        }
    }

    private void promptSearch() {
        ctx.input().requestSearch(viewer, query -> {
            search = query;
            page = 0;
            ctx.messages().send(viewer, "search.applied", "{query}", query);
            new MainMenu(ctx, viewer).openWith(sortMode, query);
        }, () -> new MainMenu(ctx, viewer).openWith(sortMode, search));
    }

    /** Belirli bir sıralama ve arama ile açar (giriş sonrası menüyü yeniden açmak için). */
    public void openWith(SortMode mode, String query) {
        this.sortMode = mode;
        this.search = query;
        open();
    }

    private void clampPage() {
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

package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.model.Order;
import dev.wulnry.mossorder.util.ItemNames;
import dev.wulnry.mossorder.util.Time;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Senin Siparişlerin" menüsü: yalnızca oyuncunun kendi aktif siparişlerini
 * listeler. Sağ alt köşedeki sandık ile yeni sipariş oluşturma akışına geçilir.
 */
public final class MyOrdersMenu extends Menu {

    private final MenuConfig.MenuLayout layout;

    private int page = 0;
    private List<Order> data = new ArrayList<>();
    private final Map<Integer, Long> slotToOrderId = new HashMap<>();

    public MyOrdersMenu(MenuContext ctx, Player viewer) {
        super(ctx, viewer);
        this.layout = ctx.menuConfig().layout("my-orders");
    }

    @Override
    protected String title() {
        String base = layout != null ? layout.title() : "&8Senin Siparişlerin";
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
        ctx.orders().ownerOrders(viewer.getUniqueId(), result -> {
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

    private ItemStack renderCard(Order order) {
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
        place(button("back"));
        place(button("create"));
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
        if (slot == slotOf("create")) {
            ctx.drafts().clear(viewer.getUniqueId());
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

        Long orderId = slotToOrderId.get(slot);
        if (orderId == null) {
            return;
        }
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            ctx.orders().cancelOwnOrder(viewer, orderId, this::reload);
        } else {
            ctx.messages().send(viewer, "order.cancel-hint");
        }
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

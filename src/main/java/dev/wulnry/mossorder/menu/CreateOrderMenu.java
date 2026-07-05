package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.util.ItemNames;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * "Yeni Sipariş Oluştur" menüsü: eşya seçme, miktar ve birim fiyat belirleme,
 * ardından onaylama veya iptal.
 */
public final class CreateOrderMenu extends Menu {

    private static final String NOT_SELECTED = "&cSeçilmedi";

    private final MenuConfig.MenuLayout layout;

    /** Onay işlemi sürerken tekrar tıklamayı engeller (çift ücret/çift sipariş koruması). */
    private boolean submitting = false;

    public CreateOrderMenu(MenuContext ctx, Player viewer) {
        super(ctx, viewer);
        this.layout = ctx.menuConfig().layout("create");
    }

    @Override
    protected String title() {
        return layout != null ? layout.title() : "&8Yeni Sipariş";
    }

    @Override
    protected int size() {
        return layout != null ? layout.size() : 45;
    }

    @Override
    protected void build() {
        OrderDraft draft = ctx.drafts().getOrCreate(viewer.getUniqueId());

        String itemName = draft.material() != null ? "&#00D420" + ItemNames.display(draft.material()) : NOT_SELECTED;
        String amountText = draft.amount() > 0 ? String.valueOf(draft.amount()) : NOT_SELECTED;
        String unitText = draft.unitPrice() > 0 ? ctx.messages().money(draft.unitPrice()) : NOT_SELECTED;
        String totalText = draft.isComplete() ? ctx.messages().money(draft.total()) : "&8-";

        // Eşya butonu seçilen materyali ikon olarak gösterir.
        MenuConfig.MenuButton itemButton = button("item");
        if (itemButton != null && itemButton.icon() != null) {
            set(itemButton.slot(), itemButton.icon().build(draft.material(), "{item}", itemName));
        }

        place(button("amount"), "{amount}", amountText);
        place(button("price"), "{unit}", unitText, "{total}", totalText);
        place(button("confirm"), "{total}", totalText);
        place(button("cancel"));

        fillEmpty(layout != null ? layout.filler() : null);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size()) {
            return;
        }
        // Onay işlenirken hiçbir tıklamayı kabul etme.
        if (submitting) {
            return;
        }

        if (slot == slotOf("item")) {
            openLater(new ItemSelectMenu(ctx, viewer));
            return;
        }
        if (slot == slotOf("amount")) {
            promptAmount();
            return;
        }
        if (slot == slotOf("price")) {
            promptPrice();
            return;
        }
        if (slot == slotOf("confirm")) {
            confirm();
            return;
        }
        if (slot == slotOf("cancel")) {
            ctx.drafts().clear(viewer.getUniqueId());
            openLater(new MyOrdersMenu(ctx, viewer));
        }
    }

    private void promptAmount() {
        ctx.input().requestAmount(viewer, input -> {
            OptionalInt parsed = dev.wulnry.mossorder.util.Numbers.parsePositiveInt(input);
            if (parsed.isEmpty()) {
                ctx.messages().send(viewer, "general.invalid-number", "{input}", input);
            } else {
                ctx.drafts().getOrCreate(viewer.getUniqueId()).amount(parsed.getAsInt());
            }
            reopen();
        }, this::reopen);
    }

    private void promptPrice() {
        ctx.input().requestPrice(viewer, input -> {
            OptionalDouble parsed = dev.wulnry.mossorder.util.Numbers.parsePositiveDouble(input);
            if (parsed.isEmpty()) {
                ctx.messages().send(viewer, "general.invalid-number", "{input}", input);
            } else {
                ctx.drafts().getOrCreate(viewer.getUniqueId()).unitPrice(parsed.getAsDouble());
            }
            reopen();
        }, this::reopen);
    }

    private void confirm() {
        OrderDraft draft = ctx.drafts().get(viewer.getUniqueId());
        if (draft == null || !draft.isComplete()) {
            ctx.messages().send(viewer, "order.draft-incomplete");
            return;
        }
        // Bu andan itibaren yeni tıklamalar yok sayılır; para çekimi tek sefer olur.
        submitting = true;
        ctx.orders().createOrder(viewer, draft.material(), draft.amount(), draft.unitPrice(), success -> {
            if (success) {
                ctx.drafts().clear(viewer.getUniqueId());
                new MyOrdersMenu(ctx, viewer).open();
            } else {
                // Hata (ör. yetersiz bakiye): taslağı koruyarak menüyü yeniden aç.
                reopen();
            }
        });
    }

    private void reopen() {
        new CreateOrderMenu(ctx, viewer).open();
    }

    private MenuConfig.MenuButton button(String key) {
        return layout != null ? layout.button(key) : null;
    }

    private int slotOf(String key) {
        MenuConfig.MenuButton b = button(key);
        return b != null ? b.slot() : -1;
    }
}

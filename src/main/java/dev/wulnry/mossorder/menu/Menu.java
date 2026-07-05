package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Tüm MossOrder menülerinin temel sınıfı.
 * <p>
 * {@link InventoryHolder} deseni kullanılır; böylece bir envanterin hangi menüye
 * ait olduğu güvenle belirlenir ve tıklamalar doğru menüye yönlendirilir.
 */
public abstract class Menu implements InventoryHolder {

    protected final MenuContext ctx;
    protected final Player viewer;
    protected Inventory inventory;

    protected Menu(MenuContext ctx, Player viewer) {
        this.ctx = ctx;
        this.viewer = viewer;
    }

    /** Çözümlenmiş (renkli, yer tutucular uygulanmış) başlık. */
    protected abstract String title();

    /** Envanter boyutu (9'un katı, 9-54 arası). */
    protected abstract int size();

    /** Envanter içeriğini oluşturur. */
    protected abstract void build();

    /** Bir tıklamayı işler. Üst envanter dışındaki tıklamalar zaten iptal edilmiştir. */
    public abstract void onClick(InventoryClickEvent event);

    /** Menü kapatıldığında çağrılır (varsayılan: bir şey yapmaz). */
    public void onClose(InventoryCloseEvent event) {
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Menüyü oluşturur ve oyuncuya açar. */
    public void open() {
        this.inventory = Bukkit.createInventory(this, size(), Text.color(title()));
        build();
        viewer.openInventory(inventory);
    }

    /** Açık olan menüyü yeniden oluşturur (aynı envanter nesnesi üzerinde). */
    public void rebuild() {
        if (inventory == null) {
            open();
            return;
        }
        inventory.clear();
        build();
    }

    /** Başka bir menüyü bir sonraki tick'te açar (tıklama olayından güvenle çıkmak için). */
    protected void openLater(Menu target) {
        ctx.plugin().syncLater(1L, target::open);
    }

    /** Menüyü bir sonraki tick'te kapatır. */
    protected void closeLater() {
        ctx.plugin().syncLater(1L, viewer::closeInventory);
    }

    /** Oyuncu hâlâ bu menüyü mü görüntülüyor? (Asenkron güncellemeler için.) */
    protected boolean isViewing() {
        return inventory != null
                && viewer.isOnline()
                && viewer.getOpenInventory().getTopInventory().getHolder() == this;
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /** Yapılandırılmış bir butonu kendi materyaliyle yerleştirir. */
    protected void place(MenuConfig.MenuButton button, Object... placeholders) {
        if (button == null) {
            return;
        }
        MenuConfig.Icon icon = button.icon();
        if (icon != null) {
            set(button.slot(), icon.build(placeholders));
        }
    }

    /** İçerik alanındaki (son satır hariç) yuva sayısı. */
    protected int contentCount() {
        return Math.max(0, size() - 9);
    }

    /** Boş yuvaları doldurucu ikonla kaplar. */
    protected void fillEmpty(MenuConfig.Icon filler) {
        if (filler == null) {
            return;
        }
        ItemStack item = filler.build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item);
            }
        }
    }
}

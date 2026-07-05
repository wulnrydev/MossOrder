package dev.wulnry.mossorder.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Tüm menü tıklamalarını, sürüklemelerini ve kapanışlarını ilgili {@link Menu}
 * örneğine yönlendiren tek merkezi dinleyici.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu menu)) {
            return;
        }
        // Menülerde hiçbir eşya taşınamaz.
        event.setCancelled(true);
        menu.onClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            menu.onClose(event);
        }
    }
}

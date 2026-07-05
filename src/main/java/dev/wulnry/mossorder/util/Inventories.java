package dev.wulnry.mossorder.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Envanter işlemleri için yardımcılar.
 * <p>
 * Siparişler yalnızca "düz" (özel meta içermeyen) eşyalarla karşılanır; böylece
 * sayım ve silme her zaman tutarlı olur ve büyülü/isimlendirilmiş eşyalar
 * yanlışlıkla teslim edilmez.
 */
public final class Inventories {

    private Inventories() {
    }

    private static boolean isPlain(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && !stack.hasItemMeta();
    }

    /**
     * Envanterdeki belirtilen materyalden kaç adet düz eşya olduğunu sayar.
     */
    public static int countPlain(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Envanterden en fazla {@code amount} adet düz eşya siler.
     *
     * @return gerçekten silinen miktar
     */
    public static int removePlain(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isPlain(stack, material)) {
                continue;
            }
            int inStack = stack.getAmount();
            if (inStack <= remaining) {
                remaining -= inStack;
                inventory.setItem(i, null);
            } else {
                // Yeni bir kopya yazarak sürüme özgü (mirror/clone) davranıştan bağımsız kal.
                ItemStack reduced = stack.clone();
                reduced.setAmount(inStack - remaining);
                inventory.setItem(i, reduced);
                remaining = 0;
            }
        }
        return amount - remaining;
    }

    /**
     * Oyuncuya envanterinde <b>sığdığı kadar</b> eşya verir; sığmayan kısmı
     * asla yere bırakmaz veya kaybetmez (çağıran, verilmeyen farkı depoda
     * bırakmakla yükümlüdür).
     *
     * @return gerçekten envantere verilen miktar
     */
    public static int giveUpTo(Player player, Material material, int amount) {
        int given = 0;
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, material.getMaxStackSize());
            ItemStack stack = new ItemStack(material, stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            int notAdded = 0;
            for (ItemStack leftover : overflow.values()) {
                notAdded += leftover.getAmount();
            }
            given += stackSize - notAdded;
            remaining -= stackSize;
            if (notAdded > 0) {
                // Envanter dolu: kalanı depoda bırak, yere hiçbir şey bırakma.
                break;
            }
        }
        return given;
    }
}

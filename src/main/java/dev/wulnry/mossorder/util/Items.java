package dev.wulnry.mossorder.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Akıcı (fluent) {@link ItemStack} oluşturucu.
 */
public final class Items {

    private final ItemStack stack;
    private final ItemMeta meta;

    private Items(Material material, int amount) {
        this.stack = new ItemStack(material == null ? Material.STONE : material, Math.max(1, amount));
        this.meta = stack.getItemMeta();
    }

    public static Items of(Material material) {
        return new Items(material, 1);
    }

    public static Items of(Material material, int amount) {
        return new Items(material, amount);
    }

    public Items name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(Text.color(name));
        }
        return this;
    }

    public Items lore(List<String> lore) {
        if (meta != null && lore != null) {
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                colored.add(Text.color(line));
            }
            meta.setLore(colored);
        }
        return this;
    }

    public Items glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public Items modelData(int modelData) {
        if (meta != null && modelData > 0) {
            meta.setCustomModelData(modelData);
        }
        return this;
    }

    public Items hideAttributes() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

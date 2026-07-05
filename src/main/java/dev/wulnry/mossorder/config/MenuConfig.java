package dev.wulnry.mossorder.config;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * menus.yml dosyasını okur; menü düzenlerini (başlık, satır, buton, doldurucu)
 * ve sipariş/eşya gösterim şablonlarını sağlar.
 */
public final class MenuConfig {

    private final MossOrder plugin;
    private final Map<String, MenuLayout> layouts = new HashMap<>();

    private Icon orderDisplay;
    private Icon itemDisplay;
    private Icon warehouseDisplay;

    public MenuConfig(MossOrder plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "menus.yml");
        if (!file.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        FileConfiguration c = YamlConfiguration.loadConfiguration(file);
        layouts.clear();

        ConfigurationSection menus = c.getConfigurationSection("menus");
        if (menus != null) {
            for (String key : menus.getKeys(false)) {
                ConfigurationSection section = menus.getConfigurationSection(key);
                if (section != null) {
                    layouts.put(key, readLayout(section));
                }
            }
        }

        this.orderDisplay = readIcon(c.getConfigurationSection("order-display"));
        if (this.orderDisplay == null) {
            this.orderDisplay = new Icon(null, "&a{item}", List.of("&7#{id} • {remaining}/{amount}", "&7Birim: {unit}"), 1, false, 0);
        }
        ConfigurationSection itemSelect = c.getConfigurationSection("menus.item-select.item-display");
        this.itemDisplay = readIcon(itemSelect);
        if (this.itemDisplay == null) {
            this.itemDisplay = new Icon(null, "&a{item}", List.of("&8{key}"), 1, false, 0);
        }
        ConfigurationSection warehouse = c.getConfigurationSection("menus.warehouse.item-display");
        this.warehouseDisplay = readIcon(warehouse);
        if (this.warehouseDisplay == null) {
            this.warehouseDisplay = new Icon(null, "&a{item}", List.of("&7Miktar: {amount}"), 1, false, 0);
        }
    }

    private MenuLayout readLayout(ConfigurationSection section) {
        String title = section.getString("title", "&8Menü");
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Icon filler = readIcon(section.getConfigurationSection("filler"));

        Map<String, MenuButton> buttons = new HashMap<>();
        ConfigurationSection buttonSection = section.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String key : buttonSection.getKeys(false)) {
                ConfigurationSection b = buttonSection.getConfigurationSection(key);
                if (b == null) {
                    continue;
                }
                int slot = b.getInt("slot", -1);
                Icon icon = readIcon(b);
                buttons.put(key, new MenuButton(slot, icon));
            }
        }
        return new MenuLayout(title, rows, filler, buttons);
    }

    private Icon readIcon(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Material material = null;
        String materialName = section.getString("material");
        if (materialName != null) {
            material = Material.matchMaterial(materialName.toUpperCase());
            if (material == null) {
                plugin.getLogger().warning("menus.yml içinde geçersiz materyal: " + materialName);
            }
        }
        String name = section.getString("name", " ");
        List<String> lore = section.getStringList("lore");
        int amount = Math.max(1, section.getInt("amount", 1));
        boolean glow = section.getBoolean("glow", false);
        int modelData = section.getInt("model-data", 0);
        return new Icon(material, name, lore, amount, glow, modelData);
    }

    public MenuLayout layout(String key) {
        return layouts.get(key);
    }

    public Icon orderDisplay() {
        return orderDisplay;
    }

    public Icon itemDisplay() {
        return itemDisplay;
    }

    public Icon warehouseDisplay() {
        return warehouseDisplay;
    }

    // ---------------------------------------------------------------------

    /** Yer tutucuları name/lore üzerinde uygulayarak {@link ItemStack} üretir. */
    public static final class Icon {

        private final Material material;
        private final String name;
        private final List<String> lore;
        private final int amount;
        private final boolean glow;
        private final int modelData;

        public Icon(Material material, String name, List<String> lore, int amount, boolean glow, int modelData) {
            this.material = material;
            this.name = name;
            this.lore = lore == null ? new ArrayList<>() : lore;
            this.amount = amount;
            this.glow = glow;
            this.modelData = modelData;
        }

        public Material material() {
            return material;
        }

        /** Yapılandırmadaki materyal ile eşya üretir. */
        public ItemStack build(Object... placeholders) {
            return build(material, placeholders);
        }

        /** Verilen materyal ile eşya üretir (sipariş/eşya kartlarında kullanılır). */
        public ItemStack build(Material override, Object... placeholders) {
            Material mat = override != null ? override : (material != null ? material : Material.STONE);
            List<String> renderedLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                renderedLore.add(apply(line, placeholders));
            }
            return Items.of(mat, amount)
                    .name(apply(name, placeholders))
                    .lore(renderedLore)
                    .glow(glow)
                    .modelData(modelData)
                    .hideAttributes()
                    .build();
        }

        private static String apply(String input, Object... placeholders) {
            if (input == null) {
                return "";
            }
            if (placeholders == null || placeholders.length == 0) {
                return input;
            }
            String result = input;
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                result = result.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
            }
            return result;
        }
    }

    /** Bir yuva ve ikondan oluşan menü butonu. */
    public static final class MenuButton {
        private final int slot;
        private final Icon icon;

        public MenuButton(int slot, Icon icon) {
            this.slot = slot;
            this.icon = icon;
        }

        public int slot() {
            return slot;
        }

        public Icon icon() {
            return icon;
        }
    }

    /** Bir menünün tüm düzeni. */
    public static final class MenuLayout {
        private final String title;
        private final int rows;
        private final Icon filler;
        private final Map<String, MenuButton> buttons;

        public MenuLayout(String title, int rows, Icon filler, Map<String, MenuButton> buttons) {
            this.title = title;
            this.rows = rows;
            this.filler = filler;
            this.buttons = buttons;
        }

        public String title() {
            return title;
        }

        public int rows() {
            return rows;
        }

        public int size() {
            return rows * 9;
        }

        public Icon filler() {
            return filler;
        }

        public MenuButton button(String key) {
            return buttons.get(key);
        }

        public Map<String, MenuButton> buttons() {
            return buttons;
        }
    }
}

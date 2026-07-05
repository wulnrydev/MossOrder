package dev.wulnry.mossorder.menu;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.config.MenuConfig;
import dev.wulnry.mossorder.config.PluginConfig;
import dev.wulnry.mossorder.input.TextInputService;
import dev.wulnry.mossorder.service.ItemCatalog;
import dev.wulnry.mossorder.service.OrderService;

/**
 * Menülerin ihtiyaç duyduğu tüm servislerin paylaşılan taşıyıcısı.
 * Menülere tek bir bağımlılık geçirmek için kullanılır.
 */
public final class MenuContext {

    private final MossOrder plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final MenuConfig menuConfig;
    private final OrderService orders;
    private final ItemCatalog catalog;
    private final TextInputService input;
    private final DraftManager drafts;

    public MenuContext(MossOrder plugin, PluginConfig config, Messages messages, MenuConfig menuConfig,
                       OrderService orders, ItemCatalog catalog, TextInputService input, DraftManager drafts) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.menuConfig = menuConfig;
        this.orders = orders;
        this.catalog = catalog;
        this.input = input;
        this.drafts = drafts;
    }

    public MossOrder plugin() {
        return plugin;
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public MenuConfig menuConfig() {
        return menuConfig;
    }

    public OrderService orders() {
        return orders;
    }

    public ItemCatalog catalog() {
        return catalog;
    }

    public TextInputService input() {
        return input;
    }

    public DraftManager drafts() {
        return drafts;
    }
}

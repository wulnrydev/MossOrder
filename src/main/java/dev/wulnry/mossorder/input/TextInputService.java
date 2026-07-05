package dev.wulnry.mossorder.input;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Yapılandırmaya ve ProtocolLib varlığına göre uygun metin girişi sağlayıcısını
 * seçer ve menülere kolay erişim sunar.
 */
public final class TextInputService {

    /** İstenecek giriş türü; ilgili tabela satırlarını ve sohbet mesajını belirler. */
    public enum InputType {
        SEARCH("input.sign-search", "input.chat-search"),
        PRICE("input.sign-price", "input.chat-price"),
        AMOUNT("input.sign-amount", "input.chat-amount");

        private final String signKey;
        private final String chatKey;

        InputType(String signKey, String chatKey) {
            this.signKey = signKey;
            this.chatKey = chatKey;
        }
    }

    private final MossOrder plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final ChatInput chatInput;
    private final SignInput signInput; // ProtocolLib yoksa null

    public TextInputService(MossOrder plugin, PluginConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;

        this.chatInput = new ChatInput(plugin, config, messages);
        plugin.getServer().getPluginManager().registerEvents(chatInput, plugin);

        SignInput sign = null;
        boolean protocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
        boolean wantSign = config.inputMethod() != PluginConfig.InputMethod.CHAT;
        if (wantSign && protocolLib) {
            try {
                sign = new SignInput(plugin, config, chatInput);
                plugin.getServer().getPluginManager().registerEvents(sign, plugin);
            } catch (Throwable t) {
                plugin.getLogger().warning("Tabela girişi başlatılamadı, sohbet kullanılacak: " + t.getMessage());
                sign = null;
            }
        }
        this.signInput = sign;

        if (config.inputMethod() == PluginConfig.InputMethod.SIGN && sign == null) {
            plugin.getLogger().warning("input.method=SIGN seçili ancak ProtocolLib bulunamadı; sohbet girişi kullanılacak.");
        }
    }

    public void requestSearch(Player player, Consumer<String> onResult, Runnable onCancel) {
        request(player, InputType.SEARCH, onResult, onCancel);
    }

    public void requestPrice(Player player, Consumer<String> onResult, Runnable onCancel) {
        request(player, InputType.PRICE, onResult, onCancel);
    }

    public void requestAmount(Player player, Consumer<String> onResult, Runnable onCancel) {
        request(player, InputType.AMOUNT, onResult, onCancel);
    }

    private void request(Player player, InputType type, Consumer<String> onResult, Runnable onCancel) {
        List<String> signLines = messages.plainList(type.signKey);
        String chatPrompt = messages.format(type.chatKey, "{cancel}", config.chatCancelWord());
        TextInputProvider.InputRequest request = new TextInputProvider.InputRequest(signLines, chatPrompt);

        TextInputProvider provider = signInput != null ? signInput : chatInput;

        // Menüyü kapat, ardından bir sonraki tick'te girişi başlat.
        player.closeInventory();
        plugin.syncLater(1L, () -> provider.request(player, request, onResult, onCancel));
    }
}

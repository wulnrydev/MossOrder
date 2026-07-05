package dev.wulnry.mossorder.input;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Oyuncudan metin girişi (arama, fiyat, miktar) toplayan sağlayıcı.
 * İki uygulaması vardır: {@link SignInput} (Tabela) ve {@link ChatInput} (sohbet).
 */
public interface TextInputProvider {

    /**
     * Oyuncudan metin ister.
     *
     * @param player    hedef oyuncu
     * @param request   gösterilecek tabela satırları ve sohbet yönlendirmesi
     * @param onResult  girilen metin (ana thread'de çağrılır)
     * @param onCancel  iptal/zaman aşımı durumunda (ana thread'de çağrılır)
     */
    void request(Player player, InputRequest request, Consumer<String> onResult, Runnable onCancel);

    /**
     * Bir metin girişi talebinin görsel içeriği.
     *
     * @param signLines  tabela üzerinde gösterilecek satırlar (en fazla 4)
     * @param chatPrompt oyuncuya gönderilecek yönlendirme mesajı (renkli, ön ekli)
     */
    record InputRequest(List<String> signLines, String chatPrompt) {
    }
}

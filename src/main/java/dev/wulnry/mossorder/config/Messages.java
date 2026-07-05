package dev.wulnry.mossorder.config;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.util.Numbers;
import dev.wulnry.mossorder.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * messages.yml dosyasını yükler ve yer tutucu değişimi ile mesaj gönderir.
 * UTF-8 kodlamasıyla okunur; Türkçe karakter desteği garanti edilir.
 */
public final class Messages {

    private final MossOrder plugin;
    private FileConfiguration config;
    private String prefix = "";

    public Messages(MossOrder plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        // Yeni eklenen mesajları jar içindeki varsayılanlarla tamamla.
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Varsayılan mesajlar yüklenemedi: " + ex.getMessage());
        }

        this.prefix = Text.color(config.getString("prefix", ""));
    }

    /**
     * Ön ek dahil, yer tutucuları değiştirilmiş renkli mesaj.
     */
    public String format(String path, Object... placeholders) {
        return prefix + raw(path, placeholders);
    }

    /**
     * Ön ek olmadan, yer tutucuları değiştirilmiş renkli mesaj.
     */
    public String raw(String path, Object... placeholders) {
        String value = config.getString(path);
        if (value == null) {
            return Text.color("&c[eksik mesaj: " + path + "]");
        }
        return Text.color(replace(value, placeholders));
    }

    /**
     * Renkli mesaj listesi (ör: yardım metni, tabela satırları).
     */
    public List<String> rawList(String path, Object... placeholders) {
        List<String> lines = config.getStringList(path);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.color(replace(line, placeholders)));
        }
        return out;
    }

    /**
     * Ham (renksiz, ön eksiz) metin; tabela satırları gibi renk istemeyen
     * yerlerde kullanılır.
     */
    public List<String> plainList(String path, Object... placeholders) {
        List<String> lines = config.getStringList(path);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.strip(replace(line, placeholders)));
        }
        return out;
    }

    /**
     * Ön ekli mesajı bir alıcıya gönderir. Mesaj boşsa hiçbir şey gönderilmez.
     */
    public void send(CommandSender target, String path, Object... placeholders) {
        String value = config.getString(path);
        if (value == null) {
            target.sendMessage(Text.color("&c[eksik mesaj: " + path + "]"));
            return;
        }
        if (value.isEmpty()) {
            return;
        }
        target.sendMessage(prefix + Text.color(replace(value, placeholders)));
    }

    /**
     * Yardım listesini (her satırı ön eksiz renkli) gönderir.
     */
    public void sendList(CommandSender target, String path, Object... placeholders) {
        for (String line : rawList(path, placeholders)) {
            target.sendMessage(line);
        }
    }

    /**
     * Tutarı config.yml'deki biçime göre metne çevirir.
     */
    public String money(double amount) {
        String format = plugin.config().moneyFormat();
        return format.replace("{amount}", Numbers.money(amount));
    }

    public String prefix() {
        return prefix;
    }

    private String replace(String input, Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return input;
        }
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String key = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            result = result.replace(key, value);
        }
        return result;
    }
}

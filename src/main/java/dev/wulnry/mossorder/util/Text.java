package dev.wulnry.mossorder.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk ve metin biçimlendirme yardımcıları.
 * <p>
 * {@code &#RRGGBB} HEX renklerini ve klasik {@code &} renk kodlarını destekler.
 */
public final class Text {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /**
     * Verilen metindeki HEX ve klasik renk kodlarını uygular.
     */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    /**
     * Bir liste içindeki tüm satırlara renk uygular.
     */
    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    /**
     * Tüm renk kodlarını kaldırır (düz metin döndürür).
     */
    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }
}

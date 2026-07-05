package dev.wulnry.mossorder.util;

import org.bukkit.Material;

import java.util.Locale;

/**
 * {@link Material} için okunabilir görünen adlar üretir.
 * Örn: {@code GOLD_BLOCK} -> {@code Gold Block}
 */
public final class ItemNames {

    private ItemNames() {
    }

    /**
     * Materyalin okunabilir adını döndürür (baş harfleri büyük).
     */
    public static String display(Material material) {
        String raw = material.getKey().getKey();
        String[] parts = raw.split("_");
        StringBuilder builder = new StringBuilder(raw.length() + parts.length);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }
}

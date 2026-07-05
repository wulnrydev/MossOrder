package dev.wulnry.mossorder.util;

/**
 * Basit, Türkçe göreli zaman biçimlendirici.
 */
public final class Time {

    private Time() {
    }

    /**
     * Verilen zaman damgasından bu yana geçen süreyi okunabilir metne çevirir.
     * Örn: "az önce", "5 dk önce", "3 sa önce", "2 gün önce".
     */
    public static String ago(long epochMillis) {
        long diff = Math.max(0, System.currentTimeMillis() - epochMillis);
        long seconds = diff / 1000L;
        if (seconds < 60) {
            return "az önce";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + " dk önce";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + " sa önce";
        }
        long days = hours / 24L;
        return days + " gün önce";
    }
}

package dev.wulnry.mossorder.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Sayı ayrıştırma ve biçimlendirme yardımcıları.
 */
public final class Numbers {

    // DecimalFormat thread-safe değildir; her iş parçacığına ayrı bir örnek verilir.
    private static final ThreadLocal<DecimalFormat> MONEY = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US)));

    private Numbers() {
    }

    /**
     * Metni pozitif tam sayı olarak ayrıştırır. Nokta ve virgül binlik ayıraç
     * kabul edilerek temizlenir. Geçersizse boş döner.
     */
    public static OptionalInt parsePositiveInt(String input) {
        if (input == null) {
            return OptionalInt.empty();
        }
        String cleaned = input.trim().replace(".", "").replace(",", "").replace(" ", "");
        if (cleaned.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            long value = Long.parseLong(cleaned);
            if (value <= 0 || value > Integer.MAX_VALUE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) value);
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    /**
     * Metni pozitif ondalık sayı olarak ayrıştırır. Hem TR ({@code 1.234,56})
     * hem EN ({@code 1,234.56}) biçimleri doğru yorumlanır. Geçersizse boş döner.
     */
    public static OptionalDouble parsePositiveDouble(String input) {
        if (input == null) {
            return OptionalDouble.empty();
        }
        String cleaned = normalizeDecimal(input);
        if (cleaned.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            double value = Double.parseDouble(cleaned);
            if (!Double.isFinite(value) || value <= 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(value);
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Kullanıcı girdisindeki binlik/ondalık ayıraçlarını standart {@code .}
     * ondalık gösterimine çevirir.
     * <ul>
     *   <li>İki ayıraç türü de varsa: son görülen ondalık, diğeri binlik.</li>
     *   <li>Tek tür ayıraç birden çok kez geçiyorsa: binlik ayıraç (atılır).</li>
     *   <li>Tek tür ayıraç bir kez geçiyorsa: ondalık ayıraç.</li>
     * </ul>
     */
    private static String normalizeDecimal(String input) {
        String s = input.trim().replace(" ", "");
        if (s.isEmpty()) {
            return "";
        }
        int dotCount = count(s, '.');
        int commaCount = count(s, ',');
        if (dotCount == 0 && commaCount == 0) {
            return s;
        }
        if (dotCount > 0 && commaCount > 0) {
            char decimalSep = s.lastIndexOf('.') > s.lastIndexOf(',') ? '.' : ',';
            char thousandsSep = decimalSep == '.' ? ',' : '.';
            return s.replace(String.valueOf(thousandsSep), "").replace(decimalSep, '.');
        }
        char sep = dotCount > 0 ? '.' : ',';
        int sepCount = dotCount > 0 ? dotCount : commaCount;
        if (sepCount > 1) {
            return s.replace(String.valueOf(sep), "");
        }
        return s.replace(sep, '.');
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * Bir tutarı okunabilir biçime çevirir (ör: 12,345.5).
     */
    public static String money(double amount) {
        return MONEY.get().format(amount);
    }
}

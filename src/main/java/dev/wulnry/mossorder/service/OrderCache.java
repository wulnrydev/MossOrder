package dev.wulnry.mossorder.service;

import dev.wulnry.mossorder.model.Order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aktif siparişlerin bellek içi tek doğruluk kaynağı (single source of truth).
 * <p>
 * Yazma işlemleri ana thread üzerinde yapılır; sıralama/filtreleme için
 * anlık kopya (snapshot) alınarak asenkron thread'de işlenebilir. Bu sayede
 * sıralama ve arama, prompt gereği "cache üzerinde asenkron" gerçekleşir.
 */
public final class OrderCache {

    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();

    /**
     * Cache'i verilen sipariş listesiyle tamamen yeniler (yalnızca açılışta,
     * cache boşken kullanılır).
     */
    public void replaceAll(Collection<Order> newOrders) {
        orders.clear();
        for (Order order : newOrders) {
            orders.put(order.getId(), order);
        }
    }

    /**
     * Cache'i veritabanı anlık görüntüsüyle <b>yıkıcı olmadan</b> uzlaştırır
     * (çalışma sırasındaki periyodik eşitleme için).
     * <p>
     * Cache tek doğruluk kaynağıdır ve asenkron yazmalar veritabanının gerisinde
     * kalabileceğinden:
     * <ul>
     *   <li>Cache'te olmayan (ör. başka bir kaynakça eklenen) siparişler eklenir.</li>
     *   <li>{@code delivered} yalnızca <b>ileri</b> alınır; asla geri sarılmaz
     *       (uçuştaki bir yazma nedeniyle ilerlemenin gerilemesini ve olası
     *       çift teslimatı önler).</li>
     *   <li>Veritabanında görünmeyen yerel siparişler <b>silinmez</b> (uçuştaki
     *       bir ekleme/silme nedeniyle yeni siparişin kaybolmasını önler).</li>
     * </ul>
     */
    public void reconcile(Collection<Order> dbOrders) {
        for (Order dbOrder : dbOrders) {
            Order local = orders.get(dbOrder.getId());
            if (local == null) {
                orders.put(dbOrder.getId(), dbOrder);
            } else if (dbOrder.getDelivered() > local.getDelivered()) {
                local.setDelivered(dbOrder.getDelivered());
            }
        }
    }

    public void put(Order order) {
        orders.put(order.getId(), order);
    }

    public Order get(long id) {
        return orders.get(id);
    }

    public void remove(long id) {
        orders.remove(id);
    }

    public int size() {
        return orders.size();
    }

    /**
     * O anki tüm aktif siparişlerin bir kopyasını döndürür (asenkron sıralama
     * için güvenli).
     */
    public List<Order> snapshot() {
        return new ArrayList<>(orders.values());
    }

    /**
     * Belirli bir oyuncuya ait siparişlerin kopyasını döndürür.
     */
    public List<Order> snapshotByOwner(UUID owner) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getOwner().equals(owner)) {
                result.add(order);
            }
        }
        return result;
    }
}

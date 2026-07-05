# MossOrder

Yüksek performanslı, **sıfır lag** hedefli bir **alım siparişi (buy order)** ekonomi eklentisi.
Oyuncular istedikleri eşyalar için sipariş oluşturur, parayı peşin öder; diğer oyuncular bu
siparişleri karşılayarak eşyalarını satar ve anında ödeme alır.

- **Yapımcı:** wulnrydev
- **Sürüm:** Paper **1.20.4+** (api-version 1.20)
- **Java:** 17+
- **Bağımlılıklar:** [Vault](https://www.spigotmc.org/resources/vault.34315/) (zorunlu),
  [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (Tabela girişi için opsiyonel)

---

## Özellikler

- 🧾 **Alım siparişi sistemi** — Vault ile peşin ödeme; bakiye yetersizse sipariş oluşturulmaz.
- 🖱️ **Tamamen yapılandırılabilir menüler** — tüm boyutlar, yuvalar ve ikonlar `menus.yml` üzerinden.
- 🔃 **Sıralama** — En çok ödenen, En çok teslim eden, En son eklenen, Birim başına en çok para.
- 🔍 **Arama** — Tabela (Sign) GUI ile eşya ID'sine göre filtreleme.
- 📦 **Eşya seçici** — 1.20.4'teki tüm eşyalar açılışta **bir kez** asenkron önbelleğe alınır.
- 🧑‍💼 **Yönetici araçları** — komut veya menüden sağ tık ile sipariş kaldırma; kalan tutar sahibine iade.
- 📬 **Çevrimdışı teslimat** — sipariş sahibi çevrimdışıysa eşyalar saklanır, girişte teslim edilir.
- ⚡ **Asenkron mimari** — tüm veritabanı, sıralama ve katalog işlemleri ana thread dışında.

## Kurulum

1. `Vault` ve bir ekonomi eklentisi (ör. EssentialsX) kurulu olmalı.
2. (Opsiyonel) Tabela girişi için `ProtocolLib` kurun.
3. `MossOrder-1.0.0.jar` dosyasını `plugins/` klasörüne atın ve sunucuyu başlatın.
4. Oluşturulan `config.yml`, `messages.yml`, `menus.yml` dosyalarını dilediğiniz gibi düzenleyin.

## Derleme

```bash
mvn -DskipTests package
```

Çıktı: `target/MossOrder-1.0.0.jar` (HikariCP ve SQLite sürücüsü gölgelenmiş hâlde).

## Komutlar

| Komut | Açıklama | İzin |
|-------|----------|------|
| `/siparis` | Ana sipariş menüsünü açar | `mossorder.use` |
| `/siparis oluştur <eşya> <miktar> <fiyat>` | Hızlı sipariş verir | `mossorder.create` |
| `/siparisadmin sil <id>` | Bir siparişi kaldırır (iade eder) | `mossorder.admin` |
| `/siparisadmin reload` | Yapılandırmayı yeniler | `mossorder.admin` |

Takma adlar: `/sipariş`, `/order`, `/mossorder` • `/siparişadmin`, `/moadmin`.

## İzinler

| İzin | Varsayılan | Açıklama |
|------|-----------|----------|
| `mossorder.use` | herkes | Menü ve komut kullanımı |
| `mossorder.create` | herkes | Sipariş oluşturma |
| `mossorder.admin` | op | Sipariş kaldırma ve yönetim |

## Yapılandırma Notları

- **Veritabanı:** Varsayılan `SQLITE` (kurulum gerektirmez). `MYSQL` için `config.yml`'deki bilgileri
  doldurun; MySQL sürücüsü sunucuda yoksa eklenti uyarır ve SQLITE önerilir.
- **Metin girişi:** `input.method` = `AUTO` (ProtocolLib varsa Tabela, yoksa sohbet), `SIGN` veya `CHAT`.
  Tabela açılmıyorsa `CHAT` yaparak her koşulda çalışan sohbet girişine geçebilirsiniz.
- **Renk paleti:** `&#RRGGBB` HEX renkleri ve klasik `&` kodları desteklenir. Tüm dosyalar UTF-8'dir.

## Mimari

```
dev.wulnry.mossorder
├── command   /siparis ve /siparisadmin
├── config    config.yml, messages.yml, menus.yml sarmalayıcıları
├── economy   Vault köprüsü
├── input     Tabela (ProtocolLib) + Sohbet metin girişi
├── listener  Çevrimdışı teslimat
├── menu      Ana / Senin Siparişlerin / Oluştur / Eşya Seç menüleri
├── model     Order, SortMode, PendingDelivery
├── service   OrderService, OrderCache (bellek içi), ItemCatalog
├── storage   HikariCP (SQLite/MySQL) + asenkron depo
└── util      Renk, eşya, envanter ve sayı yardımcıları
```

Aktif siparişler bellek içi **OrderCache**'te tutulur (tek doğruluk kaynağı); sıralama ve filtreleme
bu önbellek üzerinde asenkron yapılır. Veritabanı yalnızca kalıcılık ve çevrimdışı teslimat için kullanılır.

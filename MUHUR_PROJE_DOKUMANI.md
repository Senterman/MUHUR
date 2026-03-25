# MÜHÜR — Tam Proje Dokümantasyonu
## (Revizyon 5 — Senaryo & Kart Sistemi Eklendi)

---

## 1. PROJE KİMLİĞİ

| Alan | Değer |
|---|---|
| Proje Adı | MÜHÜR (Mühür Damgası: Paralel Sınır) |
| GitHub Repo | github.com/Senterman/MUHUR |
| Geliştirici | BoneCastOfficial |
| Hedef Platform | Android (Casper VIA X30, Android 13) |
| Dil | Saf Java (Kotlin yok) |
| Render | GLSurfaceView + OpenGL ES 2.0 |
| Minimum SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Package | com.muhur.game |
| Versiyon | 0.2.0 (Senaryo sistemi eklendi) |

---

## 2. OYUN TANIMI

Reigns mekaniklerine sahip (kart tabanlı, sağa/sola kaydırma),
paralel bir Türkiye evreninde geçen, distopik politik strateji oyunu.
Yıl: 1999. Paralel Türkiye. Bir darbe oldu. Kimse konuşmuyor.

### 2.1 Ana Denge Barları (4 adet, her biri 0-100)

| Bar | Simge | %0 Sonucu | %100 Sonucu |
|---|---|---|---|
| HALK | ☭ | İsyan — Sokaklar alev alev, sen çoktan linç edildin | Demagog — Kalabalık seni tanrı sanıyor, bu daha tehlikeli |
| ORDU | ⚔ | Darbe — General Rıfat seni pencereden seyrede seyrede düşürüyor | Cunta — Ordu devleti yuttu, sen sadece dekoratasın |
| DİN | ✞ | Laik İsyan — Atatürk posteri taşıyan öğrenciler kapında | Teokrasi — Vaizler senden fetva bekliyor, sen ne bileceksin |
| PARA | ₺ | İflas — Hazine boş, çalışanlar maaş alamıyor, sen de dahil | Oligarşi — Üç aile her şeyi satın aldı, sen kira ödüyorsun |

**Toplam 8 farklı "felaket sonu" metni vardır.**

### 2.2 Hedef Süre ve Denge

- Her muhalefet yolu: **en az 20 dakika** (yaklaşık 60-80 karar)
- İktidar evresi: **en az 20 dakika** (yaklaşık 60-80 karar)
- Toplam oyun süresi: **60-90 dakika** (3 yol × 20dk + iktidar 20dk)
- Barlar 40-60 arasında kalırsa oyun güvenle ilerler
- 20'nin altı veya 80'nin üstü: tehlike bölgesi (UI kızarır/solar)

### 2.3 Alt Bilgi Çubuğu (Ekran Alt Kısmı)

```
[YIL: 2001]  [KARAR: 14]  [UNVAN: Müfettiş Yardımcısı]  [PARTI: Halkın Sesi]
```

**Unvan Basamakları (muhalefet evresinde):**
1. Yeni Üye (0-5 karar)
2. Müfettiş Yardımcısı (6-15 karar)
3. Bölge Temsilcisi (16-30 karar)
4. Parti Sözcüsü (31-50 karar)
5. Genel Sekreter (51-70 karar)
6. Lider Adayı (71+ karar)

---

## 3. DOSYA YAPISI (TAM)

```
MUHUR/
├── MUHUR_PROJE_DOKUMANI.md          ← Bu dosya
├── app/
│   └── src/main/
│       ├── java/com/muhur/game/
│       │   ├── GameActivity.java
│       │   ├── GameView.java
│       │   ├── GameRenderer.java    ← Render + font sistemi (Rev.4)
│       │   ├── GameState.java       ← Bar değerleri, karar sayacı, yıl
│       │   └── CardEngine.java      ← Kart yükleme, karar uygulama [YAPILACAK]
│       └── assets/
│           ├── cards/               ← Kart görselleri (character_card_X.png)
│           └── scenario/            ← JSON senaryo dosyaları
│               ├── opposition_1/
│               │   ├── cards.json
│               │   └── endings.json
│               ├── opposition_2/
│               │   ├── cards.json
│               │   └── endings.json
│               ├── opposition_3/
│               │   ├── cards.json
│               │   └── endings.json
│               └── ruling_era/
│                   ├── cards.json
│                   └── endings.json
└── src/
    ├── engine/
    │   ├── CardEngine.md            ← Kart motoru teknik spec
    │   └── BarSystem.md             ← Bar sistemi kuralları
    ├── data/
    │   └── characters.md            ← Tüm karakter tanımları + kart tasarım notları
    └── scenario/
        ├── Opposition_1/
        │   ├── README.md            ← Parti kimliği ve özet
        │   ├── acts.md              ← 5 perde × ~15 karar = 75 karar
        │   └── endings.md           ← 8 felaket sonu metni (bu parti için)
        ├── Opposition_2/
        │   ├── README.md
        │   ├── acts.md
        │   └── endings.md
        ├── Opposition_3/
        │   ├── README.md
        │   ├── acts.md
        │   └── endings.md
        └── Ruling_Era/
            ├── README.md
            ├── acts.md
            └── endings.md
```

---

## 4. KRİTİK TEKNİK NOTLAR

### 4.1 EGL Yapılandırması
```java
setEGLConfigChooser(8, 8, 8, 0, 0, 0);
// Casper VIA X30'da bu olmadan siyah/amber ekran sorunu oluşur
```

### 4.2 Touch Thread Safety
```java
queueEvent(() -> mRenderer.onTouch(action, x, y, time));
```

### 4.3 Font Sistemi (Rev.4)
- 5×9 piksel grid, Papers Please estetiği
- Büyük harf: satır 1-7
- Küçük harf: satır 2-7 veya 3-7 (inen harfler 3-8)
- Türkçe: Ç/Ş cedilla satır 8, Ğ/İ/Ö/Ü işaret satır 0
- drawString artık trUpper çağırmıyor → büyük/küçük her ikisi de destekleniyor

### 4.4 Kart JSON Formatı
```json
{
  "id": "opp1_card_003",
  "character": "inspector_mehmet",
  "year_offset": 1,
  "text": "Müfettiş Mehmet masanıza bir dosya koyuyor...",
  "choice_left": {
    "label": "Reddet",
    "effects": { "halk": -5, "ordu": +3, "din": 0, "para": -2 }
  },
  "choice_right": {
    "label": "İmzala",
    "effects": { "halk": +8, "ordu": -4, "din": +2, "para": +6 }
  },
  "prerequisite": null,
  "unlocks": ["opp1_card_010"]
}
```

### 4.5 State Machine (Genişletilmiş)
| Sabit | Değer | Açıklama |
|---|---|---|
| ST_SPLASH | 0 | BoneCastOfficial splash |
| ST_MENU | 1 | Ana menü |
| ST_CINEMA | 2 | 6 sinematik sahne |
| ST_SETTINGS | 3 | Ayarlar |
| ST_FONTTEST | 5 | Font test ekranı |
| ST_PARTY_SELECT | 6 | Muhalefet partisi seçimi [YAPILACAK] |
| ST_OPPOSITION | 7 | Muhalefet evresi kartları [YAPILACAK] |
| ST_RULING | 8 | İktidar evresi kartları [YAPILACAK] |
| ST_GAME_OVER | 9 | Felaket sonu ekranı [YAPILACAK] |
| ST_GAME | 4 | Geçici placeholder |

---

## 5. SENARYO SİSTEMİ

### 5.1 Üç Muhalefet Partisi

| # | Parti Adı | Kimlik | Renk Tonu | Temel Kaygısı |
|---|---|---|---|---|
| 1 | **Halkın Sesi Partisi (HSP)** | Sosyal demokrat, biraz Kemalist, biraz naif | Mavi | Halk ve Para |
| 2 | **Millet ve Nizam Partisi (MNP)** | Muhafazakâr milliyetçi, pragmatist | Kırmızı | Din ve Ordu |
| 3 | **Yeni Cumhuriyet Hareketi (YCH)** | Liberal, şehirli, teknokratik | Sarı | Para ve Halk |

### 5.2 Dallanma Mantığı

Her senaryo **5 perde**'den oluşur. Her perde ~12-16 karar içerir.
Toplam: 60-80 karar → 20 dakika (ortalama 15-20 sn/karar).

```
PERDE 1 — Başlangıç (Yıl 1999, Karar 1-15)
  └── Her kart bağımsız, bar kontrolü yok, hikaye tanıtımı

PERDE 2 — Örgütlenme (Yıl 2000-2001, Karar 16-30)
  ├── Bar > 70 veya < 30 → Uyarı kartları gelir
  └── İlk dallanma noktası (2 farklı alt yol)

PERDE 3 — Kriz (Yıl 2001-2002, Karar 31-50)
  ├── Büyük olay (her parti için farklı)
  ├── Bar > 80 veya < 20 → Felaket tetiklenebilir
  └── İkinci dallanma (hangi alt yoldasın bağlı)

PERDE 4 — Seçim Kampanyası (Yıl 2002, Karar 51-65)
  └── Seçim mekanizması: son 10 kararın HALK ortalaması > 50 ise kazanırsın

PERDE 5 — Seçim Gecesi (Yıl 2002, Karar 66-75)
  ├── Kazan → ST_RULING (iktidar evresi)
  └── Kaybet → Seçim sonrası devam (MHP barajı gibi) ya da oyun biter
```

---

## 6. KARAKTER KATALOĞU

Tüm detaylar `src/data/characters.md` dosyasında.
Özet tablo:

| ID | İsim | Hangi Parti | Tür | Kart Görünümü |
|---|---|---|---|---|
| inspector_mehmet | Müfettiş Mehmet Yılmaz | HSP | Tekrarlayan | Ortayaşlı, bıyıklı, dosya tutuyor |
| secretary_leyla | Sekreter Leyla Hanım | HSP | Tekrarlayan | Gözlüklü, saçları topuz, kalem kulağında |
| general_rifat | General Rıfat Bey | Tüm partiler | Tehdit | Üniforma, sert bakış, tek kaş havada |
| imam_nureddin | İmam Nureddin Efendi | MNP | Tekrarlayan | Cüppeli, tespihli, tatlı dilli |
| journalist_can | Gazeteci Can Arslan | Tüm partiler | Joker | Kravatsız, sigara, not defteri |
| spy_x | "Bay X" | Tüm partiler | Sürpriz | Silik yüz, gri palto, hiç gülümsemiyor |
| treasurer_hasan | Hazinedar Hasan Bey | YCH | Tekrarlayan | Takım elbise, hesap makinesi, endişeli |
| student_zeynep | Öğrenci Zeynep | HSP/YCH | Joker | Genç, ateşli, bildiri tutuyor |
| old_voter_ali | Yaşlı Ali Amca | Tüm partiler | Halk sesi | Kasket, değnek, kararsız bakış |

---

## 7. RENK PALETİ & UI

```java
float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f};  // #0d0a05
float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f};  // #d4a853
float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f};
float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f};
float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f};
float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f};

// Bar renkleri (tehlike)
float[] C_DANGER_HIGH = {0.831f, 0.200f, 0.100f, 1.0f}; // kırmızı — bar > 80
float[] C_DANGER_LOW  = {0.200f, 0.200f, 0.600f, 1.0f}; // mavi — bar < 20
float[] C_WARNING     = {0.831f, 0.659f, 0.100f, 1.0f}; // sarı — 20-30 veya 70-80
```

---

## 8. YAPILACAKLAR

### 🔴 Öncelik 1 — Altyapı
- [ ] `CardEngine.java` — JSON okuma, kart yükleme, karar uygulama
- [ ] `GameState.java` güncellemesi — bar değerleri, yıl, karar sayısı, unvan
- [ ] ST_PARTY_SELECT ekranı
- [ ] ST_OPPOSITION render döngüsü (kart göster, kaydır, efekt uygula)
- [ ] Bar UI çizimi (4 bar, renk değişimi)
- [ ] Alt bilgi çubuğu (yıl/karar/unvan)

### 🟡 Öncelik 2 — Senaryo İçeriği
- [ ] Opposition_1 (HSP) — acts.md tamamlandı ✓
- [ ] Opposition_2 (MNP) — acts.md tamamlandı ✓
- [ ] Opposition_3 (YCH) — acts.md tamamlandı ✓
- [ ] Ruling_Era — acts.md tamamlandı ✓
- [ ] 8 felaket sonu metni (her parti için ayrı ton)
- [ ] cards.json dosyaları (her senaryo için)

### 🟢 Öncelik 3 — Görsel
- [ ] 9 karakter kartı çizimi (character_card_X.png)
- [ ] Muhalefet arka planı (opposition_bg.png)
- [ ] Kart çerçevesi tasarımı
- [ ] Bar animasyonları

---

## 9. GEÇMİŞ SORUNLAR VE ÇÖZÜMLER

| Sorun | Çözüm |
|---|---|
| NDK EGL amber ekran (Casper VIA X30) | Java + GLSurfaceView'e geçildi |
| Logcat erişimi (LADB/Shizuku yok) | UncaughtExceptionHandler → crash dosyası |
| PNG stored-block decode (C++) | Java BitmapFactory |
| Touch thread safety | queueEvent() ile GL thread'ine ilet |
| D/O harfi aynı görünmesi | D'de kısa yatay barlar + eğim segmentleri |
| Türkçe büyük harf dönüşümü | trUpper() metodu + drawString'den kaldırıldı |
| Küçük harf eksikliği | Her harf için ayrı case, 3 yükseklik seviyesi |
| `)` parantez boş çıkması | col indeksleri düzeltildi |
| `"` tırnak eksik | col1+col3 satır 1-2 iki kısa dikey |

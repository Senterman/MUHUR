# MÜHÜR — Tam Proje Dokümantasyonu
## (Yeni Sohbet için Referans Şablon)

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
| Versiyon | 0.1.0 |

---

## 2. OYUN TANIMI

Reigns mekaniklerine sahip (kart tabanlı, sağa/sola kaydırma),
paralel bir Türkiye evreninde geçen, distopik politik strateji oyunu.

### 2.1 İstatistikler (0-100 arası)
- HALK
- İNANÇ
- EKONOMİ
- ORDU

### 2.2 Oyun Akışı
```
Splash Ekranı (120 frame, BoneCastOfficial)
    ↓
Sinematik Giriş (6 sahne, daktilo efekti)
    ↓
Ana Menü (Yeni Oyun / Devam Et / Ayarlar / Çıkış)
    ↓
Taraf Seçimi [YAPILACAK]
  ├── İktidar → İktidar Evresi
  └── Muhalefet → Seçim Evresi → (Kazanırsa) İktidar Evresi
    ↓
Kart Sistemi (sağ/sol kaydırma) [YAPILACAK]
```

### 2.3 Tasarım Felsefesi
- Papers Please estetiği: pikselli, distopik, bürokratik
- Renk paleti: #0d0a05 (koyu siyah) + #d4a853 (altın sarısı)
- Pixel font: segment tabanlı rect() çizimleri
- CRT tarama çizgileri efekti
- Motto: "Kaderin, mührünün ucunda."

---

## 3. DOSYA YAPISI (TAM)

```
MUHUR/
├── .github/
│   └── workflows/
│       └── main.yml                      ← CI/CD: APK build (NDK yok)
├── app/
│   ├── build.gradle                      ← Modül gradle (Java, appcompat)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   ├── logo.png              ← PLACEHOLDER — gerçek ile değiştir
│           │   ├── menu_bg.png           ← PLACEHOLDER
│           │   ├── story_bg_1.png        ← Masa/bürokrasi sahnesi
│           │   ├── story_bg_2.png        ← Büyük bina/kapı sahnesi
│           │   ├── story_bg_3.png        ← Saat + mühür sahnesi
│           │   ├── story_bg_4.png        ← Şehir/kalabalık sahnesi
│           │   ├── story_bg_5.png        ← Bodrum/matbaa sahnesi
│           │   └── story_bg_6.png        ← Masa + kağıt sahnesi
│           ├── java/com/muhur/game/
│           │   ├── GameActivity.java     ← Activity, crash handler
│           │   ├── GameView.java         ← GLSurfaceView
│           │   ├── GameRenderer.java     ← Tüm render + oyun mantığı
│           │   └── GameState.java        ← İstatistikler, kart sistemi için
│           └── res/
│               ├── mipmap-hdpi/ic_launcher.png
│               └── values/strings.xml
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle
├── settings.gradle
├── gradle.properties
└── MUHUR_PROJE_DOKUMANI.md               ← Bu dosya
```

---

## 4. KRİTİK TEKNİK NOTLAR

### 4.1 EGL Yapılandırması
```java
setEGLConfigChooser(8, 8, 8, 0, 0, 0);
// R=8 G=8 B=8 → 24-bit renk
// A=0          → ALPHA YOK (opaque surface)
// Depth=0      → 2D oyun, derinlik tamponu gereksiz
// Casper VIA X30'da bu olmadan siyah/amber ekran sorunu oluşur
```

### 4.2 Touch Thread Safety
```java
// GameView.java:
queueEvent(() -> mRenderer.onTouch(action, x, y, time));
// Direkt renderer metodunu çağırmak thread-safe DEĞİL
```

### 4.3 Crash Handler
```
Android 9-  : /sdcard/muhur_crash.txt (WRITE_EXTERNAL_STORAGE gerekir)
Android 10+ : /sdcard/Android/data/com.muhur.game/files/muhur_crash.txt
```

### 4.4 Koordinat Sistemi
```
(0,0) = Sol üst
(W,H) = Sağ alt
Shader dönüşümü:
  nx = (x/W)*2 - 1      → [-1, +1]
  ny = -(y/H)*2 + 1     → [+1, -1]  (Y ekseni çevrilir)
```

---

## 5. SHADER ÜNİFORM/ATTRİBÜT İSİMLERİ

| İsim | Tip | Açıklama |
|---|---|---|
| `aPos` | attribute vec2 | Piksel koordinatı |
| `uRes` | uniform vec2 | Ekran boyutu (W, H) |
| `uColor` | uniform vec4 | RGBA renk |
| `aUV` | attribute vec2 | Texture koordinatı |
| `uTex` | uniform sampler2D | Texture birimi |
| `uAlpha` | uniform float | Texture şeffaflığı |

---

## 6. STATE MAKİNESİ

| Sabit | Değer | Açıklama |
|---|---|---|
| ST_SPLASH | 0 | BoneCastOfficial + ilerleme çubuğu (120 frame) |
| ST_CINEMA | 1 | 6 sinematik sahne, daktilo efekti |
| ST_MENU | 2 | Ana menü (4 buton) |
| ST_SETTINGS | 3 | Ses açık/kapalı, FPS 30/60 |
| ST_GAME | 4 | Oyun (ilerleyen sürümde) |

---

## 7. RENK PALETİ

```java
float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f};  // #0d0a05 — arka plan
float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f};  // #d4a853 — altın
float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f};  // Koyu altın
float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f};  // Buton arka planı
float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f};  // Basılı buton
float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f};  // Pasif buton
```

---

## 8. SİNEMATİK SİSTEM

### Sahne Listesi
| # | Asset | Metin |
|---|---|---|
| 1 | story_bg_1.png | "YIL 1999. PARALEL TURKIYE..." |
| 2 | story_bg_2.png | "YENİ BIR DUZEN ILAN EDILDI..." |
| 3 | story_bg_3.png | "BUROKRATIN MASASINDA..." |
| 4 | story_bg_4.png | "HALK SORMUYOR, HALK BEKLIYOR..." |
| 5 | story_bg_5.png | "GOLGEDEKI SESLER BUYUYOR..." |
| 6 | story_bg_6.png | "ISTE O MUHUR..." |

### Daktilo Efekti
- Her **60ms**'de 1 karakter görünür
- Ekrana dokunulunca metin **anında** tamamlanır
- Metin bittikten sonra **DEVAM** butonu çıkar
- **ATLA** butonu: **3 saniye** basılı tutunca tüm sinematik atlanır

---

## 9. ASSET PLACEHOLDER'LAR

`assets/` klasöründeki dosyalar **placeholder** (düz renkli PNG).
Gerçek oyun grafikleriyle değiştirilmeli:

| Dosya | Önerilen boyut | İçerik |
|---|---|---|
| logo.png | 256×256 | BoneCastOfficial logosu |
| menu_bg.png | 1080×1920 | Menü arka planı |
| story_bg_1.png | 1080×1920 | Masa/bürokrasi sahnesi |
| story_bg_2.png | 1080×1920 | Büyük bina/kapı sahnesi |
| story_bg_3.png | 1080×1920 | Saat + mühür sahnesi |
| story_bg_4.png | 1080×1920 | Şehir/kalabalık sahnesi |
| story_bg_5.png | 1080×1920 | Bodrum/matbaa sahnesi |
| story_bg_6.png | 1080×1920 | Masa + kağıt sahnesi |

---

## 10. YAPILACAKLAR

### 🔲 Öncelikli
- [ ] Kart sistemi (sağ/sol swipe mekaniği)
- [ ] İstatistik barları (Halk/İnanç/Ekonomi/Ordu)
- [ ] Taraf seçimi ekranı (İktidar / 3x Muhalefet)

### 🔲 Sonraki Sürüm
- [ ] Seçim evresi mekaniği
- [ ] İktidar evresi mekaniği
- [ ] Kayıt sistemi (SharedPreferences)
- [ ] Ses sistemi (SoundPool)
- [ ] Kart veritabanı (JSON tabanlı)
- [ ] Türkçe/İngilizce i18n

---

## 11. GEÇMIŞ SORUNLAR VE ÇÖZÜMLER

| Sorun | Çözüm |
|---|---|
| NDK EGL amber ekran (Casper VIA X30) | Java + GLSurfaceView'e geçildi |
| Logcat erişimi (LADB/Shizuku yok) | UncaughtExceptionHandler → crash dosyası |
| PNG stored-block decode (C++) | Java BitmapFactory (tüm PNG formatları) |
| Touch thread safety | queueEvent() ile GL thread'ine ilet |
| --whole-archive ANativeActivity_onCreate | Java'ya geçilince gerek kalmadı |

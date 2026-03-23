<div align="center">

# MÜHÜR
### *Mühür Damgası: Paralel Sınır*

**Distopik politik strateji oyunu — Android**

*"Kaderin, mührünün ucunda."*

---

![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-brightgreen?style=flat-square&logo=android)
![Language](https://img.shields.io/badge/Dil-Java-orange?style=flat-square&logo=java)
![OpenGL](https://img.shields.io/badge/Render-OpenGL%20ES%202.0-blue?style=flat-square)
![Version](https://img.shields.io/badge/Versiyon-0.1.0--alpha-yellow?style=flat-square)
![License](https://img.shields.io/badge/Lisans-MIT-lightgrey?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/Senterman/MUHUR/main.yml?style=flat-square&label=APK%20Build)

</div>

---

## 📖 Hakkında

**MÜHÜR**, paralel bir Türkiye evreninde geçen, kart tabanlı distopik politik strateji oyunudur. [Reigns](https://store.steampowered.com/app/474750/Reigns/) mekaniklerinden ilham alan oyunda kartları sağa veya sola kaydırarak kararlar alır, dört temel dengeyi korumaya çalışırsınız.

Yıl **1999**. Paralel Türkiye'de bir darbe oldu. Ama kimse konuşmuyor.  
Masanızda bir mühür var. O mühür sizin.

> Bu oyun açık kaynaklıdır. Katkıda bulunmak isteyen herkese açıktır.

---

## 🎮 Oyun Mekaniği

### Dört Temel Denge (0–100)

| İstatistik | Açıklama |
|-----------|----------|
| 👥 **HALK** | Halkın desteği ve memnuniyeti |
| 🕌 **İNANÇ** | Dini ve ideolojik baskı |
| 💰 **EKONOMİ** | Devlet kasası ve piyasa |
| ⚔️ **ORDU** | Askeri sadakat ve güç |

Herhangi bir istatistik **0** veya **100**'e ulaşırsa oyun biter.

### Oyun Akışı

```
🎬 Splash (BoneCastOfficial)
        ↓
📽️  Sinematik Giriş  (6 sahne — daktilo efekti)
        ↓
🗂️  Ana Menü
        ↓
⚖️  Taraf Seçimi          [geliştirme aşamasında]
   ├── İktidar  →  İktidar Evresi
   └── Muhalefet  →  Seçim Evresi  →  İktidar Evresi
        ↓
🃏  Kart Sistemi (sağa/sola kaydır)  [geliştirme aşamasında]
```

---

## 📸 Ekran Görüntüleri

> *Gerçek ekran görüntüleri eklendikçe burası güncellenecek.*

---

## ⬇️ İndirme

En güncel debug APK'sını [**Releases**](https://github.com/Senterman/MUHUR/releases) sayfasından veya [**Actions**](https://github.com/Senterman/MUHUR/actions) sekmesinden indirabilirsiniz.

| Kanal | Açıklama |
|-------|----------|
| [Releases](https://github.com/Senterman/MUHUR/releases) | Kararlı sürümler |
| [Actions → Artifacts](https://github.com/Senterman/MUHUR/actions) | Her commit'teki otomatik build |

---

## 🛠️ Derleme

### Gereksinimler

- **Android Studio** Hedgehog (2023.1.1) veya üstü
- **JDK 17**
- **Android SDK** — `platforms;android-34` + `build-tools;34.0.0`
- NDK **gerektirmez** — saf Java

### Adımlar

```bash
# 1. Repoyu klonla
git clone https://github.com/Senterman/MUHUR.git
cd MUHUR

# 2. Android Studio ile aç
#    File → Open → MUHUR klasörünü seç

# 3. Komut satırından derlemek için
chmod +x gradlew
./gradlew assembleDebug

# 4. APK çıktı konumu
# app/build/outputs/apk/debug/app-debug.apk
```

### CI/CD

Her `main` veya `master` push'unda GitHub Actions otomatik olarak APK derler.  
Badge'e tıklayarak son build durumunu görebilirsiniz.

---

## 🏗️ Proje Yapısı

```
MUHUR/
├── .github/workflows/main.yml          # CI/CD — APK otomatik build
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/                         # Oyun görselleri (PNG)
│   │   ├── logo.png
│   │   ├── menu_bg.png
│   │   └── story_bg_1..6.png
│   └── java/com/muhur/game/
│       ├── GameActivity.java           # Activity yönetimi, crash handler
│       ├── GameView.java               # GLSurfaceView + touch iletimi
│       ├── GameRenderer.java           # Render döngüsü + oyun mantığı
│       └── GameState.java             # İstatistikler, ileride kart sistemi
└── MUHUR_PROJE_DOKUMANI.md            # Teknik referans dökümanı
```

### Teknik Seçimler

| Konu | Karar | Neden |
|------|-------|-------|
| Render | `GLSurfaceView` + OpenGL ES 2.0 | NDK/EGL Casper cihazında amber ekran sorunu oluşturuyordu |
| Dil | Saf Java (Kotlin yok) | Minimum bağımlılık |
| Font | Segment tabanlı `rect()` çizimleri | Gerçek piksel font, bitmap atlas yok |
| EGL | `setEGLConfigChooser(8,8,8,0,0,0)` | Alpha=0 — opaque surface (siyah ekran önlemi) |
| Hata yakalama | `UncaughtExceptionHandler` → dosya | Logcat gerektirmeden hata okuma |

---

## 🎨 Tasarım Dili

**Papers Please** estetiğinden ilham alınmıştır: bürokratik, pikselli, distopik.

```
Arka plan  →  #0d0a05  ██  Koyu siyah
Ana renk   →  #d4a853  ██  Altın sarısı
```

- CRT tarama çizgisi (scanlines) efekti
- Segment tabanlı özel piksel font
- Daktilo yazı efekti (60ms/karakter)

---

## 🗺️ Yol Haritası

- [x] Proje altyapısı (Java + GLSurfaceView)
- [x] GitHub Actions CI/CD
- [x] Splash ekranı
- [x] Sinematik sistem (daktilo efekti)
- [x] Ana menü
- [x] Ayarlar ekranı
- [ ] Taraf seçimi ekranı
- [ ] Kart sistemi (sağa/sola swipe)
- [ ] İstatistik barları (UI)
- [ ] Seçim & iktidar evresi mekaniği
- [ ] JSON tabanlı kart veritabanı
- [ ] Kayıt sistemi (SharedPreferences)
- [ ] Ses sistemi (SoundPool)
- [ ] Türkçe / İngilizce dil desteği

---

## 🤝 Katkı

Bu proje açık kaynaklıdır, her türlü katkıya açıktır.

```
1. Fork yap
2. Feature branch oluştur  →  git checkout -b ozellik/karti-sistemi
3. Değişiklikleri commit et →  git commit -m "Kart sistemi eklendi"
4. Branch'i push et        →  git push origin ozellik/kart-sistemi
5. Pull Request aç
```

Büyük değişiklikler için önce bir [Issue](https://github.com/Senterman/MUHUR/issues) açarak tartışmaya başlamanızı öneririz.

### Katkı Alanları

- 🎨 **Sanat** — Karakter, arka plan, arayüz görselleri
- 🃏 **Kart içeriği** — Oyun senaryoları, diyaloglar
- 🌍 **Çeviri** — İngilizce ve diğer diller
- 🐛 **Bug fix** — Hata düzeltmeleri
- ⚡ **Performans** — Render optimizasyonları

---

## 📋 Teknik Referans

Projenin tüm teknik detayları, shader kodları, state makinesi diyagramları ve geliştirici notları için [`MUHUR_PROJE_DOKUMANI.md`](./MUHUR_PROJE_DOKUMANI.md) dosyasına bakın.

---

## 📄 Lisans

Bu proje **MIT Lisansı** ile lisanslanmıştır. Detaylar için [`LICENSE`](./LICENSE) dosyasına bakın.

---

<div align="center">

**BoneCastOfficial** tarafından geliştirilmektedir.

*"Kaderin, mührünün ucunda."*

</div>

# 🔏 MÜHÜR

> Motorsuz, saf C++ ile yazılmış, Android Native Activity tabanlı  
> paralel Türkiye evreninde geçen politik strateji oyunu.

---

## 📁 Klasör Yapısı

```
MUHUR/
├── .github/
│   └── workflows/
│       └── main.yml              ← CI/CD: Otomatik APK build
│
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml   ← NativeActivity tanımı
│       ├── assets/
│       │   └── logo.png          ← ⚠️ GIT'E EKLEYİN (henüz yok)
│       ├── cpp/
│       │   ├── CMakeLists.txt    ← NDK build sistemi
│       │   ├── main.cpp          ← Giriş noktası, OpenGL ES render
│       │   └── png_loader.h      ← Minimal PNG decoder
│       └── res/values/
│           └── strings.xml
│
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle
├── app/build.gradle
├── settings.gradle
├── gradle.properties
├── local.properties.template     ← local.properties oluştur buradan
└── .gitignore
```

---

## 🚀 Hızlı Başlangıç

### 1. Repo'yu klonla

```bash
git clone https://github.com/KULLANICI/MUHUR.git
cd MUHUR
```

### 2. `local.properties` dosyasını oluştur

```bash
cp local.properties.template local.properties
# İçindeki sdk.dir yolunu kendi Android SDK yolunla güncelle
```

### 3. Logo'yu ekle

`app/src/main/assets/logo.png` olarak bir PNG dosyası koy.  
RGBA veya RGB, 8-bit olmalı. Önerilen boyut: 512×512.

> **Geçici çözüm:** CI'da placeholder otomatik oluşturulur,  
> ama kendi logonu koyunca gerçek görsel çalışır.

### 4. Debug APK build

```bash
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### 5. Cihaza yükle

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 GitHub Actions CI/CD

| Tetikleyici | İş | Çıktı |
|---|---|---|
| `push` → `main`/`develop` | Debug build | Artifact (30 gün) |
| `push` → `v*` tag | Release build + imzalama | GitHub Release + Artifact |

### Release için Secrets ayarla

Repo → Settings → Secrets → Actions → New repository secret:

| Secret | Değer |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 muhur.jks` çıktısı |
| `KEY_ALIAS` | Keystore alias adı |
| `KEY_PASSWORD` | Anahtar şifresi |
| `STORE_PASSWORD` | Keystore şifresi |

Yeni keystore oluşturmak için:
```bash
keytool -genkey -v -keystore muhur.jks \
  -alias muhur -keyalg RSA -keysize 2048 -validity 10000
```

---

## 🎮 Oyun Mimarisi (Planlanan)

```
android_main()
    └── Olay Döngüsü (ALooper)
            ├── APP_CMD_INIT_WINDOW  → EGL + OpenGL ES init
            ├── INPUT_EVENT          → Dokunma → Kart kaydırma
            └── Render Frame
                    ├── Başlangıç Menüsü
                    ├── Hikaye Sineması (skip özellikli)
                    ├── Taraf Seçimi (İktidar / 3× Muhalefet)
                    └── Kart Döngüsü
                            ├── Seçimi Kazanma Evresi (Muhalefet)
                            └── İktidar Evresi
```

İstatistikler: **Halk · Din · Ekonomi · Ordu** (0–100)

---

## 📦 Bağımlılıklar

| Kütüphane | Nereden | Açıklama |
|---|---|---|
| `android_native_app_glue` | Android NDK (built-in) | Olay döngüsü |
| `EGL` | Android sistem | OpenGL context |
| `GLESv2` | Android sistem | Render |
| `android` | Android sistem | Assets, pencere |
| `log` | Android sistem | Loglama |
| `z` (zlib) | Android sistem | PNG decompress (ilerisi) |

> **Öneri:** Gerçek PNG desteği için [`stb_image.h`](https://github.com/nothings/stb)  
> dosyasını `app/src/main/cpp/` altına kopyalayın ve  
> `png_loader.h` içindeki `decodePNG` fonksiyonunu  
> `stbi_load_from_memory` ile değiştirin.

---

## 🔮 Sonraki Adımlar

- [ ] `stb_image.h` entegrasyonu (tam PNG desteği)
- [ ] Kart veri yapısı (`card_engine.h`)
- [ ] İstatistik sistemi (`stat_system.h`)
- [ ] Swipe gesture tanıma (sol/sağ kaydırma)
- [ ] Ses sistemi (OpenSL ES veya AAudio)
- [ ] Türkçe/İngilizce dil dosyaları (JSON tabanlı i18n)
- [ ] Seçim mekaniği (iktidar vs muhalefet akışı)

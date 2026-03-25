# Muhalefet 1: Halkın Sesi Partisi (HSP)
## src/scenario/Opposition_1/README.md

---

## Parti Kimliği

**Tam Ad:** Halkın Sesi Partisi  
**Kısa Ad:** HSP  
**Slogan:** "Sesinizi duyuruyoruz."  
**İdeoloji:** Sosyal demokrat, Kemalist damarı var ama örtük, laik  
**Taban:** Şehir işçileri, memurlar, öğretmenler, üniversite gençliği  
**Zayıf Noktası:** Para kaynağı yok, partili ağalar birbirini çekemiyor  
**Güçlü Noktası:** Sokak tabanı geniş, medyada sempati var  

---

## Senaryonun Tonu

HSP yolu en "insani" yoldur. Karakterler daha sıcak, krizler daha kişisel.
Para her zaman az, halk her zaman yüksek ama bu seni kurtarmıyor —
çünkü halk seninle aynı fikirde değil, sadece seni seviyor.

Mizah: Bürokratik absürd. Müfettiş Mehmet yanlış dosyayı getiriyor,
Leyla Hanım her şeyi biliyor ama söylemiyor, Zeynep herkesi kızdırıyor.

---

## Oyuncunun Unvan Basamakları (HSP'ye Özgü)

| Karar | Unvan |
|---|---|
| 0-5 | Yeni Üye |
| 6-15 | Müfettiş Yardımcısı |
| 16-30 | Bölge Temsilcisi |
| 31-50 | Parti Sözcüsü |
| 51-70 | Genel Sekreter |
| 71+ | Lider Adayı |

---

## Ana Karakterler (Bu Yolda Görünenler)

- **inspector_mehmet** — Her perdede en az 2-3 kez gelir
- **secretary_leyla** — Perde 1'den itibaren, bilgi kaynağı
- **student_zeynep** — Perde 2'den itibaren, zaman zaman seni sıkıştırır
- **journalist_can** — Perde 3'te ilk kez gelir, tehlikeli sorular
- **general_rifat** — Perde 3 krizinde bir kez gelir (tehdit)
- **old_voter_ali** — Perde 4'te, seçim kampanyasında
- **spy_x** — Perde 3 veya 4'te bir kez, hiç beklenmedik anda

---

## Bar Etkisi Profili

HSP kararları genellikle:
- **HALK:** Yüksek etki (±8 ile ±15 arası)
- **PARA:** Orta negatif etki (kötü kararlar ağır kaybettirir)
- **DİN:** Düşük etki (nadiren değişir, ama değişince beklenmedik)
- **ORDU:** Düşük negatif baskı (ordu HSP'yi sevmiyor, bu sabit)

**Tehlike profili:** PARA düşüşü ve ORDU düşüşü en büyük riskler.

---

## Dallanma Özeti

```
PERDE 1 (Karar 1-15) — Tanışma
  Doğrusal, tüm oyuncular aynı kartları görür.

PERDE 2 (Karar 16-30) — İlk Seçim
  Dal A: Zeynep'i destekle → Gençlik kolu aktifleşir, HALK yükselir, DİN düşer
  Dal B: Zeynep'i fren et → Parti içi huzur, HALK düşer, PARA artar

PERDE 3 (Karar 31-50) — Kriz: "Müfettiş Dosyası"
  Mehmet yolsuzluk dosyası getiriyor.
  İfşa et → Büyük HALK artışı, PARA çöküşü, General Rıfat tehdidi
  Göm → PARA korunur, DİN artar, ama Zeynep seninle işi bitirir

PERDE 4 (Karar 51-65) — Seçim Kampanyası
  Ali Amca ve sokak sahneleri.
  Strateji: vaatlerin HALK barını 55+ tutarsa seçim kazanılır.

PERDE 5 (Karar 66-75) — Seçim Gecesi
  Kazan → ST_RULING
  Kaybet → "Muhalefette Devam" (5 ekstra karar, ikinci şans)
```

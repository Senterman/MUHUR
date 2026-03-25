# Kart Motoru — Teknik Spec
## src/engine/CardEngine.md

---

## CardEngine.java — Yapılacak İşlevler

### 1. JSON Okuma

```java
// assets/scenario/opposition_1/cards.json okunur
// Her kart bir Card objesi olarak parse edilir
public class Card {
    String id;
    String character;       // karakter ID (inspector_mehmet vb.)
    String text;            // kart metni
    Choice choiceLeft;      // sol kaydırma
    Choice choiceRight;     // sağ kaydırma
    String prerequisite;    // hangi kart tamamlandıktan sonra gelebilir
    String[] unlocks;       // bu kart sonrası hangi kartlar açılır
    int yearOffset;         // bu kart kaç yıl ilerletir (0 veya 1)
}

public class Choice {
    String label;           // kısa etiket (15 karakter max)
    int halk;               // ±değer
    int ordu;
    int din;
    int para;
}
```

### 2. Kart Seçim Algoritması

```java
// Her kararda:
// 1. Mevcut dal durumuna bak (hangi kartlar açıldı)
// 2. Önkoşulları karşılayan kartları filtrele
// 3. Ağırlıklı rastgele seç (acil kartlar önce gelir)
// 4. Acil kart: herhangi bir bar < 25 veya > 75 ise

private Card selectNextCard(List<String> completedCards, GameState state) {
    List<Card> available = getAvailableCards(completedCards);
    
    // Tehlike kartları önce
    Card urgentCard = checkUrgentCards(state);
    if (urgentCard != null) return urgentCard;
    
    // Normal sıra
    return available.get(random.nextInt(available.size()));
}
```

### 3. Bar Güncelleme

```java
// Karar sonrası:
public void applyChoice(Choice choice, GameState state) {
    state.halk = clamp(state.halk + choice.halk, 0, 100);
    state.ordu = clamp(state.ordu + choice.ordu, 0, 100);
    state.din  = clamp(state.din  + choice.din,  0, 100);
    state.para = clamp(state.para + choice.para,  0, 100);
    
    state.decisionCount++;
    state.year += choice.yearOffset; // çoğu kart 0, önemliler 1
    
    checkGameOver(state);
}

private void checkGameOver(GameState state) {
    if (state.halk == 0) triggerEnding("halk_0");
    if (state.halk == 100) triggerEnding("halk_100");
    // ... diğerleri
}
```

### 4. Unvan Hesaplama

```java
public String getTitle(int decisionCount, int gamePhase) {
    if (gamePhase == PHASE_OPPOSITION) {
        if (decisionCount <= 5)  return "Yeni Üye";
        if (decisionCount <= 15) return "Müfettiş Yardımcısı";
        if (decisionCount <= 30) return "Bölge Temsilcisi";
        if (decisionCount <= 50) return "Parti Sözcüsü";
        if (decisionCount <= 70) return "Genel Sekreter";
        return "Lider Adayı";
    } else { // PHASE_RULING
        if (decisionCount <= 10) return "Koalisyon Ortağı";
        if (decisionCount <= 25) return "Başbakan Yardımcısı";
        if (decisionCount <= 45) return "Başbakan";
        if (decisionCount <= 60) return "Kalıcı Başbakan";
        return "Tek Adam";  // tehlike sinyali — UI kırmızı
    }
}
```

### 5. Seçim Mekanizması

```java
// Perde 4 sonu — son 10 kararın HALK ortalaması
public boolean checkElectionResult(List<Integer> recentHalkValues) {
    int last10 = recentHalkValues.subList(
        Math.max(0, recentHalkValues.size() - 10), 
        recentHalkValues.size()
    ).stream().mapToInt(i -> i).average().orElse(0);
    
    return last10 >= 55; // HSP/MNP
    // YCH: (recentHalk + recentPara) / 2 >= 55
}
```

---

## GameState.java — Gerekli Alanlar

```java
public class GameState {
    // Barlar (0-100)
    public int halk = 50;
    public int ordu = 50;
    public int din  = 50;
    public int para = 50;
    
    // İstatistikler
    public int year = 1999;
    public int decisionCount = 0;
    public int gamePhase = PHASE_OPPOSITION; // 0=opp, 1=ruling
    public int partyChoice = -1; // 0=HSP, 1=MNP, 2=YCH
    
    // Son 10 HALK değeri (seçim hesabı için)
    public List<Integer> recentHalkHistory = new ArrayList<>();
    
    // Tamamlanan kartlar (dallanma için)
    public Set<String> completedCards = new HashSet<>();
    
    // Açık kartlar
    public Set<String> unlockedCards = new HashSet<>();
    
    // Faz sabitleri
    public static final int PHASE_OPPOSITION = 0;
    public static final int PHASE_RULING = 1;
}
```

---

## Alt Bilgi Çubuğu — UI Render

```java
// GameRenderer.java'ya eklenecek
private void renderStatusBar() {
    String yearStr  = "YIL: " + state.year;
    String decStr   = "KARAR: " + state.decisionCount;
    String titleStr = getTitle(state.decisionCount, state.gamePhase);
    String partyStr = getPartyName(state.partyChoice);
    
    // Ekran alt %8'i
    float barY = mH * 0.92f;
    float ps   = gPS * 0.55f;
    
    rect(0, barY - gPS, mW, mH - barY + gPS, C_DARK);
    
    drawString(yearStr,  mPad,           barY, ps, C_GOLD);
    drawString(decStr,   mW * 0.28f,    barY, ps, C_GOLD);
    drawString(titleStr, mW * 0.50f,    barY, ps, C_GDIM);
    drawString(partyStr, mW - mPad - charWidth(partyStr, ps), barY, ps, C_GREY);
}
```

---

## Bar UI — Render

```java
private void renderBars() {
    String[] labels = {"HALK", "ORDU", "DİN", "PARA"};
    int[] values = {state.halk, state.ordu, state.din, state.para};
    
    float barW   = mUsableW / 4f - gPS;
    float barH   = gPS * 2f;
    float barY   = mH * 0.04f;
    float labelY = barY + barH + gPS;
    
    for (int i = 0; i < 4; i++) {
        float bx = mPad + i * (barW + gPS);
        int v = values[i];
        
        // Arka plan
        rect(bx, barY, barW, barH, C_DARK);
        
        // Dolu kısım (renge göre)
        float[] fillColor = getBarColor(v);
        rect(bx, barY, barW * v / 100f, barH, fillColor);
        
        // Etiket
        float ps = fitTextPS(labels[i], barW, gPS * 0.6f);
        drawString(labels[i], bx, labelY, ps, fillColor);
    }
}

private float[] getBarColor(int value) {
    if (value <= 20) return C_DANGER_LOW;   // mavi
    if (value <= 30) return C_WARNING;      // sarı
    if (value >= 80) return C_DANGER_HIGH;  // kırmızı
    if (value >= 70) return C_WARNING;      // sarı
    return C_GOLD;                          // normal altın
}
```

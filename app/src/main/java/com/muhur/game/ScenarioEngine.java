package com.muhur.game;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MÜHÜR — ScenarioEngine (Revizyon 8)
 *
 * Değişiklikler (Rev 8):
 *   1. loadScenario / loadEndings yolları artık dışarıdan tam path alıyor
 *      → GameRenderer.startGameWithParty() "scenario/MNP/cards.json" şeklinde verir.
 *   2. determineBranch çağrısı: sabit "OPP1-015" yerine son aktın son kartı izleniyor.
 *      Kart JSON'ında "branchTrigger": true bayrağı varsa dal belirleme yapılır.
 *   3. advance() artık act sırasını koruyarak sıralıyor (act ASC, sonra orijinal sıra).
 *   4. parseCard() "choiceLeft"/"choiceRight" içindeki eksik "ordu" anahtarını 0 ile doldurur.
 *   5. choose() iktidar evresi çarpanını GameState.applyEffects() üzerinden alır (oraya taşındı).
 *   6. isScenarioComplete() → senaryo tamamen bitti mi (oyun bitmeden seçim ekranına gidilecek)?
 */
public class ScenarioEngine {

    // ══════════════════════════════════════════════════════════════════════
    //  MODEL
    // ══════════════════════════════════════════════════════════════════════

    public static class Choice {
        public String label;
        public int halk, din, para, ordu;
        public int yearOffset;
    }

    public static class Card {
        public String   id;
        public String   character;
        public String   flavor;
        public String   text;
        public Choice   choiceLeft;
        public Choice   choiceRight;
        public String   prerequisite;
        public String[] unlocks;
        public int      act;
        public String   branch;              // "A", "B" veya "" (her dal)
        public boolean  branchTrigger;       // true → bu kart oynandıktan sonra dal belirle
        public String[] requiresRightOn;     // bu kart IDlerinde sağ seçilmişse görünür
        public String   requiresChoiceCardId;
        public String   requiresChoiceValue; // "left" veya "right"
    }

    public static class Ending {
        public String key;
        public String title;
        public String body;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  İÇ DURUM
    // ══════════════════════════════════════════════════════════════════════

    private final AssetManager mAssets;

    /** Tüm kartlar: id → Card (yükleme sırasını korur) */
    private final Map<String, Card>   mCards   = new LinkedHashMap<>();
    /** Tüm felaket sonları: key → Ending */
    private final Map<String, Ending> mEndings = new LinkedHashMap<>();

    /** Şu an oyuncuya gösterilen kart */
    private Card mCurrentCard = null;

    // ══════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════

    public ScenarioEngine(AssetManager assets) {
        mAssets = assets;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YÜKLEME
    // ══════════════════════════════════════════════════════════════════════

    /**
     * cards.json dosyasını yükler.
     * @param assetPath örn. "scenario/MNP/cards.json"
     */
    public boolean loadScenario(String assetPath) {
        try {
            JSONObject root  = new JSONObject(readAsset(assetPath));
            JSONArray  cards = root.getJSONArray("cards");
            mCards.clear();
            for (int i = 0; i < cards.length(); i++) {
                Card c = parseCard(cards.getJSONObject(i));
                mCards.put(c.id, c);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * endings.json dosyasını yükler.
     * @param assetPath örn. "scenario/MNP/endings.json"
     */
    public boolean loadEndings(String assetPath) {
        try {
            JSONArray arr = new JSONObject(readAsset(assetPath)).getJSONArray("endings");
            mEndings.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Ending e = new Ending();
                e.key   = obj.optString("key",   "");
                e.title = obj.optString("title", "");
                e.body  = obj.optString("body",  "");
                mEndings.put(e.key, e);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BAŞLATMA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Oyunu sıfırlar ve ilk uygun kartı yükler.
     * GameState.sifirla() çağrısından SONRA çağrılmalı.
     */
    public void startScenario(GameState state) {
        mCurrentCard = null;
        advance(state);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ERİŞİLEBİLİRLİK
    // ══════════════════════════════════════════════════════════════════════

    private boolean isCardAvailable(Card card, GameState state) {
        // 1. Zaten oynanmış
        if (state.completedCards.contains(card.id)) return false;

        // 2. Önkoşul tamamlanmamış
        if (card.prerequisite != null && !card.prerequisite.isEmpty()
                && !card.prerequisite.equals("null")) {
            if (!state.completedCards.contains(card.prerequisite)) return false;
        }

        // 3. Dal kısıtı (boş → her dalda göster)
        if (card.branch != null && !card.branch.isEmpty()) {
            if (!card.branch.equals(state.activeBranch)) return false;
        }

        // 4. requiresRightOn — belirli kartlarda sağ seçim yapılmış mı?
        if (card.requiresRightOn != null) {
            for (String reqId : card.requiresRightOn) {
                if (!"right".equals(state.cardChoices.get(reqId))) return false;
            }
        }

        // 5. requiresChoiceOnCard — belirli kartta belirli yön seçilmiş mi?
        if (card.requiresChoiceCardId != null && !card.requiresChoiceCardId.isEmpty()) {
            String actual = state.cardChoices.get(card.requiresChoiceCardId);
            if (!card.requiresChoiceValue.equals(actual)) return false;
        }

        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  KART GEÇİŞİ
    // ══════════════════════════════════════════════════════════════════════

    /** Oyuncuya gösterilen aktif kart. null → senaryo bitti veya felaket. */
    public Card getCurrentCard() {
        return mCurrentCard;
    }

    /**
     * Oyuncu seçim yaptığında çağrılır.
     * @param right true = sağa kaydır (EVET), false = sola (HAYIR)
     */
    public void choose(GameState state, boolean right) {
        if (mCurrentCard == null) return;
        Card card = mCurrentCard;

        Choice choice    = right ? card.choiceRight : card.choiceLeft;
        String choiceKey = right ? "right" : "left";

        // Efektleri uygula — GameState çarpanı ve yıl yönetimini halleder
        state.applyEffects(choice.halk, choice.din, choice.para, choice.ordu, choice.yearOffset);

        // Kartı tamamlandı olarak işaretle
        state.markCardDone(card.id, choiceKey);

        // Perde aktını güncelle
        if (card.act >= state.currentAct) state.currentAct = card.act;

        // Dal belirleyici kart mı?
        if (card.branchTrigger) {
            state.determineBranch();
        }

        // Felaket tetiklendiyse dur
        if (state.oyunBitti) {
            mCurrentCard = null;
            return;
        }

        advance(state);
    }

    /**
     * Mevcut duruma göre bir sonraki uygun kartı bulur.
     * Sıralama: act ASC → LinkedHashMap orijinal sıra.
     */
    private void advance(GameState state) {
        // Tüm kartları filtrele: uygun + oynanmamış
        List<Card> available = new ArrayList<>();
        for (Card card : mCards.values()) {
            if (isCardAvailable(card, state)) available.add(card);
        }

        if (available.isEmpty()) {
            mCurrentCard = null;
            return;
        }

        // Act'e göre sırala, sonra orijinal eklenme sırasını koru (stable sort)
        // Java'nın sort'u stable olduğu için act karşılaştırması yeterli
        Card next = available.get(0);
        for (Card c : available) {
            if (c.act < next.act) next = c;
        }
        mCurrentCard = next;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BİTİŞ
    // ══════════════════════════════════════════════════════════════════════

    /** Felaket sonunu döndürür. null → felaket yok veya anahtar eşleşmiyor. */
    public Ending getEnding(GameState state) {
        if (!state.oyunBitti || state.endingKey.isEmpty()) return null;
        return mEndings.get(state.endingKey);
    }

    /** Tüm kartlar bitti mi (felaket olmadan)? → Seçim ekranına geç. */
    public boolean isScenarioComplete() {
        return mCurrentCard == null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARSE
    // ══════════════════════════════════════════════════════════════════════

    private Card parseCard(JSONObject obj) throws Exception {
        Card c = new Card();
        c.id           = obj.optString("id",        "");
        c.character    = obj.optString("character", "");
        c.flavor       = obj.optString("flavor",    "");
        c.text         = obj.optString("text",      "");
        c.act          = obj.optInt("act", 1);
        c.branch       = obj.optString("branch", "");
        c.branchTrigger = obj.optBoolean("branchTrigger", false);
        c.prerequisite = obj.optString("prerequisite", "");
        if (c.prerequisite.equals("null")) c.prerequisite = "";

        // unlocks
        if (obj.has("unlocks")) {
            JSONArray ua = obj.getJSONArray("unlocks");
            c.unlocks = new String[ua.length()];
            for (int i = 0; i < ua.length(); i++) c.unlocks[i] = ua.getString(i);
        } else {
            c.unlocks = new String[0];
        }

        // requiresRightOn (eski JSON'da "requiresCompletedCards" adıyla da gelebilir)
        String reqKey = obj.has("requiresRightOn") ? "requiresRightOn"
                      : obj.has("requiresCompletedCards") ? "requiresCompletedCards" : null;
        if (reqKey != null) {
            JSONArray ra = obj.getJSONArray(reqKey);
            c.requiresRightOn = new String[ra.length()];
            for (int i = 0; i < ra.length(); i++) c.requiresRightOn[i] = ra.getString(i);
        }

        // requiresChoiceOnCard
        if (obj.has("requiresChoiceOnCard")) {
            JSONObject rco = obj.getJSONObject("requiresChoiceOnCard");
            c.requiresChoiceCardId = rco.optString("cardId", "");
            c.requiresChoiceValue  = rco.optString("choice", "");
        }

        c.choiceLeft  = parseChoice(obj.getJSONObject("choiceLeft"));
        c.choiceRight = parseChoice(obj.getJSONObject("choiceRight"));

        // yearOffset her iki seçeneğe de uygulanır
        int yearOffset = obj.optInt("yearOffset", 0);
        c.choiceLeft.yearOffset  = yearOffset;
        c.choiceRight.yearOffset = yearOffset;

        return c;
    }

    private Choice parseChoice(JSONObject obj) throws Exception {
        Choice ch = new Choice();
        ch.label = obj.optString("label", "");
        ch.halk  = obj.optInt("halk",  0);
        ch.din   = obj.optInt("din",   0);
        ch.para  = obj.optInt("para",  0);
        ch.ordu  = obj.optInt("ordu",  0);
        return ch;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ASSET OKUMA
    // ══════════════════════════════════════════════════════════════════════

    private String readAsset(String path) throws Exception {
        InputStream is  = mAssets.open(path);
        byte[]      buf = new byte[is.available()];
        //noinspection ResultOfMethodCallIgnored
        is.read(buf);
        is.close();
        return new String(buf, "UTF-8");
    }
}

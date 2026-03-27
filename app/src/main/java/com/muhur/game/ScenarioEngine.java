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
 * MÜHÜR — ScenarioEngine
 *
 * Kart motoru. Tek sorumluluk: doğru kartı doğru anda göstermek.
 *
 * Bağımlılıklar:
 *   - assets/scenario/opposition_1/cards.json   (kart verisi)
 *   - assets/scenario/opposition_1/endings.json (felaket sonları)
 *   - GameState                                 (bar/karar durumu)
 *
 * Kullanım (GameRenderer içinden):
 *   engine = new ScenarioEngine(assetManager);
 *   engine.loadScenario("scenario/opposition_1/cards.json");
 *   engine.loadEndings("scenario/opposition_1/endings.json");
 *
 *   Card card = engine.getCurrentCard();
 *   engine.choose(card, true, state);   // true=sağ, false=sol
 *   Card next = engine.getCurrentCard();
 */
public class ScenarioEngine {

    // ── KART MODELİ ───────────────────────────────────────────────────────

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
        public String   prerequisite;       // tek önkoşul kart ID
        public String[] unlocks;            // bu kart bittikten sonra açılanlar
        public int      act;
        public String   branch;             // "A", "B" veya "" (her dalda)
        // Dal A için özel: hangi kartların SAĞ seçilmiş olması gerekir
        public String[] requiresRightOn;
        // Özel koşul: belirli bir kartta belirli seçim
        public String   requiresChoiceCardId;
        public String   requiresChoiceValue; // "left" veya "right"
    }

    // ── FELAkat SON MODELİ ───────────────────────────────────────────────

    public static class Ending {
        public String key;    // "halk_0", "ordu_100" vb.
        public String title;
        public String body;
    }

    // ── İÇ DURUM ─────────────────────────────────────────────────────────

    private final AssetManager mAssets;

    /** Tüm kartlar: id → Card */
    private final Map<String, Card> mCards = new LinkedHashMap<>();

    /** Tüm sonlar: key → Ending */
    private final Map<String, Ending> mEndings = new LinkedHashMap<>();

    /** Sıradaki kart kuyruğu (act sırası) */
    private final List<String> mQueue = new ArrayList<>();

    /** Şu an oyuncuya gösterilen kart */
    private Card mCurrentCard = null;

    /** Kuyruktaki indeks */
    private int mQueueIdx = 0;

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────

    public ScenarioEngine(AssetManager assets) {
        mAssets = assets;
    }

    // ── YÜKLEME ───────────────────────────────────────────────────────────

    /**
     * cards.json dosyasını yükler ve dahili kart haritasını doldurur.
     * @param assetPath örn. "scenario/opposition_1/cards.json"
     * @return Başarılı mı?
     */
    public boolean loadScenario(String assetPath) {
        try {
            String raw = readAsset(assetPath);
            JSONObject root  = new JSONObject(raw);
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
     */
    public boolean loadEndings(String assetPath) {
        try {
            String raw    = readAsset(assetPath);
            JSONObject root = new JSONObject(raw);
            JSONArray  arr  = root.getJSONArray("endings");

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

    // ── BAŞLATMA ──────────────────────────────────────────────────────────

    /**
     * Oyunu sıfırlar ve ilk kartı yükler.
     * GameState.sifirla() çağrısından SONRA çağrılmalı.
     */
    public void startScenario(GameState state) {
        mQueue.clear();
        mQueueIdx = 0;
        mCurrentCard = null;
        buildQueue(state);
        advance(state);
    }

    /**
     * Mevcut duruma göre kuyruğu (re)inşa eder.
     * Perde, dal ve tamamlanmış kartlara göre sıralama yapar.
     */
    private void buildQueue(GameState state) {
        mQueue.clear();
        for (Card card : mCards.values()) {
            if (isCardAvailable(card, state)) {
                mQueue.add(card.id);
            }
        }
    }

    // ── ERİŞİLEBİLİRLİK KONTROLÜ ─────────────────────────────────────────

    /**
     * Kartın şu an oynanabilir olup olmadığını kontrol eder.
     * Kurallar:
     *   1. Zaten tamamlanmış → hayır
     *   2. Prerequisite tamamlanmamış → hayır
     *   3. Dal kısıtı uyuşmuyorsa → hayır
     *   4. requiresRightOn → ilgili kartlarda sağ seçim yapılmış mı?
     *   5. requiresChoiceCardId → belirli kartta belirli seçim yapılmış mı?
     */
    private boolean isCardAvailable(Card card, GameState state) {
        // 1. Zaten oynandıysa geç
        if (state.completedCards.contains(card.id)) return false;

        // 2. Önkoşul
        if (card.prerequisite != null && !card.prerequisite.isEmpty()) {
            if (!state.completedCards.contains(card.prerequisite)) return false;
        }

        // 3. Dal kısıtı
        if (card.branch != null && !card.branch.isEmpty()) {
            if (!card.branch.equals(state.activeBranch)) return false;
        }

        // 4. requiresRightOn
        if (card.requiresRightOn != null) {
            for (String reqId : card.requiresRightOn) {
                if (!"right".equals(state.cardChoices.get(reqId))) return false;
            }
        }

        // 5. requiresChoiceCardId
        if (card.requiresChoiceCardId != null && !card.requiresChoiceCardId.isEmpty()) {
            String actual = state.cardChoices.get(card.requiresChoiceCardId);
            if (!card.requiresChoiceValue.equals(actual)) return false;
        }

        return true;
    }

    // ── KART GEÇİŞİ ──────────────────────────────────────────────────────

    /**
     * Sıradaki uygun kartı döndürür. Null → senaryo bitti.
     */
    public Card getCurrentCard() {
        return mCurrentCard;
    }

    /**
     * Oyuncu seçim yaptığında çağrılır.
     * @param right true = sağ (EVET), false = sol (HAYIR)
     */
    public void choose(GameState state, boolean right) {
        if (mCurrentCard == null) return;
        Card card = mCurrentCard;

        Choice choice = right ? card.choiceRight : card.choiceLeft;
        String choiceKey = right ? "right" : "left";

        // Barları güncelle
        state.applyEffects(choice.halk, choice.din, choice.para, choice.ordu, choice.yearOffset);

        // Kartı tamamlandı olarak işaretle
        state.markCardDone(card.id, choiceKey);

        // Perde 1 bittikten sonra dal belirle
        if ("OPP1-015".equals(card.id)) {
            state.determineBranch();
        }

        // Perde takibi
        updateAct(state, card.act);

        // Oyun bittiyse dur
        if (state.oyunBitti) {
            mCurrentCard = null;
            return;
        }

        // Bir sonraki karta geç
        advance(state);
    }

    /**
     * Mevcut kuyruğu günceller ve sonraki uygun kartı yükler.
     */
    private void advance(GameState state) {
        // Kuyruğu mevcut duruma göre yeniden filtrele
        buildQueue(state);

        if (mQueue.isEmpty()) {
            mCurrentCard = null;
            return;
        }

        // Kuyruktaki ilk uygun kartı al
        mCurrentCard = mCards.get(mQueue.get(0));
    }

    private void updateAct(GameState state, int completedAct) {
        // Perdeye göre currentAct güncelle
        if (completedAct >= state.currentAct) {
            state.currentAct = completedAct;
        }
    }

    // ── BİTİŞ YÖNETİMİ ───────────────────────────────────────────────────

    /**
     * Felaket sonunu döndürür. null → felaket yok.
     */
    public Ending getEnding(GameState state) {
        if (!state.oyunBitti || state.endingKey.isEmpty()) return null;
        return mEndings.get(state.endingKey);
    }

    /**
     * Senaryo tüm kartları bitirdi mi?
     */
    public boolean isScenarioDone() {
        return mCurrentCard == null;
    }

    // ── PARSE ────────────────────────────────────────────────────────────

    private Card parseCard(JSONObject obj) throws Exception {
        Card c = new Card();
        c.id        = obj.optString("id",        "");
        c.character = obj.optString("character", "");
        c.flavor    = obj.optString("flavor",    "");
        c.text      = obj.optString("text",      "");
        c.act       = obj.optInt("act", 1);
        c.branch    = obj.optString("branch",    "");
        c.prerequisite = obj.optString("prerequisite", "");
        if (c.prerequisite.equals("null")) c.prerequisite = "";

        // unlocks dizisi
        if (obj.has("unlocks")) {
            JSONArray ua = obj.getJSONArray("unlocks");
            c.unlocks = new String[ua.length()];
            for (int i = 0; i < ua.length(); i++) c.unlocks[i] = ua.getString(i);
        } else {
            c.unlocks = new String[0];
        }

        // requiresRightOn dizisi
        if (obj.has("requiresCompletedCards")) {
            JSONArray ra = obj.getJSONArray("requiresCompletedCards");
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

        c.choiceLeft.yearOffset  = obj.optInt("yearOffset", 0);
        c.choiceRight.yearOffset = obj.optInt("yearOffset", 0);

        return c;
    }

    private Choice parseChoice(JSONObject obj) throws Exception {
        Choice ch = new Choice();
        ch.label = obj.optString("label", "");
        ch.halk  = obj.optInt("halk", 0);
        ch.din   = obj.optInt("din",  0);
        ch.para  = obj.optInt("para", 0);
        ch.ordu  = obj.optInt("ordu", 0);
        return ch;
    }

    // ── ASSET OKUMA ──────────────────────────────────────────────────────

    private String readAsset(String path) throws Exception {
        InputStream is  = mAssets.open(path);
        byte[]      buf = new byte[is.available()];
        //noinspection ResultOfMethodCallIgnored
        is.read(buf);
        is.close();
        return new String(buf, "UTF-8");
    }
}

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
 * MÜHÜR — ScenarioEngine (Revizyon 9)
 */
public class ScenarioEngine {

    // ── Model ─────────────────────────────────────────────────────────────

    public static class Choice {
        public String label;
        public int halk, din, para, ordu, yearOffset;
    }

    public static class Card {
        public String   id, character, flavor, text;
        public Choice   choiceLeft, choiceRight;
        public String   prerequisite;
        public String[] unlocks;
        public int      act;
        public String   branch;
        public boolean  branchTrigger;
        public String[] requiresRightOn;
        public String   requiresChoiceCardId, requiresChoiceValue;
    }

    public static class Ending {
        public String key, title, body;
    }

    // ── İç durum ─────────────────────────────────────────────────────────

    private final AssetManager mAssets;
    private final Map<String, Card>   mCards   = new LinkedHashMap<>();
    private final Map<String, Ending> mEndings = new LinkedHashMap<>();
    private Card mCurrentCard = null;

    public ScenarioEngine(AssetManager assets) {
        mAssets = assets;
    }

    // ── Yükleme ───────────────────────────────────────────────────────────

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
        } catch (Exception e) { return false; }
    }

    public boolean loadEndings(String assetPath) {
        try {
            JSONArray arr = new JSONObject(readAsset(assetPath)).getJSONArray("endings");
            mEndings.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Ending e = new Ending();
                e.key = obj.optString("key",""); e.title = obj.optString("title","");
                e.body = obj.optString("body","");
                mEndings.put(e.key, e);
            }
            return true;
        } catch (Exception e) { return false; }
    }

    // ── Başlatma ──────────────────────────────────────────────────────────

    public void startScenario(GameState state) {
        mCurrentCard = null;
        advance(state);
    }

    // ── Erişilebilirlik ───────────────────────────────────────────────────

    private boolean isCardAvailable(Card card, GameState state) {
        if (state.completedCards.contains(card.id)) return false;
        if (card.prerequisite != null && !card.prerequisite.isEmpty()
                && !card.prerequisite.equals("null")) {
            if (!state.completedCards.contains(card.prerequisite)) return false;
        }
        if (card.branch != null && !card.branch.isEmpty()) {
            if (!card.branch.equals(state.activeBranch)) return false;
        }
        if (card.requiresRightOn != null) {
            for (String reqId : card.requiresRightOn)
                if (!"right".equals(state.cardChoices.get(reqId))) return false;
        }
        if (card.requiresChoiceCardId != null && !card.requiresChoiceCardId.isEmpty()) {
            String actual = state.cardChoices.get(card.requiresChoiceCardId);
            if (!card.requiresChoiceValue.equals(actual)) return false;
        }
        return true;
    }

    // ── Kart geçişi ───────────────────────────────────────────────────────

    public Card getCurrentCard() { return mCurrentCard; }

    public void choose(GameState state, boolean right) {
        if (mCurrentCard == null) return;
        Card card = mCurrentCard;
        Choice choice = right ? card.choiceRight : card.choiceLeft;
        String choiceKey = right ? "right" : "left";
        state.applyEffects(choice.halk, choice.din, choice.para, choice.ordu, choice.yearOffset);
        state.markCardDone(card.id, choiceKey);
        if (card.act >= state.currentAct) state.currentAct = card.act;
        if (card.branchTrigger) state.determineBranch();
        if (state.oyunBitti) { mCurrentCard = null; return; }
        advance(state);
    }

    private void advance(GameState state) {
        Card next = null;
        for (Card card : mCards.values()) {
            if (!isCardAvailable(card, state)) continue;
            if (next == null || card.act < next.act) next = card;
        }
        mCurrentCard = next;
    }

    // ── Bitiş ────────────────────────────────────────────────────────────

    public Ending getEnding(GameState state) {
        if (!state.oyunBitti || state.endingKey.isEmpty()) return null;
        return mEndings.get(state.endingKey);
    }

    public boolean isScenarioComplete() { return mCurrentCard == null; }

    // ── Parse ────────────────────────────────────────────────────────────

    private Card parseCard(JSONObject obj) throws Exception {
        Card c = new Card();
        c.id = obj.optString("id",""); c.character = obj.optString("character","");
        c.flavor = obj.optString("flavor",""); c.text = obj.optString("text","");
        c.act = obj.optInt("act",1); c.branch = obj.optString("branch","");
        c.branchTrigger = obj.optBoolean("branchTrigger",false);
        c.prerequisite = obj.optString("prerequisite","");
        if ("null".equals(c.prerequisite)) c.prerequisite = "";
        if (obj.has("unlocks")) {
            JSONArray ua = obj.getJSONArray("unlocks");
            c.unlocks = new String[ua.length()];
            for (int i = 0; i < ua.length(); i++) c.unlocks[i] = ua.getString(i);
        } else c.unlocks = new String[0];
        String reqKey = obj.has("requiresRightOn") ? "requiresRightOn"
                      : obj.has("requiresCompletedCards") ? "requiresCompletedCards" : null;
        if (reqKey != null) {
            JSONArray ra = obj.getJSONArray(reqKey);
            c.requiresRightOn = new String[ra.length()];
            for (int i = 0; i < ra.length(); i++) c.requiresRightOn[i] = ra.getString(i);
        }
        if (obj.has("requiresChoiceOnCard")) {
            JSONObject rco = obj.getJSONObject("requiresChoiceOnCard");
            c.requiresChoiceCardId = rco.optString("cardId","");
            c.requiresChoiceValue  = rco.optString("choice","");
        }
        c.choiceLeft  = parseChoice(obj.optJSONObject("choiceLeft"));
        c.choiceRight = parseChoice(obj.optJSONObject("choiceRight"));
        int yo = obj.optInt("yearOffset",0);
        c.choiceLeft.yearOffset = yo; c.choiceRight.yearOffset = yo;
        return c;
    }

    private Choice parseChoice(JSONObject obj) {
        Choice ch = new Choice();
        if (obj == null) return ch;
        ch.label = obj.optString("label","");
        ch.halk = obj.optInt("halk",0); ch.din = obj.optInt("din",0);
        ch.para = obj.optInt("para",0); ch.ordu = obj.optInt("ordu",0);
        return ch;
    }

    private String readAsset(String path) throws Exception {
        InputStream is = mAssets.open(path);
        byte[] buf = new byte[is.available()];
        is.read(buf); is.close();
        return new String(buf, "UTF-8");
    }
}

package com.muhur.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MÜHÜR — GameState (Revizyon 9)
 */
public class GameState {

    public static final int PHASE_OPPOSITION = 0;
    public static final int PHASE_RULING     = 1;

    public static final int PARTY_HSP = 0;
    public static final int PARTY_MNP = 1;
    public static final int PARTY_LDP = 2;

    private static final String[] PARTY_FOLDERS = { "HSP", "MNP", "LDP" };
    private static final String[] PARTY_DISPLAY = {
        "HALKCI SECIM PARTISI",
        "MILLI NIZAM PARTISI",
        "LIBERAL DEMOKRAT PARTI"
    };

    // Barlar
    public int halk = 50;
    public int din  = 50;
    public int para = 50;
    public int ordu = 50;

    // İlerleme
    public int     year          = 1999;
    public int     decisionCount = 0;
    public int     gamePhase     = PHASE_OPPOSITION;
    public int     partyChoice   = PARTY_HSP;
    public int     currentAct    = 1;
    public boolean oyunBitti     = false;
    public String  endingKey     = "";

    // Seçim geçmişi
    public final List<Integer> recentHalkHistory = new ArrayList<>();

    // Dal yönetimi
    public final Set<String>         completedCards = new HashSet<>();
    public final Map<String, String> cardChoices    = new HashMap<>();
    public String activeBranch = "";

    // ── Unvan ────────────────────────────────────────────────────────────

    public String getTitle() {
        if (gamePhase == PHASE_RULING) return getRulingTitle();
        if (decisionCount <= 5)  return "YENI UYE";
        if (decisionCount <= 15) return "MUFETTIS YARD.";
        if (decisionCount <= 30) return "BOLGE TEMSILCISI";
        if (decisionCount <= 50) return "PARTI SOZCUSU";
        if (decisionCount <= 70) return "GENEL SEKRETER";
        return "LIDER ADAYI";
    }

    private String getRulingTitle() {
        if (decisionCount <= 10) return "KOALISYON ORTAGI";
        if (decisionCount <= 25) return "BAS. YARDIMCISI";
        if (decisionCount <= 45) return "BASBAKAN";
        if (decisionCount <= 60) return "KALICI BASBAKAN";
        return "TEK ADAM";
    }

    // ── Parti yardımcıları ────────────────────────────────────────────────

    public String getScenarioFolder() {
        if (partyChoice >= 0 && partyChoice < PARTY_FOLDERS.length)
            return PARTY_FOLDERS[partyChoice];
        return "HSP";
    }

    public String getPartyDisplayName() {
        if (partyChoice >= 0 && partyChoice < PARTY_DISPLAY.length)
            return PARTY_DISPLAY[partyChoice];
        return "BILINMIYOR";
    }

    // ── Bar güncelleme ────────────────────────────────────────────────────

    public void applyEffects(int dHalk, int dDin, int dPara, int dOrdu, int yearOffset) {
        float mult = (gamePhase == PHASE_RULING) ? 1.5f : 1.0f;
        halk = clamp(halk + Math.round(dHalk * mult));
        din  = clamp(din  + Math.round(dDin  * mult));
        para = clamp(para + Math.round(dPara * mult));
        ordu = clamp(ordu + Math.round(dOrdu * mult));
        decisionCount++;
        year += yearOffset;
        recentHalkHistory.add(halk);
        if (recentHalkHistory.size() > 15) recentHalkHistory.remove(0);
        checkGameOver();
    }

    public void markCardDone(String cardId, String choice) {
        completedCards.add(cardId);
        cardChoices.put(cardId, choice);
    }

    public void determineBranch() {
        String folder = getScenarioFolder();
        boolean right3  = "right".equals(cardChoices.get(folder + "-003"))
                       || "right".equals(cardChoices.get("OPP1-003"));
        boolean right11 = "right".equals(cardChoices.get(folder + "-011"))
                       || "right".equals(cardChoices.get("OPP1-011"));
        activeBranch = (right3 && right11) ? "A" : "B";
    }

    // ── Seçim ────────────────────────────────────────────────────────────

    public static final int ELECTION_WIN  = 2;
    public static final int ELECTION_TIE  = 1;
    public static final int ELECTION_LOSS = 0;

    public int getElectionScore() {
        if (recentHalkHistory.isEmpty()) return halk;
        int start = Math.max(0, recentHalkHistory.size() - 10);
        List<Integer> last10 = recentHalkHistory.subList(start, recentHalkHistory.size());
        int sum = 0;
        for (int v : last10) sum += v;
        return sum / last10.size();
    }

    public int getElectionResult() {
        int score = getElectionScore();
        if (score >= 55) return ELECTION_WIN;
        if (score >= 45) return ELECTION_TIE;
        return ELECTION_LOSS;
    }

    // ── Felaket ──────────────────────────────────────────────────────────

    private void checkGameOver() {
        if (oyunBitti) return;
        if (halk <= 0)   { triggerEnding("halk_0");   return; }
        if (halk >= 100) { triggerEnding("halk_100"); return; }
        if (ordu <= 0)   { triggerEnding("ordu_0");   return; }
        if (ordu >= 100) { triggerEnding("ordu_100"); return; }
        if (din  <= 0)   { triggerEnding("din_0");    return; }
        if (din  >= 100) { triggerEnding("din_100");  return; }
        if (para <= 0)   { triggerEnding("para_0");   return; }
        if (para >= 100) { triggerEnding("para_100"); return; }
    }

    private void triggerEnding(String key) {
        oyunBitti = true;
        endingKey  = key;
    }

    // ── Sıfırlama ────────────────────────────────────────────────────────

    public void sifirla() {
        halk = din = para = ordu = 50;
        year = 1999; decisionCount = 0;
        gamePhase = PHASE_OPPOSITION;
        currentAct = 1; oyunBitti = false;
        endingKey = ""; activeBranch = "";
        recentHalkHistory.clear();
        completedCards.clear();
        cardChoices.clear();
    }

    // ── Yardımcı ────────────────────────────────────────────────────────

    public static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    public int getDangerLevel(int barValue) {
        if (barValue <= 15 || barValue >= 85) return 2;
        if (barValue <= 25 || barValue >= 75) return 1;
        return 0;
    }
}

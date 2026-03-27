package com.muhur.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MÜHÜR — GameState  (Revizyon 2)
 *
 * CardEngine.md spesifikasyonuna tam uyum.
 * Tüm oyun durumunu tutar: barlar, yıl, karar sayısı, dal geçmişi,
 * seçim hesabı için son 10 HALK değeri, felaket kontrolü.
 */
public class GameState {

    // ── FAZ SABİTLERİ ─────────────────────────────────────────────────────
    public static final int PHASE_OPPOSITION = 0;
    public static final int PHASE_RULING     = 1;

    // ── PARTI SABİTLERİ ───────────────────────────────────────────────────
    public static final int PARTY_HSP = 0;
    public static final int PARTY_MNP = 1;
    public static final int PARTY_YCH = 2;

    // ── BARLAR (0–100) ────────────────────────────────────────────────────
    public int halk    = 50;
    public int din     = 50;
    public int para    = 50;
    public int ordu    = 50;

    // ── OYUN DURUM DEĞİŞKENLERİ ──────────────────────────────────────────
    public int  year          = 1999;
    public int  decisionCount = 0;
    public int  gamePhase     = PHASE_OPPOSITION;
    public int  partyChoice   = PARTY_HSP;
    public int  currentAct    = 1;        // 1-5 perde
    public boolean oyunBitti  = false;
    public String  endingKey  = "";       // tetiklenen felaket son kodu

    // ── SEÇİM MEKANİZMASI ────────────────────────────────────────────────
    // Son 10 HALK değeri — Perde 4 sonu hesabı için
    public final List<Integer> recentHalkHistory = new ArrayList<>();

    // ── DAL YÖNETİMİ ─────────────────────────────────────────────────────
    // Tamamlanan kart ID'leri (prerequisite kontrolü için)
    public final Set<String> completedCards = new HashSet<>();
    // Yapılan seçimler: cardId → "left" veya "right"
    public final java.util.Map<String, String> cardChoices = new java.util.HashMap<>();
    // Dal durumu (Perde 2 dallanması için)
    public String activeBranch = "";  // "A", "B" veya ""

    // ── HSP UNVAN BASAMAKLARI ─────────────────────────────────────────────
    // README.md'den: 0-5 / 6-15 / 16-30 / 31-50 / 51-70 / 71+
    public String getTitle() {
        if (gamePhase == PHASE_RULING) return getRulingTitle();
        // Muhalefet unvanları
        if (decisionCount <= 5)  return "YENİ ÜYE";
        if (decisionCount <= 15) return "MÜFETTİŞ YARDIMCISI";
        if (decisionCount <= 30) return "BÖLGE TEMSİLCİSİ";
        if (decisionCount <= 50) return "PARTİ SÖZCÜSÜ";
        if (decisionCount <= 70) return "GENEL SEKRETER";
        return "LİDER ADAYI";
    }

    private String getRulingTitle() {
        if (decisionCount <= 10) return "KOALİSYON ORTAĞI";
        if (decisionCount <= 25) return "BAŞBAKAN YARDIMCISI";
        if (decisionCount <= 45) return "BAŞBAKAN";
        if (decisionCount <= 60) return "KALICI BAŞBAKAN";
        return "TEK ADAM";  // tehlike — UI kırmızı sinyali
    }

    // ── BAR GÜNCELLEME ────────────────────────────────────────────────────

    /**
     * Seçim efektlerini barlar üzerine uygular.
     * @param dHalk  HALK değişimi
     * @param dDin   DİN değişimi
     * @param dPara  PARA değişimi
     * @param dOrdu  ORDU değişimi
     * @param yearOffset Bu kart yıl ilerletiyor mu?
     */
    public void applyEffects(int dHalk, int dDin, int dPara, int dOrdu, int yearOffset) {
        halk = clamp(halk + dHalk, 0, 100);
        din  = clamp(din  + dDin,  0, 100);
        para = clamp(para + dPara, 0, 100);
        ordu = clamp(ordu + dOrdu, 0, 100);

        decisionCount++;
        year += yearOffset;

        // Seçim hesabı için HALK geçmişi — max 15 tutar
        recentHalkHistory.add(halk);
        if (recentHalkHistory.size() > 15) {
            recentHalkHistory.remove(0);
        }

        checkGameOver();
    }

    /**
     * Kart tamamlandığında çağrılır.
     * @param cardId  Tamamlanan kartın ID'si
     * @param choice  "left" veya "right"
     */
    public void markCardDone(String cardId, String choice) {
        completedCards.add(cardId);
        cardChoices.put(cardId, choice);
    }

    /**
     * Perde 2 dal belirleme.
     * OPP1-003 sağ + OPP1-011 sağ → Dal A, yoksa Dal B.
     */
    public void determineBranch() {
        boolean zeynep3  = "right".equals(cardChoices.get("OPP1-003"));
        boolean zeynep11 = "right".equals(cardChoices.get("OPP1-011"));
        activeBranch = (zeynep3 && zeynep11) ? "A" : "B";
    }

    // ── SEÇİM HESABI ──────────────────────────────────────────────────────

    /**
     * Son 10 HALK değerinin ortalamasını döndürür.
     * CardEngine.md: avg >= 55 → kazanıldı, 45-54 → 2. tur, <45 → kaybedildi
     */
    public int getElectionScore() {
        if (recentHalkHistory.isEmpty()) return halk;
        int start = Math.max(0, recentHalkHistory.size() - 10);
        List<Integer> last10 = recentHalkHistory.subList(start, recentHalkHistory.size());
        int sum = 0;
        for (int v : last10) sum += v;
        return sum / last10.size();
    }

    public static final int ELECTION_WIN  = 2;
    public static final int ELECTION_TIE  = 1;  // 2. tur
    public static final int ELECTION_LOSS = 0;

    public int getElectionResult() {
        int score = getElectionScore();
        if (score >= 55) return ELECTION_WIN;
        if (score >= 45) return ELECTION_TIE;
        return ELECTION_LOSS;
    }

    // ── FELAKET KONTROLÜ ─────────────────────────────────────────────────

    /**
     * Herhangi bir bar sınıra ulaştığında oyun biter.
     * endingKey → GameRenderer felaket sonunu bu anahtarla yükler.
     */
    private void checkGameOver() {
        if (oyunBitti) return;
        if (halk == 0)   { triggerEnding("halk_0");   return; }
        if (halk == 100) { triggerEnding("halk_100");  return; }
        if (ordu == 0)   { triggerEnding("ordu_0");    return; }
        if (ordu == 100) { triggerEnding("ordu_100");  return; }
        if (din == 0)    { triggerEnding("din_0");     return; }
        if (din == 100)  { triggerEnding("din_100");   return; }
        if (para == 0)   { triggerEnding("para_0");    return; }
        if (para == 100) { triggerEnding("para_100");  return; }
    }

    private void triggerEnding(String key) {
        oyunBitti = true;
        endingKey  = key;
    }

    // ── SIFIRLAMA ────────────────────────────────────────────────────────

    public void sifirla() {
        halk = din = para = ordu = 50;
        year = 1999;
        decisionCount = 0;
        gamePhase     = PHASE_OPPOSITION;
        partyChoice   = PARTY_HSP;
        currentAct    = 1;
        oyunBitti     = false;
        endingKey     = "";
        activeBranch  = "";
        recentHalkHistory.clear();
        completedCards.clear();
        cardChoices.clear();
    }

    // ── YARDIMCI ─────────────────────────────────────────────────────────

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Bar tehlike seviyesi: 0=normal, 1=uyarı, 2=tehlike */
    public int getDangerLevel(int barValue) {
        if (barValue <= 15 || barValue >= 85) return 2;
        if (barValue <= 25 || barValue >= 75) return 1;
        return 0;
    }
}

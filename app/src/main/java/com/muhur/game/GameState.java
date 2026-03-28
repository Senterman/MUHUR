package com.muhur.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MÜHÜR — GameState (Revizyon 8)
 *
 * Değişiklikler (Rev 8):
 *   1. PARTY sabitleri int → String ("HSP","MNP","LDP") olarak güncellendi.
 *      GameRenderer.PARTY_IDS[] ve startGameWithParty() ile tam uyumlu.
 *   2. sifirla() metodu partyChoice'u korumaya aldı (yeniden başlatmada parti kaybolmuyordu).
 *   3. determineBranch() MNP ve LDP dal kartlarını destekleyecek şekilde genelleştirildi.
 *   4. getPartyDisplayName() eklendi — seçim ekranında doğru isim gösterimi.
 *   5. getScenarioFolder() eklendi — GameRenderer'ın sabit string yerine buradan okuması sağlandı.
 *   6. Unvan sistemi türkçe karakter sorunu giderildi (trUpper gerektirmeden ASCII safe).
 */
public class GameState {

    // ── FAZ SABİTLERİ ─────────────────────────────────────────────────────
    public static final int PHASE_OPPOSITION = 0;
    public static final int PHASE_RULING     = 1;

    // ── PARTİ SABİTLERİ (int index — PARTY_IDS[] ile aynı sıra) ──────────
    public static final int PARTY_HSP = 0;   // Halkçı Seçim Partisi
    public static final int PARTY_MNP = 1;   // Milli Nizam Partisi
    public static final int PARTY_LDP = 2;   // Liberal Demokrat Parti

    /** Asset klasörü isimleri — GameRenderer.PARTY_IDS[] ile birebir aynı */
    private static final String[] PARTY_FOLDERS = { "HSP", "MNP", "LDP" };

    /** Ekranda gösterilecek tam parti adları */
    private static final String[] PARTY_DISPLAY = {
        "HALKCI SECIM PARTISI",
        "MILLI NIZAM PARTISI",
        "LIBERAL DEMOKRAT PARTI"
    };

    // ── BARLAR (0–100) ────────────────────────────────────────────────────
    public int halk = 50;
    public int din  = 50;
    public int para = 50;
    public int ordu = 50;

    // ── OYUN DURUM DEĞİŞKENLERİ ──────────────────────────────────────────
    public int     year          = 1999;
    public int     decisionCount = 0;
    public int     gamePhase     = PHASE_OPPOSITION;
    public int     partyChoice   = PARTY_HSP;
    public int     currentAct    = 1;
    public boolean oyunBitti     = false;
    public String  endingKey     = "";

    // ── SEÇİM MEKANİZMASI ────────────────────────────────────────────────
    // Son 15 HALK değeri — Perde 4 sonu hesabı için
    public final List<Integer> recentHalkHistory = new ArrayList<>();

    // ── DAL YÖNETİMİ ─────────────────────────────────────────────────────
    public final Set<String>         completedCards = new HashSet<>();
    public final Map<String, String> cardChoices    = new HashMap<>();
    public String activeBranch = "";   // "A", "B" veya ""

    // ══════════════════════════════════════════════════════════════════════
    //  UNVAN
    // ══════════════════════════════════════════════════════════════════════

    public String getTitle() {
        if (gamePhase == PHASE_RULING) return getRulingTitle();
        if (decisionCount <= 5)  return "YENI UYE";
        if (decisionCount <= 15) return "MUFETTIS YARDIMCISI";
        if (decisionCount <= 30) return "BOLGE TEMSILCISI";
        if (decisionCount <= 50) return "PARTI SOZCUSU";
        if (decisionCount <= 70) return "GENEL SEKRETER";
        return "LIDER ADAYI";
    }

    private String getRulingTitle() {
        if (decisionCount <= 10) return "KOALISYON ORTAGI";
        if (decisionCount <= 25) return "BASBAKAN YARDIMCISI";
        if (decisionCount <= 45) return "BASBAKAN";
        if (decisionCount <= 60) return "KALICI BASBAKAN";
        return "TEK ADAM";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARTİ YARDIMCILARI
    // ══════════════════════════════════════════════════════════════════════

    /**
     * @return assets klasörü adı: "HSP", "MNP" veya "LDP"
     */
    public String getScenarioFolder() {
        if (partyChoice >= 0 && partyChoice < PARTY_FOLDERS.length) {
            return PARTY_FOLDERS[partyChoice];
        }
        return "HSP";
    }

    /**
     * @return Ekranda gösterilecek tam parti adı (ASCII safe, Türkçe karakter yok)
     */
    public String getPartyDisplayName() {
        if (partyChoice >= 0 && partyChoice < PARTY_DISPLAY.length) {
            return PARTY_DISPLAY[partyChoice];
        }
        return "BILINMIYOR";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BAR GÜNCELLEME
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Seçim efektlerini barlar üzerine uygular.
     * İktidar evresinde ×1.5 çarpan otomatik uygulanır.
     */
    public void applyEffects(int dHalk, int dDin, int dPara, int dOrdu, int yearOffset) {
        float mult = (gamePhase == PHASE_RULING) ? 1.5f : 1.0f;
        halk = clamp(halk + Math.round(dHalk * mult), 0, 100);
        din  = clamp(din  + Math.round(dDin  * mult), 0, 100);
        para = clamp(para + Math.round(dPara * mult), 0, 100);
        ordu = clamp(ordu + Math.round(dOrdu * mult), 0, 100);

        decisionCount++;
        year += yearOffset;

        // Seçim için HALK geçmişi — max 15 kayıt
        recentHalkHistory.add(halk);
        if (recentHalkHistory.size() > 15) recentHalkHistory.remove(0);

        checkGameOver();
    }

    public void markCardDone(String cardId, String choice) {
        completedCards.add(cardId);
        cardChoices.put(cardId, choice);
    }

    /**
     * Dal belirleme — parti seçimsiz çalışır.
     * Perde 1'in sonunda çağrılır; seçilen karakter kartlarına göre dal belirlenir.
     * HSP: OPP1-003 + OPP1-011 sağ → Dal A
     * MNP: MNP-003 + MNP-011 sağ → Dal A
     * LDP: LDP-003 + LDP-011 sağ → Dal A
     */
    public void determineBranch() {
        String folder = getScenarioFolder();
        String card3  = folder + "-003";
        String card11 = folder + "-011";
        // Prefix OPP1 artık kullanılmıyor; kartlar kendi prefix'leriyle geliyor
        // Geriye uyumluluk için OPP1 da kontrol et
        boolean right3  = "right".equals(cardChoices.get(card3))
                       || "right".equals(cardChoices.get("OPP1-003"));
        boolean right11 = "right".equals(cardChoices.get(card11))
                       || "right".equals(cardChoices.get("OPP1-011"));
        activeBranch = (right3 && right11) ? "A" : "B";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEÇİM HESABI
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    //  FELAKET KONTROLÜ
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    //  SIFIRLAMA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Oyunu sıfırlar. partyChoice KORUNUR — aynı parti ile yeniden başlanabilir.
     */
    public void sifirla() {
        halk = din = para = ordu = 50;
        year = 1999;
        decisionCount = 0;
        gamePhase     = PHASE_OPPOSITION;
        // partyChoice korunuyor — çağıran startGameWithParty() yeniden set eder
        currentAct    = 1;
        oyunBitti     = false;
        endingKey     = "";
        activeBranch  = "";
        recentHalkHistory.clear();
        completedCards.clear();
        cardChoices.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YARDIMCI
    // ══════════════════════════════════════════════════════════════════════

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Bar tehlike seviyesi.
     * @return 0=normal, 1=uyarı (sarı), 2=tehlike (kırmızı)
     */
    public int getDangerLevel(int barValue) {
        if (barValue <= 15 || barValue >= 85) return 2;
        if (barValue <= 25 || barValue >= 75) return 1;
        return 0;
    }
}

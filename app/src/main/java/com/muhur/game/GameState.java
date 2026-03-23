package com.muhur.game;

/**
 * MÜHÜR — GameState
 * Oyun durumunu tutar: istatistikler, tur sayacı, taraf, kayıt.
 *
 * ŞU AN: Temel yapı hazır, ilerleyen sürümde dolacak.
 * Kart sistemi (Reigns mekaniği) buraya entegre edilecek.
 */
public class GameState {

    // ─── TARAFlar ──────────────────────────────────────────────────────────
    public static final int SIDE_NONE       = 0;
    public static final int SIDE_IKTIDAR    = 1;
    public static final int SIDE_MUHALEFET  = 2;

    // ─── İSTATİSTİKLER (0–100) ────────────────────────────────────────────
    public int halk    = 50;
    public int inanc   = 50;
    public int ekonomi = 50;
    public int ordu    = 50;

    // ─── OYUN DURUMU ───────────────────────────────────────────────────────
    public int  tur   = 1;
    public int  taraf = SIDE_NONE;
    public boolean oyunBitti = false;

    /** Tüm istatistikleri başlangıç değerlerine döndür */
    public void sifirla() {
        halk    = 50;
        inanc   = 50;
        ekonomi = 50;
        ordu    = 50;
        tur     = 1;
        taraf   = SIDE_NONE;
        oyunBitti = false;
    }

    /**
     * İstatistik güncelle — 0–100 arasında sınırla.
     * @param delta Değişim miktarı (+/-)
     */
    public void halkEkle(int delta) {
        halk = clamp(halk + delta, 0, 100);
        checkGameOver();
    }

    public void inancEkle(int delta) {
        inanc = clamp(inanc + delta, 0, 100);
        checkGameOver();
    }

    public void ekonomiEkle(int delta) {
        ekonomi = clamp(ekonomi + delta, 0, 100);
        checkGameOver();
    }

    public void orduEkle(int delta) {
        ordu = clamp(ordu + delta, 0, 100);
        checkGameOver();
    }

    /** Herhangi bir istatistik 0'a veya 100'e ulaştıysa oyun biter */
    private void checkGameOver() {
        if (halk == 0 || halk == 100 ||
            inanc == 0 || inanc == 100 ||
            ekonomi == 0 || ekonomi == 100 ||
            ordu == 0 || ordu == 100) {
            oyunBitti = true;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

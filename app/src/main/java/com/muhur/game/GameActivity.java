package com.muhur.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MÜHÜR — GameActivity  (Revizyon 3)
 *
 * Değişiklikler:
 *   1. MÜZİK: muzik1.mp3 / muzik2.mp3 dönüşümlü çalar (menüden oyun sonuna)
 *   2. LIFECYCLE: onPause → müzik duraklat, onResume → devam et
 *   3. SES SEVİYESİ: SharedPreferences'tan yüklenir, Renderer slider callback'i ile güncellenir
 *   4. MusicController interface — Renderer, Activity'ye bağımlı olmadan müzik kontrol eder
 */
public class GameActivity extends Activity implements GameRenderer.MusicController {

    // ─── SABITLER ──────────────────────────────────────────────────────────
    private static final String PREFS_NAME  = "muhur_prefs";
    private static final String KEY_VOLUME  = "music_volume";   // 0.0 – 1.0
    private static final float  DEFAULT_VOL = 0.75f;

    // ─── ALANLAR ───────────────────────────────────────────────────────────
    private GameView    mGameView;
    private GameRenderer mRenderer;

    /** Şu an çalan player (A/B dönüşüm için ikisi de tutulur) */
    private MediaPlayer mPlayerA;   // muzik1.mp3
    private MediaPlayer mPlayerB;   // muzik2.mp3
    private MediaPlayer mCurrent;   // şu an aktif olan

    private float   mVolume      = DEFAULT_VOL;
    private boolean mMusicPaused = false;   // Activity pause'dan mı geldi?

    // ══════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupCrashHandler();

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        setImmersive();

        // Kaydedilmiş ses seviyesini yükle
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mVolume = prefs.getFloat(KEY_VOLUME, DEFAULT_VOL);

        // Renderer'a MusicController referansını ver
        mRenderer = new GameRenderer(this, this);

        mGameView = new GameView(this, mRenderer);
        setContentView(mGameView);

        // Müziği hazırla — henüz başlatma (Renderer hazır değil)
        initMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGameView.onResume();
        setImmersive();

        // Activity pause'dan dönüyorsa müziği devam ettir
        if (mMusicPaused && mCurrent != null) {
            mCurrent.start();
            mMusicPaused = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGameView.onPause();

        // Müziği duraklat (release etme — sadece arka plana geçiş)
        if (mCurrent != null && mCurrent.isPlaying()) {
            mCurrent.pause();
            mMusicPaused = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMusic();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersive();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MusicController INTERFACE  (GameRenderer → GameActivity)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Renderer menüye geçince müziği başlat.
     * GL thread'inden çağrılır — runOnUiThread ile ana thread'e taşı.
     */
    @Override
    public void onMusicStart() {
        runOnUiThread(() -> {
            if (mCurrent != null && mCurrent.isPlaying()) return;
            startTrack(mPlayerA);
        });
    }

    /**
     * Ses seviyesi değişti (0.0 – 1.0).
     * Slider'dan çağrılır, SharedPreferences'a kaydeder.
     */
    @Override
    public void onVolumeChanged(float vol) {
        mVolume = Math.max(0f, Math.min(1f, vol));
        applyVolume();
        // Kalıcı kaydet
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(KEY_VOLUME, mVolume)
                .apply();
    }

    /** Renderer başlangıçta mevcut sesi sorgular */
    @Override
    public float getVolume() {
        return mVolume;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MÜZİK YARDIMCI METODLARI
    // ══════════════════════════════════════════════════════════════════════

    private void initMusic() {
        try {
            mPlayerA = MediaPlayer.create(this, R.raw.muzik1);
            mPlayerB = MediaPlayer.create(this, R.raw.muzik2);

            if (mPlayerA != null) {
                mPlayerA.setLooping(false);
                mPlayerA.setVolume(mVolume, mVolume);
                // A bitince B'yi başlat
                mPlayerA.setOnCompletionListener(mp -> startTrack(mPlayerB));
            }
            if (mPlayerB != null) {
                mPlayerB.setLooping(false);
                mPlayerB.setVolume(mVolume, mVolume);
                // B bitince A'yı başlat
                mPlayerB.setOnCompletionListener(mp -> startTrack(mPlayerA));
            }
        } catch (Exception e) {
            // Ses dosyası yoksa sessiz devam et
            mPlayerA = null;
            mPlayerB = null;
        }
    }

    private void startTrack(MediaPlayer player) {
        if (player == null) return;
        try {
            // Önceki track'i durdur (aynı player değilse)
            if (mCurrent != null && mCurrent != player && mCurrent.isPlaying()) {
                mCurrent.pause();
                mCurrent.seekTo(0);
            }
            mCurrent = player;
            mCurrent.seekTo(0);
            mCurrent.setVolume(mVolume, mVolume);
            mCurrent.start();
        } catch (Exception ignored) {}
    }

    private void applyVolume() {
        if (mPlayerA != null) mPlayerA.setVolume(mVolume, mVolume);
        if (mPlayerB != null) mPlayerB.setVolume(mVolume, mVolume);
    }

    private void releaseMusic() {
        if (mPlayerA != null) { mPlayerA.release(); mPlayerA = null; }
        if (mPlayerB != null) { mPlayerB.release(); mPlayerB = null; }
        mCurrent = null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  IMMERSIVE MOD
    // ══════════════════════════════════════════════════════════════════════

    private void setImmersive() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CRASH HANDLER  (değişmedi)
    // ══════════════════════════════════════════════════════════════════════

    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                File crashFile;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File externalDir = getExternalFilesDir(null);
                    crashFile = (externalDir != null)
                            ? new File(externalDir, "muhur_crash.txt")
                            : new File(getFilesDir(), "muhur_crash.txt");
                } else {
                    crashFile = new File(
                            android.os.Environment.getExternalStorageDirectory(),
                            "muhur_crash.txt"
                    );
                }
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date());
                PrintWriter pw = new PrintWriter(new FileWriter(crashFile, true));
                pw.println("=== MÜHÜR CRASH REPORT ===");
                pw.println("Zaman    : " + ts);
                pw.println("Thread   : " + thread.getName());
                pw.println("Cihaz    : " + Build.MANUFACTURER + " " + Build.MODEL);
                pw.println("Android  : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println("Hata     : " + ex.getMessage());
                pw.println("Stack Trace:");
                ex.printStackTrace(pw);
                pw.println("==========================");
                pw.println();
                pw.flush();
                pw.close();
            } catch (Exception ignored) {}

            if (defaultHandler != null) defaultHandler.uncaughtException(thread, ex);
        });
    }
}

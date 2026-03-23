package com.muhur.game;

import android.app.Activity;
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
 * MÜHÜR — GameActivity
 * Ana Activity: tam ekran, GLSurfaceView yönetimi, hata yakalama.
 */
public class GameActivity extends Activity {

    private GameView mGameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Crash handler — logcat olmadan da hataları okuyabilmek için
        setupCrashHandler();

        // Tam ekran + ekranı açık tut
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Immersive mod (Android 4.4+)
        setImmersive();

        // GameView oluştur ve ekrana yerleştir
        mGameView = new GameView(this);
        setContentView(mGameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGameView.onResume();
        setImmersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGameView.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersive();
        }
    }

    /** Immersive tam ekran modu */
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

    /**
     * Global hata yakalayıcı.
     * Hatayı /sdcard/muhur_crash.txt dosyasına yazar.
     * LADB/Shizuku olmadan da hataları okuyabilmek için.
     */
    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                // /sdcard/ — Android 9 ve altında WRITE_EXTERNAL_STORAGE ile çalışır
                // Android 10+ için getExternalFilesDir kullanılıyor (izin gerekmez)
                File crashFile;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File externalDir = getExternalFilesDir(null);
                    if (externalDir != null) {
                        crashFile = new File(externalDir, "muhur_crash.txt");
                    } else {
                        crashFile = new File(getFilesDir(), "muhur_crash.txt");
                    }
                } else {
                    crashFile = new File(
                            android.os.Environment.getExternalStorageDirectory(),
                            "muhur_crash.txt"
                    );
                }

                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                ).format(new Date());

                PrintWriter pw = new PrintWriter(new FileWriter(crashFile, true));
                pw.println("=== MÜHÜR CRASH REPORT ===");
                pw.println("Zaman    : " + timestamp);
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
            } catch (Exception ignored) {
                // Dosyaya yazamazsak bile uygulamayı çökertme
            }

            // Varsayılan handler'a devret (sistemin normal çökme davranışı)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            }
        });
    }
}

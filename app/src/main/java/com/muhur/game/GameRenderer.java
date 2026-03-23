package com.muhur.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.view.MotionEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * MÜHÜR — GameRenderer
 * Tüm oyun mantığı ve OpenGL ES 2.0 render işlemleri burada.
 *
 * State Makinesi:
 *   ST_SPLASH   → BoneCastOfficial ekranı (120 frame)
 *   ST_CINEMA   → 6 sinematik sahne, daktilo efekti
 *   ST_MENU     → Ana menü (4 buton)
 *   ST_SETTINGS → Ayarlar (ses + FPS)
 *   ST_GAME     → Oyun (ilerleyen sürümde)
 *
 * Koordinat sistemi: (0,0)=sol üst, (W,H)=sağ alt (piksel)
 * Shader aPos uniform'u bu sistemi OpenGL NDC'ye dönüştürür.
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ─── STATE SABİTLERİ ───────────────────────────────────────────────────
    private static final int ST_SPLASH   = 0;
    private static final int ST_CINEMA   = 1;
    private static final int ST_MENU     = 2;
    private static final int ST_SETTINGS = 3;
    private static final int ST_GAME     = 4;

    // ─── RENK PALETİ ───────────────────────────────────────────────────────
    private static final float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f}; // #0d0a05
    private static final float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f}; // #d4a853
    private static final float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f}; // Koyu altın
    private static final float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f}; // Buton arka planı
    private static final float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f}; // Basılı buton
    private static final float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f}; // Pasif buton
    private static final float[] C_WHT  = {0.900f, 0.900f, 0.880f, 1.0f}; // Açık krem
    private static final float[] C_SCAN = {0.000f, 0.000f, 0.000f, 0.18f};// Tarama çizgisi

    // ─── SİNEMATİK VERİ ────────────────────────────────────────────────────
    private static final String[] CINEMA_TEXT = {
        "YIL 1999. PARALEL TURKIYE.\nBIR DARBE OLDU.\nAMA KIMSE KONUSMUYOR.",
        "YENİ BIR DUZEN ILAN EDILDI.\nKURALLAR DEGISTI.\nSENIN KURALLARIN DA.",
        "BUROKRATIN MASASINDA\nBIR MUHUR VAR.\nO MUHUR SENIN.",
        "HALK SORMUYOR.\nHALK BEKLIYOR.\nSEN KARAR VERECEKSIN.",
        "GOLGEDEKI SESLER BUYUYOR.\nMUHALEFET ORGULUYOR.\nYA SEN?",
        "ISTE O MUHUR.\nKADERIN, MUHRURUN UCUNDA."
    };

    private static final String[] CINEMA_ASSETS = {
        "story_bg_1.png", "story_bg_2.png", "story_bg_3.png",
        "story_bg_4.png", "story_bg_5.png", "story_bg_6.png"
    };

    // ─── SHADER KAYNAK KODLARI ─────────────────────────────────────────────

    /**
     * Vertex Shader — piksel koordinat sistemi
     * nx = (x / W) * 2 - 1      → [-1, +1]
     * ny = -(y / H) * 2 + 1     → [+1, -1]  (Y eksenini çevirir)
     */
    private static final String VERT_SRC =
        "attribute vec2 aPos;\n" +
        "uniform   vec2 uRes;\n" +
        "void main() {\n" +
        "    float nx =  (aPos.x / uRes.x) * 2.0 - 1.0;\n" +
        "    float ny = -(aPos.y / uRes.y) * 2.0 + 1.0;\n" +
        "    gl_Position = vec4(nx, ny, 0.0, 1.0);\n" +
        "}\n";

    /** Fragment Shader — düz renk */
    private static final String FRAG_SRC =
        "precision mediump float;\n" +
        "uniform vec4 uColor;\n" +
        "void main() {\n" +
        "    gl_FragColor = uColor;\n" +
        "}\n";

    /** Vertex Shader — texture */
    private static final String VERT_TEX_SRC =
        "attribute vec2 aPos;\n" +
        "attribute vec2 aUV;\n" +
        "uniform   vec2 uRes;\n" +
        "varying   vec2 vUV;\n" +
        "void main() {\n" +
        "    float nx =  (aPos.x / uRes.x) * 2.0 - 1.0;\n" +
        "    float ny = -(aPos.y / uRes.y) * 2.0 + 1.0;\n" +
        "    gl_Position = vec4(nx, ny, 0.0, 1.0);\n" +
        "    vUV = aUV;\n" +
        "}\n";

    /** Fragment Shader — texture */
    private static final String FRAG_TEX_SRC =
        "precision mediump float;\n" +
        "uniform sampler2D uTex;\n" +
        "uniform float     uAlpha;\n" +
        "varying vec2      vUV;\n" +
        "void main() {\n" +
        "    vec4 c = texture2D(uTex, vUV);\n" +
        "    gl_FragColor = vec4(c.rgb, c.a * uAlpha);\n" +
        "}\n";

    // ─── GL PROGRAMLAR & UNİFORM LOKASYONLARI ─────────────────────────────
    private int mProgCol;          // Düz renk programı
    private int mLocAPos;          // aPos attribute
    private int mLocURes;          // uRes uniform
    private int mLocUColor;        // uColor uniform

    private int mProgTex;          // Texture programı
    private int mLocTAPos;         // aPos (tex)
    private int mLocTAUV;          // aUV  (tex)
    private int mLocTURes;         // uRes (tex)
    private int mLocTUTex;         // uTex (tex)
    private int mLocTUAlpha;       // uAlpha (tex)

    // ─── VERTEX BUFFER ─────────────────────────────────────────────────────
    private FloatBuffer mVtxBuf;   // 4 köşe × 2 float = 8 float

    // ─── TEXTURE ID'LERİ ───────────────────────────────────────────────────
    private int mTexLogo   = -1;
    private int mTexMenuBg = -1;
    private int[] mTexCinema = new int[6];

    // ─── EKRAN BOYUTU ──────────────────────────────────────────────────────
    private float mW, mH;

    // ─── OYUN DURUMU ───────────────────────────────────────────────────────
    private int mState = ST_SPLASH;
    private int mFrame = 0;

    // Splash
    private float mSplashAlpha = 0f;
    private static final int SPLASH_FRAMES = 120;

    // Cinema
    private int    mCinemaScene  = 0;        // 0–5
    private int    mCinemaChar   = 0;        // Görünen karakter sayısı
    private long   mCinemaLast   = 0;        // Son karakter zamanı (ms)
    private boolean mCinemaDone  = false;    // Metin tamamlandı mı
    private boolean mSkipHeld    = false;    // SKIP basılı mı
    private long   mSkipStart    = 0;        // SKIP başlangıç zamanı
    private static final long TYPEWRITER_MS  = 60;   // ms/karakter
    private static final long SKIP_HOLD_MS   = 3000; // 3 sn basılı tut

    // Menu
    private int  mMenuHover  = -1;           // Hangi buton hover
    private int  mMenuPress  = -1;           // Hangi buton basılı

    // Settings
    private boolean mSoundOn = true;
    private int     mFps     = 60;
    private int     mSetHover = -1;
    private int     mSetPress = -1;

    // ─── CONTEXT & ASSETS ──────────────────────────────────────────────────
    private final Context mCtx;
    private final AssetManager mAssets;

    // ─── PIXEL SIZE (font boyutu) ──────────────────────────────────────────
    private float gPS = 3f; // onSurfaceChanged'de güncellenir

    // ─── TOUCH ─────────────────────────────────────────────────────────────
    private float mTouchX, mTouchY;
    private int   mTouchAction;

    public GameRenderer(Context ctx) {
        mCtx    = ctx;
        mAssets = ctx.getAssets();

        // Texture dizisini -1 ile başlat (yüklenmemiş)
        for (int i = 0; i < 6; i++) mTexCinema[i] = -1;

        // Vertex buffer: 4 köşe, her biri 2 float (x, y)
        ByteBuffer bb = ByteBuffer.allocateDirect(8 * 4);
        bb.order(ByteOrder.nativeOrder());
        mVtxBuf = bb.asFloatBuffer();
    }

    // ══════════════════════════════════════════════════════════════════════
    // GLSurfaceView.Renderer callback'leri
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig config) {
        // Düz renk programı
        mProgCol   = buildProgram(VERT_SRC, FRAG_SRC);
        mLocAPos   = GLES20.glGetAttribLocation (mProgCol, "aPos");
        mLocURes   = GLES20.glGetUniformLocation(mProgCol, "uRes");
        mLocUColor = GLES20.glGetUniformLocation(mProgCol, "uColor");

        // Texture programı
        mProgTex    = buildProgram(VERT_TEX_SRC, FRAG_TEX_SRC);
        mLocTAPos   = GLES20.glGetAttribLocation (mProgTex, "aPos");
        mLocTAUV    = GLES20.glGetAttribLocation (mProgTex, "aUV");
        mLocTURes   = GLES20.glGetUniformLocation(mProgTex, "uRes");
        mLocTUTex   = GLES20.glGetUniformLocation(mProgTex, "uTex");
        mLocTUAlpha = GLES20.glGetUniformLocation(mProgTex, "uAlpha");

        // Blend — texture şeffaflığı için
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Asset texture'larını yükle
        mTexLogo   = loadTexture("logo.png");
        mTexMenuBg = loadTexture("menu_bg.png");
        for (int i = 0; i < 6; i++) {
            mTexCinema[i] = loadTexture(CINEMA_ASSETS[i]);
        }

        // Cinema zamanlayıcıyı başlat
        mCinemaLast = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        mW = width;
        mH = height;
        GLES20.glViewport(0, 0, width, height);

        // Pixel font boyutu — ekran genişliğine orantılı
        gPS = Math.max(2f, mW / 120f);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        mFrame++;
        update();
        render();
    }

    // ══════════════════════════════════════════════════════════════════════
    // TOUCH (GL thread'inden çağrılır — queueEvent ile)
    // ══════════════════════════════════════════════════════════════════════

    public void onTouch(int action, float x, float y, long time) {
        mTouchAction = action;
        mTouchX      = x;
        mTouchY      = y;

        switch (mState) {
            case ST_SPLASH:   touchSplash(action, x, y);   break;
            case ST_CINEMA:   touchCinema(action, x, y, time); break;
            case ST_MENU:     touchMenu(action, x, y);     break;
            case ST_SETTINGS: touchSettings(action, x, y); break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GÜNCELLEME (her frame)
    // ══════════════════════════════════════════════════════════════════════

    private void update() {
        switch (mState) {
            case ST_SPLASH:   updateSplash();   break;
            case ST_CINEMA:   updateCinema();   break;
        }
    }

    private void updateSplash() {
        // Alpha fade-in 0→1 ilk 30 frame, 1→0 son 30 frame
        if (mFrame < 30) {
            mSplashAlpha = mFrame / 30f;
        } else if (mFrame > SPLASH_FRAMES - 30) {
            mSplashAlpha = (SPLASH_FRAMES - mFrame) / 30f;
        } else {
            mSplashAlpha = 1f;
        }
        if (mFrame >= SPLASH_FRAMES) {
            mFrame = 0;
            mCinemaScene = 0;
            mCinemaChar  = 0;
            mCinemaDone  = false;
            mCinemaLast  = System.currentTimeMillis();
            mState = ST_CINEMA;
        }
    }

    private void updateCinema() {
        if (mCinemaDone) return;

        String text = CINEMA_TEXT[mCinemaScene];
        long now = System.currentTimeMillis();
        if (now - mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast = now;
            if (mCinemaChar < text.length()) {
                mCinemaChar++;
            } else {
                mCinemaDone = true;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER ANA METODU
    // ══════════════════════════════════════════════════════════════════════

    private void render() {
        // Arka planı temizle
        GLES20.glClearColor(C_BG[0], C_BG[1], C_BG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        switch (mState) {
            case ST_SPLASH:   renderSplash();   break;
            case ST_CINEMA:   renderCinema();   break;
            case ST_MENU:     renderMenu();     break;
            case ST_SETTINGS: renderSettings(); break;
            case ST_GAME:     renderGame();     break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SPLASH EKRANI
    // ══════════════════════════════════════════════════════════════════════

    private void renderSplash() {
        float cx = mW / 2f;
        float cy = mH / 2f;

        // Logo texture varsa göster, yoksa metin
        if (mTexLogo > 0) {
            float lw = mW * 0.5f;
            float lh = lw; // kare logo varsayımı
            drawTex(mTexLogo, cx - lw/2f, cy - lh/2f - mH*0.08f, lw, lh, mSplashAlpha);
        }

        // BoneCastOfficial yazısı
        float ps = gPS * 1.1f;
        float textAlpha = mSplashAlpha;
        float[] col = {C_GOLD[0], C_GOLD[1], C_GOLD[2], textAlpha};
        drawStringC("BoneCastOfficial", cy + mH * 0.08f, ps, col);

        // İlerleme çubuğu
        float barW = mW * 0.5f;
        float barH = gPS * 1.5f;
        float barX = cx - barW / 2f;
        float barY = mH * 0.85f;
        float prog = Math.min(1f, mFrame / (float) SPLASH_FRAMES);

        float[] dimCol = {C_DARK[0], C_DARK[1], C_DARK[2], textAlpha};
        float[] fillCol = {C_GOLD[0], C_GOLD[1], C_GOLD[2], textAlpha};

        rect(barX, barY, barW, barH, dimCol);
        if (prog > 0) rect(barX, barY, barW * prog, barH, fillCol);

        scanLines();
    }

    private void touchSplash(int action, float x, float y) {
        // Dokunuşta splash'ı atla
        if (action == MotionEvent.ACTION_DOWN) {
            mFrame = SPLASH_FRAMES;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SİNEMATİK
    // ══════════════════════════════════════════════════════════════════════

    private void renderCinema() {
        // Arka plan texture
        int bg = mTexCinema[mCinemaScene];
        if (bg > 0) {
            drawTex(bg, 0, 0, mW, mH, 0.85f);
            // Koyu overlay — metin okunabilirliği
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.50f};
            rect(0, 0, mW, mH, ov);
        }

        // Sahne numarası (üst sağ)
        float[] greyAlpha = {C_GREY[0], C_GREY[1], C_GREY[2], 0.7f};
        String sceneNum = (mCinemaScene + 1) + "/" + CINEMA_TEXT.length;
        drawString(sceneNum, mW - 60 * gPS/3f, gPS * 3f, gPS * 0.8f, greyAlpha);

        // Daktilo metni
        String fullText = CINEMA_TEXT[mCinemaScene];
        String visible  = fullText.substring(0, Math.min(mCinemaChar, fullText.length()));

        float textY = mH * 0.55f;
        float ps    = gPS * 1.1f;

        // Metni satırlara böl (\n)
        String[] lines = visible.split("\n", -1);
        float lineH = ps * 10f;
        for (int i = 0; i < lines.length; i++) {
            drawStringC(lines[i], textY + i * lineH, ps, C_GOLD);
        }

        // İmleç (metin bitmemişse yanıp söner)
        if (!mCinemaDone) {
            if ((mFrame / 15) % 2 == 0) {
                float[] lines2 = {0f};
                // Son satırın sonu
                String lastLine = lines[lines.length - 1];
                float curX = mW/2f + lastLine.length() * ps * 6f / 2f + ps;
                float curY = textY + (lines.length - 1) * lineH;
                rect(curX, curY, ps, ps * 8f, C_GOLD);
            }
        }

        // DEVAM butonu (metin bittiyse)
        if (mCinemaDone) {
            float bw = mW * 0.35f;
            float bh = gPS * 10f;
            float bx = mW / 2f - bw / 2f;
            float by = mH * 0.82f;
            boolean hover = (mSetHover == 99);
            drawButton("DEVAM", bx, by, bw, bh, hover, false, false);
        }

        // SKIP butonu (sol alt)
        float sw = mW * 0.25f;
        float sh = gPS * 7f;
        drawButton("ATLA", gPS * 2f, mH - sh - gPS * 3f, sw, sh, false, mSkipHeld, false);

        // SKIP basılı tutma çubuğu
        if (mSkipHeld) {
            long held = System.currentTimeMillis() - mSkipStart;
            float prog = Math.min(1f, held / (float) SKIP_HOLD_MS);
            float bw2 = mW * 0.25f;
            rect(gPS * 2f, mH - gPS * 2f, bw2 * prog, gPS * 1.5f, C_GOLD);
            if (held >= SKIP_HOLD_MS) {
                skipAllCinema();
            }
        }

        scanLines();
    }

    private void touchCinema(int action, float x, float y, long time) {
        float bw = mW * 0.35f;
        float bh = gPS * 10f;
        float bx = mW / 2f - bw / 2f;
        float by = mH * 0.82f;

        if (action == MotionEvent.ACTION_DOWN) {
            // Metin tam değilse: anında tamamla
            if (!mCinemaDone) {
                mCinemaChar = CINEMA_TEXT[mCinemaScene].length();
                mCinemaDone = true;
                return;
            }
            // DEVAM butonu
            if (mCinemaDone && hit(x, y, bx, by, bw, bh)) {
                nextCinemaScene();
                return;
            }
            // SKIP
            float sw = mW * 0.25f;
            float sh = gPS * 7f;
            if (hit(x, y, gPS * 2f, mH - sh - gPS * 3f, sw, sh)) {
                mSkipHeld  = true;
                mSkipStart = System.currentTimeMillis();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mSkipHeld = false;
            mSetHover = -1;
        }
    }

    private void nextCinemaScene() {
        mCinemaScene++;
        if (mCinemaScene >= CINEMA_TEXT.length) {
            // Sinematik bitti → Menüye geç
            mState = ST_MENU;
            mMenuHover = -1;
            mMenuPress = -1;
        } else {
            mCinemaChar = 0;
            mCinemaDone = false;
            mCinemaLast = System.currentTimeMillis();
        }
    }

    private void skipAllCinema() {
        mSkipHeld  = false;
        mState     = ST_MENU;
        mMenuHover = -1;
        mMenuPress = -1;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANA MENÜ
    // ══════════════════════════════════════════════════════════════════════

    private static final String[] MENU_LABELS = {
        "YENİ OYUN", "DEVAM ET", "AYARLAR", "CIKIS"
    };

    private void renderMenu() {
        // Arka plan
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.7f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.55f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;

        // Başlık: MÜHÜR
        float titlePS = gPS * 2.5f;
        drawStringC("MUHUR", mH * 0.18f, titlePS, C_GOLD);

        // Motto
        float mottoPS = gPS * 0.75f;
        drawStringC("Kaderin, muhrurun ucunda.", mH * 0.30f, mottoPS, C_GDIM);

        // Geliştirici
        drawStringC("BoneCastOfficial", mH * 0.36f, gPS * 0.65f, C_GREY);

        // Butonlar
        float bw = mW * 0.60f;
        float bh = gPS * 10f;
        float bx = cx - bw / 2f;
        float gap = bh + gPS * 3f;
        float startY = mH * 0.48f;

        for (int i = 0; i < MENU_LABELS.length; i++) {
            float by = startY + i * gap;
            boolean disabled = (i == 1); // Devam Et — kayıt yoksa pasif
            drawButton(MENU_LABELS[i], bx, by, bw, bh,
                    mMenuHover == i, mMenuPress == i, disabled);
        }

        // Versiyon
        float[] greyDim = {C_GREY[0], C_GREY[1], C_GREY[2], 0.5f};
        drawString("v0.1.0", gPS * 2f, mH - gPS * 5f, gPS * 0.7f, greyDim);

        scanLines();
    }

    private void touchMenu(int action, float x, float y) {
        float bw = mW * 0.60f;
        float bh = gPS * 10f;
        float bx = mW / 2f - bw / 2f;
        float gap = bh + gPS * 3f;
        float startY = mH * 0.48f;

        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < MENU_LABELS.length; i++) {
                float by = startY + i * gap;
                if (hit(x, y, bx, by, bw, bh)) {
                    mMenuPress = i;
                    mMenuHover = i;
                    return;
                }
            }
            mMenuPress = -1;
        } else if (action == MotionEvent.ACTION_MOVE) {
            mMenuHover = -1;
            for (int i = 0; i < MENU_LABELS.length; i++) {
                float by = startY + i * gap;
                if (hit(x, y, bx, by, bw, bh)) {
                    mMenuHover = i;
                    break;
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            int pressed = mMenuPress;
            mMenuPress = -1;
            mMenuHover = -1;

            if (pressed < 0) return;
            float by = startY + pressed * gap;
            if (!hit(x, y, bx, by, bw, bh)) return;

            switch (pressed) {
                case 0: // Yeni Oyun
                    mState = ST_GAME;
                    break;
                case 1: // Devam Et — pasif
                    break;
                case 2: // Ayarlar
                    mSetHover = -1;
                    mSetPress = -1;
                    mState = ST_SETTINGS;
                    break;
                case 3: // Çıkış
                    if (mCtx instanceof android.app.Activity) {
                        ((android.app.Activity) mCtx).finish();
                    }
                    break;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AYARLAR
    // ══════════════════════════════════════════════════════════════════════

    private void renderSettings() {
        // Arka plan
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.5f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.65f};
            rect(0, 0, mW, mH, ov);
        }

        // Başlık
        drawStringC("AYARLAR", mH * 0.15f, gPS * 1.8f, C_GOLD);

        float cx = mW / 2f;
        float ps = gPS;

        // ─── Ses ───
        float row1Y = mH * 0.38f;
        float labelX = mW * 0.12f;
        drawString("SES:", labelX, row1Y, ps, C_GOLD);

        float togW = mW * 0.22f;
        float togH = gPS * 9f;
        float togGap = togW + gPS * 3f;
        float togX0 = cx - togW - gPS * 1.5f;

        // ACIK
        boolean soundOnHov  = (mSetHover == 0);
        boolean soundOnPres = (mSetPress == 0);
        drawButton("ACIK",  togX0,           row1Y, togW, togH,
                soundOnHov, soundOnPres, !mSoundOn);

        // KAPALI
        boolean soundOffHov  = (mSetHover == 1);
        boolean soundOffPres = (mSetPress == 1);
        drawButton("KAPALI", togX0 + togGap, row1Y, togW, togH,
                soundOffHov, soundOffPres, mSoundOn);

        // ─── FPS ───
        float row2Y = mH * 0.55f;
        drawString("FPS:", labelX, row2Y, ps, C_GOLD);

        boolean fps30Hov  = (mSetHover == 2);
        boolean fps30Pres = (mSetPress == 2);
        drawButton("30", togX0,           row2Y, togW, togH,
                fps30Hov, fps30Pres, mFps != 30);

        boolean fps60Hov  = (mSetHover == 3);
        boolean fps60Pres = (mSetPress == 3);
        drawButton("60", togX0 + togGap, row2Y, togW, togH,
                fps60Hov, fps60Pres, mFps != 60);

        // ─── Geri ───
        float backW = mW * 0.40f;
        float backH = gPS * 10f;
        float backX = cx - backW / 2f;
        float backY = mH * 0.78f;
        drawButton("GERI", backX, backY, backW, backH,
                mSetHover == 10, mSetPress == 10, false);

        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx    = mW / 2f;
        float togW  = mW * 0.22f;
        float togH  = gPS * 9f;
        float togGap= togW + gPS * 3f;
        float togX0 = cx - togW - gPS * 1.5f;
        float row1Y = mH * 0.38f;
        float row2Y = mH * 0.55f;
        float backW = mW * 0.40f;
        float backH = gPS * 10f;
        float backX = cx - backW / 2f;
        float backY = mH * 0.78f;

        if (action == MotionEvent.ACTION_DOWN) {
            if      (hit(x, y, togX0,          row1Y, togW, togH)) mSetPress = 0;
            else if (hit(x, y, togX0 + togGap, row1Y, togW, togH)) mSetPress = 1;
            else if (hit(x, y, togX0,          row2Y, togW, togH)) mSetPress = 2;
            else if (hit(x, y, togX0 + togGap, row2Y, togW, togH)) mSetPress = 3;
            else if (hit(x, y, backX,          backY, backW, backH)) mSetPress = 10;
            else mSetPress = -1;
            mSetHover = mSetPress;

        } else if (action == MotionEvent.ACTION_UP) {
            int p = mSetPress;
            mSetPress = -1;
            mSetHover = -1;
            switch (p) {
                case 0:  mSoundOn = true;  break;
                case 1:  mSoundOn = false; break;
                case 2:  mFps = 30;        break;
                case 3:  mFps = 60;        break;
                case 10:
                    if (hit(x, y, backX, backY, backW, backH)) {
                        mState = ST_MENU;
                    }
                    break;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mSetPress = -1;
            mSetHover = -1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // OYUN (ilerleyen sürümde doldurulacak)
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        drawStringC("OYUN GELIYOR...", mH * 0.45f, gPS * 1.5f, C_GOLD);
        drawStringC("(Beklenti: Kart sistemi)", mH * 0.56f, gPS * 0.8f, C_GDIM);

        // Geri butonu (test için)
        float bw = mW * 0.40f;
        float bh = gPS * 10f;
        float bx = mW / 2f - bw / 2f;
        float by = mH * 0.75f;
        drawButton("MENUYE DON", bx, by, bw, bh, false, false, false);

        if (mTouchAction == MotionEvent.ACTION_UP && hit(mTouchX, mTouchY, bx, by, bw, bh)) {
            mState = ST_MENU;
            mTouchAction = -1;
        }
        scanLines();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ÇİZİM METODLARI
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Dolu dikdörtgen çiz.
     * @param x  Sol üst X (piksel)
     * @param y  Sol üst Y (piksel)
     * @param w  Genişlik
     * @param h  Yükseklik
     * @param c  RGBA float[4]
     */
    private void rect(float x, float y, float w, float h, float[] c) {
        GLES20.glUseProgram(mProgCol);
        GLES20.glUniform2f(mLocURes, mW, mH);
        GLES20.glUniform4f(mLocUColor, c[0], c[1], c[2], c[3]);

        mVtxBuf.position(0);
        mVtxBuf.put(x    ); mVtxBuf.put(y    );
        mVtxBuf.put(x + w); mVtxBuf.put(y    );
        mVtxBuf.put(x    ); mVtxBuf.put(y + h);
        mVtxBuf.put(x + w); mVtxBuf.put(y + h);
        mVtxBuf.position(0);

        GLES20.glEnableVertexAttribArray(mLocAPos);
        GLES20.glVertexAttribPointer(mLocAPos, 2, GLES20.GL_FLOAT, false, 0, mVtxBuf);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mLocAPos);
    }

    /**
     * Texture çiz (TRIANGLE_STRIP, UV 0→1).
     */
    private void drawTex(int texId, float x, float y, float w, float h, float alpha) {
        if (texId <= 0) return;

        GLES20.glUseProgram(mProgTex);
        GLES20.glUniform2f(mLocTURes, mW, mH);
        GLES20.glUniform1i(mLocTUTex, 0);
        GLES20.glUniform1f(mLocTUAlpha, alpha);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);

        // 4 köşe: pos(2) + uv(2) = 4 float her köşe → 16 float
        ByteBuffer bb = ByteBuffer.allocateDirect(16 * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();

        fb.put(x    ); fb.put(y    ); fb.put(0f); fb.put(0f);
        fb.put(x + w); fb.put(y    ); fb.put(1f); fb.put(0f);
        fb.put(x    ); fb.put(y + h); fb.put(0f); fb.put(1f);
        fb.put(x + w); fb.put(y + h); fb.put(1f); fb.put(1f);
        fb.position(0);

        int stride = 4 * 4; // 4 float × 4 byte
        GLES20.glEnableVertexAttribArray(mLocTAPos);
        GLES20.glVertexAttribPointer(mLocTAPos, 2, GLES20.GL_FLOAT, false, stride, fb);

        fb.position(2); // UV offset
        GLES20.glEnableVertexAttribArray(mLocTAUV);
        GLES20.glVertexAttribPointer(mLocTAUV, 2, GLES20.GL_FLOAT, false, stride, fb);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mLocTAPos);
        GLES20.glDisableVertexAttribArray(mLocTAUV);
    }

    /**
     * CRT tarama çizgisi efekti — Papers Please estetiği.
     * Her 3 pikselde bir yarı şeffaf siyah çizgi çizer.
     */
    private void scanLines() {
        float step = 3f;
        for (float y = 0; y < mH; y += step) {
            rect(0, y, mW, 1f, C_SCAN);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PİXEL FONT (Segment tabanlı — her karakter rect() çağrılarıyla)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * String çiz.
     * @param s  Metin (büyük harf önerilir)
     * @param x  Sol üst X
     * @param y  Sol üst Y
     * @param ps Pixel boyutu (font ölçeği)
     * @param c  RGBA
     */
    private void drawString(String s, float x, float y, float ps, float[] c) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toUpperCase(s.charAt(i));
            drawChar(ch, cx, y, ps, c);
            cx += ps * 7f; // karakter genişliği + boşluk
        }
    }

    /**
     * Ortalanmış string çiz.
     * @param s  Metin
     * @param cy Y koordinatı (sol üst)
     * @param ps Pixel boyutu
     * @param c  RGBA
     */
    private void drawStringC(String s, float cy, float ps, float[] c) {
        float totalW = s.length() * ps * 7f;
        float sx = mW / 2f - totalW / 2f;
        drawString(s, sx, cy, ps, c);
    }

    /**
     * Tek karakter çiz — segment tabanlı.
     * Koordinat sistemi: (x,y) = sol üst köşe
     * gPS birimi ile ölçeklenir.
     *
     * Her karakter 5 sütun × 7 satır grid'inde.
     */
    private void drawChar(char ch, float x, float y, float ps, float[] c) {
        // Yatay çubuk (horizontal bar)
        // fH(row): y + row*ps, genişlik 5ps
        // Dikey çubuk (vertical bar)
        // fV(col, rowStart, rowEnd)

        switch (ch) {
            case 'A':
                fH(x,y,ps,1,c); fH(x,y,ps,4,c);
                fV(x,y,ps,0,3,c); fV(x,y,ps,4,3,c);
                fV(x,y,ps,0,4,6,c); fV(x,y,ps,4,4,6,c);
                break;
            case 'B':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c);
                fV(x,y,ps,4,0,2,c); fV(x,y,ps,4,3,5,c);
                break;
            case 'C':
                fH(x,y,ps,0,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c);
                break;
            case 'D':
                fH(x,y,ps,0,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,1,5,c);
                fV(x,y,ps,3,0,0,c); fV(x,y,ps,3,6,6,c);
                break;
            case 'E':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c);
                break;
            case 'F':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c);
                fV(x,y,ps,0,0,6,c);
                break;
            case 'G':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,3,6,c);
                break;
            case 'H':
                fH(x,y,ps,3,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                break;
            case 'I':
                fH(x,y,ps,0,c); fH(x,y,ps,6,c);
                fV(x,y,ps,2,0,6,c);
                break;
            case 'J':
                fH(x,y,ps,0,c); fH(x,y,ps,5,c);
                fV(x,y,ps,4,0,6,c); fV(x,y,ps,0,5,6,c);
                break;
            case 'K':
                fV(x,y,ps,0,0,6,c);
                fH(x,y,ps,3,c);
                fV(x,y,ps,4,0,2,c); fV(x,y,ps,4,4,6,c);
                break;
            case 'L':
                fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c);
                break;
            case 'M':
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                fV(x,y,ps,1,0,2,c); fV(x,y,ps,3,0,2,c);
                fV(x,y,ps,2,1,2,c);
                break;
            case 'N':
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                fV(x,y,ps,1,0,2,c); fV(x,y,ps,3,4,6,c);
                fV(x,y,ps,2,2,4,c);
                break;
            case 'O':
                fH(x,y,ps,0,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                break;
            case 'P':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,3,c);
                break;
            case 'Q':
                fH(x,y,ps,0,c); fH(x,y,ps,5,c);
                fV(x,y,ps,0,0,5,c); fV(x,y,ps,4,0,5,c);
                fV(x,y,ps,3,4,5,c); fV(x,y,ps,4,5,6,c);
                break;
            case 'R':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,3,c);
                fV(x,y,ps,3,4,4,c); fV(x,y,ps,4,5,6,c);
                break;
            case 'S':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,3,c); fV(x,y,ps,4,3,6,c);
                break;
            case 'T':
                fH(x,y,ps,0,c);
                fV(x,y,ps,2,0,6,c);
                break;
            case 'U':
                fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                break;
            case 'V':
                fV(x,y,ps,0,0,4,c); fV(x,y,ps,4,0,4,c);
                fV(x,y,ps,1,4,5,c); fV(x,y,ps,3,4,5,c);
                fV(x,y,ps,2,5,6,c);
                break;
            case 'W':
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                fV(x,y,ps,1,4,6,c); fV(x,y,ps,3,4,6,c);
                fV(x,y,ps,2,5,6,c);
                break;
            case 'X':
                fV(x,y,ps,0,0,2,c); fV(x,y,ps,4,0,2,c);
                fV(x,y,ps,2,2,4,c);
                fV(x,y,ps,0,4,6,c); fV(x,y,ps,4,4,6,c);
                fV(x,y,ps,1,2,3,c); fV(x,y,ps,3,3,4,c);
                break;
            case 'Y':
                fV(x,y,ps,0,0,2,c); fV(x,y,ps,4,0,2,c);
                fV(x,y,ps,2,2,6,c);
                fH(x,y,ps,3,c);
                break;
            case 'Z':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,4,0,3,c); fV(x,y,ps,0,3,6,c);
                break;
            case '0':
                fH(x,y,ps,0,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                fV(x,y,ps,1,1,2,c); fV(x,y,ps,3,3,4,c);
                fV(x,y,ps,2,2,3,c);
                break;
            case '1':
                fV(x,y,ps,2,0,6,c);
                fH(x,y,ps,6,c);
                fV(x,y,ps,1,0,1,c);
                break;
            case '2':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,4,0,3,c); fV(x,y,ps,0,3,6,c);
                break;
            case '3':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,4,0,6,c);
                break;
            case '4':
                fH(x,y,ps,3,c);
                fV(x,y,ps,0,0,3,c); fV(x,y,ps,4,0,6,c);
                break;
            case '5':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,3,c); fV(x,y,ps,4,3,6,c);
                break;
            case '6':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,3,6,c);
                break;
            case '7':
                fH(x,y,ps,0,c); fV(x,y,ps,4,0,6,c);
                break;
            case '8':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,6,c); fV(x,y,ps,4,0,6,c);
                break;
            case '9':
                fH(x,y,ps,0,c); fH(x,y,ps,3,c); fH(x,y,ps,6,c);
                fV(x,y,ps,0,0,3,c); fV(x,y,ps,4,0,6,c);
                break;
            case '.':
                fD(x,y,ps,2,6,c);
                break;
            case ',':
                fD(x,y,ps,2,6,c); fD(x,y,ps,1,7,c);
                break;
            case ':':
                fD(x,y,ps,2,2,c); fD(x,y,ps,2,4,c);
                break;
            case '!':
                fV(x,y,ps,2,0,4,c); fD(x,y,ps,2,6,c);
                break;
            case '?':
                fH(x,y,ps,0,c);
                fV(x,y,ps,4,0,2,c);
                fV(x,y,ps,2,2,3,c); fV(x,y,ps,3,2,3,c);
                fD(x,y,ps,2,6,c);
                break;
            case '/':
                fV(x,y,ps,4,0,2,c); fV(x,y,ps,3,2,4,c);
                fV(x,y,ps,2,4,5,c); fV(x,y,ps,1,5,6,c);
                break;
            case '-':
                fH(x,y,ps,3,c);
                break;
            case '+':
                fH(x,y,ps,3,c); fV(x,y,ps,2,1,5,c);
                break;
            case '(':
                fH(x,y,ps,1,c); fH(x,y,ps,5,c);
                fV(x,y,ps,0,1,5,c);
                break;
            case ')':
                fH(x,y,ps,1,c); fH(x,y,ps,5,c);
                fV(x,y,ps,4,1,5,c);
                break;
            case ' ':
                // boşluk — çizme
                break;
            default:
                // Bilinmeyen karakter: küçük nokta
                fD(x,y,ps,2,3,c);
                break;
        }
    }

    // ─── Font yardımcı metodları ───────────────────────────────────────────

    /** Yatay çubuk — satır row (0–6), 5 piksel genişlik */
    private void fH(float x, float y, float ps, int row, float[] c) {
        rect(x, y + row * ps, 5f * ps, ps, c);
    }

    /** Dikey çubuk — sütun col (0–4), rowStart–rowEnd satırları */
    private void fV(float x, float y, float ps, int col, int rowStart, int rowEnd, float[] c) {
        float ty = y + rowStart * ps;
        float th = (rowEnd - rowStart + 1) * ps;
        rect(x + col * ps, ty, ps, th, c);
    }

    /** Dikey çubuk — col sütunu, row1'den row2'ye (4 param sürümü) */
    private void fV(float x, float y, float ps, int col, int row1, float[] c) {
        fV(x, y, ps, col, 0, row1, c);
    }

    /** Tek piksel nokta */
    private void fD(float x, float y, float ps, int col, int row, float[] c) {
        rect(x + col * ps, y + row * ps, ps, ps, c);
    }

    // ══════════════════════════════════════════════════════════════════════
    // BUTON ÇİZİMİ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Standart menü butonu çiz.
     * @param label    Buton metni
     * @param x,y,w,h  Konum ve boyut
     * @param hover    Mouse/parmak üzerinde mi?
     * @param pressed  Basılı mı?
     * @param disabled Pasif mi?
     */
    private void drawButton(String label, float x, float y, float w, float h,
                            boolean hover, boolean pressed, boolean disabled) {
        float[] bg;
        float[] border;
        float[] textCol;

        if (disabled) {
            bg      = C_DARK;
            border  = C_GREY;
            textCol = C_GREY;
        } else if (pressed) {
            bg      = C_PRES;
            border  = C_GOLD;
            textCol = C_GOLD;
        } else if (hover) {
            bg      = C_DARK;
            border  = C_GOLD;
            textCol = C_GOLD;
        } else {
            bg      = C_DARK;
            border  = C_GDIM;
            textCol = C_GDIM;
        }

        // Arka plan
        rect(x, y, w, h, bg);

        // Kenarlık (1px çizgiler)
        float brd = 2f;
        rect(x,         y,         w,   brd, border); // üst
        rect(x,         y + h - brd, w, brd, border); // alt
        rect(x,         y,         brd, h,   border); // sol
        rect(x + w - brd, y,       brd, h,   border); // sağ

        // Metin (ortala)
        float ps = gPS * 0.85f;
        float textW = label.length() * ps * 7f;
        float tx = x + w / 2f - textW / 2f;
        float ty = y + h / 2f - 3.5f * ps;
        drawString(label, tx, ty, ps, textCol);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEXTURE YÜKLEME
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Asset klasöründen PNG yükle.
     * BitmapFactory tüm PNG formatlarını destekler (stored-block dahil).
     * @param name Asset dosya adı
     * @return OpenGL texture ID, -1 ise hata
     */
    private int loadTexture(String name) {
        try {
            InputStream is = mAssets.open(name);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return -1;

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            bmp.recycle();

            return ids[0];
        } catch (IOException e) {
            // Asset yoksa -1 döndür — kod gracefully handle eder
            return -1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SHADER DERLEME & BAĞLAMA
    // ══════════════════════════════════════════════════════════════════════

    private int buildProgram(String vertSrc, String fragSrc) {
        int vert = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc);
        int frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);

        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vert);
        GLES20.glAttachShader(prog, frag);
        GLES20.glLinkProgram(prog);

        // Bağlantı kontrolü
        int[] status = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(prog);
            GLES20.glDeleteProgram(prog);
            throw new RuntimeException("GL Program link hatası: " + log);
        }

        GLES20.glDeleteShader(vert);
        GLES20.glDeleteShader(frag);
        return prog;
    }

    private int compileShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("GL Shader derleme hatası: " + log);
        }
        return shader;
    }

    // ══════════════════════════════════════════════════════════════════════
    // YARDIMCI
    // ══════════════════════════════════════════════════════════════════════

    /** Nokta dikdörtgen içinde mi? */
    private boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }
}

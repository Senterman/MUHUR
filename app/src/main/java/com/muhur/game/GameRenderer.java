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
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * MÜHÜR — GameRenderer  (Revizyon 2)
 *
 * Düzeltmeler:
 *   1. AKIŞ DÜZELTİLDİ: Splash → MENÜ → (YENİ OYUN basınca) → Sinematik
 *   2. TAŞMA DÜZELTİLDİ: fitTextPS() + wrapText() ile otomatik ölçekleme
 *   3. EKRAN UYUMU: mPad + mUsableW ile güvenli alan, 20:9 desteği
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ─── STATE SABİTLERİ ───────────────────────────────────────────────────
    private static final int ST_SPLASH   = 0;
    private static final int ST_MENU     = 1;   // Splash → MENÜ (düzeltildi)
    private static final int ST_CINEMA   = 2;   // Yeni Oyun → Sinematik
    private static final int ST_SETTINGS = 3;
    private static final int ST_GAME     = 4;

    // ─── RENK PALETİ ───────────────────────────────────────────────────────
    private static final float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f};
    private static final float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f};
    private static final float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f};
    private static final float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f};
    private static final float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f};
    private static final float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f};
    private static final float[] C_SCAN = {0.000f, 0.000f, 0.000f, 0.15f};

    // ─── SİNEMATİK VERİ ────────────────────────────────────────────────────
    private static final String[] CINEMA_TEXT = {
        "YIL 1999. PARALEL TURKIYE.\nBIR DARBE OLDU.\nAMA KIMSE KONUSMUYOR.",
        "YENI BIR DUZEN ILAN EDILDI.\nKURALLAR DEGISTI.\nSENIN KURALLARIN DA.",
        "BUROKRATIN MASASINDA\nBIR MUHUR VAR.\nO MUHUR SENIN.",
        "HALK SORMUYOR.\nHALK BEKLIYOR.\nSEN KARAR VERECEKSIN.",
        "GOLGEDEKI SESLER BUYUYOR.\nMUHALEFET ORGULUYOR.\nYA SEN?",
        "ISTE O MUHUR.\nKADERIN, MUHRURUN UCUNDA."
    };
    private static final String[] CINEMA_ASSETS = {
        "story_bg_1.png","story_bg_2.png","story_bg_3.png",
        "story_bg_4.png","story_bg_5.png","story_bg_6.png"
    };

    // ─── SHADER KAYNAK KODLARI ─────────────────────────────────────────────
    private static final String VERT_SRC =
        "attribute vec2 aPos;\nuniform vec2 uRes;\n" +
        "void main(){\n" +
        "  float nx=(aPos.x/uRes.x)*2.0-1.0;\n" +
        "  float ny=-(aPos.y/uRes.y)*2.0+1.0;\n" +
        "  gl_Position=vec4(nx,ny,0.0,1.0);\n}\n";

    private static final String FRAG_SRC =
        "precision mediump float;\nuniform vec4 uColor;\n" +
        "void main(){gl_FragColor=uColor;}\n";

    private static final String VERT_TEX_SRC =
        "attribute vec2 aPos;attribute vec2 aUV;uniform vec2 uRes;varying vec2 vUV;\n" +
        "void main(){\n" +
        "  float nx=(aPos.x/uRes.x)*2.0-1.0;\n" +
        "  float ny=-(aPos.y/uRes.y)*2.0+1.0;\n" +
        "  gl_Position=vec4(nx,ny,0.0,1.0);vUV=aUV;\n}\n";

    private static final String FRAG_TEX_SRC =
        "precision mediump float;uniform sampler2D uTex;uniform float uAlpha;varying vec2 vUV;\n" +
        "void main(){vec4 c=texture2D(uTex,vUV);gl_FragColor=vec4(c.rgb,c.a*uAlpha);}\n";

    // ─── GL HANDLES ────────────────────────────────────────────────────────
    private int mProgCol, mLocAPos, mLocURes, mLocUColor;
    private int mProgTex, mLocTAPos, mLocTAUV, mLocTURes, mLocTUTex, mLocTUAlpha;
    private FloatBuffer mVtxBuf;

    // ─── TEXTURE ID'LERİ ───────────────────────────────────────────────────
    private int mTexLogo   = -1;
    private int mTexMenuBg = -1;
    private int[] mTexCinema = new int[6];

    // ─── EKRAN & GÜVENLI ALAN ──────────────────────────────────────────────
    private float mW, mH;
    /** Yatay kenar boşluğu — metin bu alanın dışına çıkmaz */
    private float mPad;
    /** Kullanılabilir genişlik = mW - 2*mPad */
    private float mUsableW;

    // ─── FONT ──────────────────────────────────────────────────────────────
    /** Temel piksel boyutu — onSurfaceChanged'de ekrana göre hesaplanır */
    private float gPS;

    // ─── OYUN DURUMU ───────────────────────────────────────────────────────
    private int   mState = ST_SPLASH;
    private int   mFrame = 0;

    // Splash
    private float   mSplashAlpha = 0f;
    private static final int SPLASH_FRAMES = 120;

    // Cinema
    private int     mCinemaScene = 0;
    private int     mCinemaChar  = 0;
    private long    mCinemaLast  = 0;
    private boolean mCinemaDone  = false;
    private boolean mSkipHeld    = false;
    private long    mSkipStart   = 0;
    private static final long TYPEWRITER_MS = 55;
    private static final long SKIP_HOLD_MS  = 3000;

    // Menu / Settings
    private int mMenuHover = -1, mMenuPress = -1;
    private int mSetHover  = -1, mSetPress  = -1;

    // Settings değerleri
    private boolean mSoundOn = true;
    private int     mFps     = 60;

    // Touch
    private float mTouchX, mTouchY;
    private int   mTouchAction = -1;

    // ─── CONTEXT ───────────────────────────────────────────────────────────
    private final Context      mCtx;
    private final AssetManager mAssets;

    public GameRenderer(Context ctx) {
        mCtx    = ctx;
        mAssets = ctx.getAssets();
        for (int i = 0; i < 6; i++) mTexCinema[i] = -1;
        ByteBuffer bb = ByteBuffer.allocateDirect(8 * 4);
        bb.order(ByteOrder.nativeOrder());
        mVtxBuf = bb.asFloatBuffer();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GL CALLBACKS
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        mProgCol   = buildProgram(VERT_SRC, FRAG_SRC);
        mLocAPos   = GLES20.glGetAttribLocation (mProgCol,"aPos");
        mLocURes   = GLES20.glGetUniformLocation(mProgCol,"uRes");
        mLocUColor = GLES20.glGetUniformLocation(mProgCol,"uColor");

        mProgTex    = buildProgram(VERT_TEX_SRC, FRAG_TEX_SRC);
        mLocTAPos   = GLES20.glGetAttribLocation (mProgTex,"aPos");
        mLocTAUV    = GLES20.glGetAttribLocation (mProgTex,"aUV");
        mLocTURes   = GLES20.glGetUniformLocation(mProgTex,"uRes");
        mLocTUTex   = GLES20.glGetUniformLocation(mProgTex,"uTex");
        mLocTUAlpha = GLES20.glGetUniformLocation(mProgTex,"uAlpha");

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mTexLogo   = loadTexture("logo.png");
        mTexMenuBg = loadTexture("menu_bg.png");
        for (int i = 0; i < 6; i++) mTexCinema[i] = loadTexture(CINEMA_ASSETS[i]);

        mCinemaLast = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        mW = w; mH = h;
        GLES20.glViewport(0, 0, w, h);

        // Güvenli padding: %5 yatay boşluk
        mPad     = mW * 0.05f;
        mUsableW = mW - mPad * 2f;

        // Font boyutu: ekran genişliğine orantılı
        // mW/144 → 360px ekranda gPS≈2.5, 720px'de ≈5.0
        gPS = mW / 144f;
        gPS = Math.max(1.8f, Math.min(gPS, 5.5f));
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        mFrame++;
        update();
        render();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOUCH
    // ══════════════════════════════════════════════════════════════════════

    public void onTouch(int action, float x, float y, long time) {
        mTouchAction = action;
        mTouchX = x; mTouchY = y;
        switch (mState) {
            case ST_SPLASH:   touchSplash(action);             break;
            case ST_CINEMA:   touchCinema(action, x, y, time); break;
            case ST_MENU:     touchMenu(action, x, y);         break;
            case ST_SETTINGS: touchSettings(action, x, y);     break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ══════════════════════════════════════════════════════════════════════

    private void update() {
        switch (mState) {
            case ST_SPLASH: updateSplash(); break;
            case ST_CINEMA: updateCinema(); break;
        }
    }

    private void updateSplash() {
        if      (mFrame < 30)                  mSplashAlpha = mFrame / 30f;
        else if (mFrame > SPLASH_FRAMES - 30)  mSplashAlpha = (SPLASH_FRAMES - mFrame) / 30f;
        else                                   mSplashAlpha = 1f;

        if (mFrame >= SPLASH_FRAMES) {
            mFrame = 0;
            // ← DÜZELTME: Splash bitti → MENÜ'ye geç (sinematik değil)
            mState     = ST_MENU;
            mMenuHover = mMenuPress = -1;
        }
    }

    private void updateCinema() {
        if (mCinemaDone) return;
        String text = CINEMA_TEXT[mCinemaScene];
        long now = System.currentTimeMillis();
        if (now - mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast = now;
            if (mCinemaChar < text.length()) mCinemaChar++;
            else mCinemaDone = true;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER ANA METODU
    // ══════════════════════════════════════════════════════════════════════

    private void render() {
        GLES20.glClearColor(C_BG[0], C_BG[1], C_BG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        switch (mState) {
            case ST_SPLASH:   renderSplash();   break;
            case ST_MENU:     renderMenu();     break;
            case ST_CINEMA:   renderCinema();   break;
            case ST_SETTINGS: renderSettings(); break;
            case ST_GAME:     renderGame();     break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SPLASH
    // ══════════════════════════════════════════════════════════════════════

    private void renderSplash() {
        float cx = mW / 2f, cy = mH / 2f;
        float a  = mSplashAlpha;

        if (mTexLogo > 0) {
            float lw = mUsableW * 0.55f;
            drawTex(mTexLogo, cx - lw/2f, cy - lw/2f - mH*0.06f, lw, lw, a);
        }

        // BoneCastOfficial — genişliğe sığacak şekilde otomatik küçülür
        float ps = fitTextPS("BONECASTOFFICIAL", mUsableW * 0.88f, gPS);
        float[] col = {C_GOLD[0], C_GOLD[1], C_GOLD[2], a};
        drawStringC("BONECASTOFFICIAL", cy + mH * 0.09f, ps, col);

        // Progress bar
        float prog = Math.min(1f, mFrame / (float) SPLASH_FRAMES);
        float bw = mUsableW * 0.50f, bh = gPS * 1.5f;
        float bx = cx - bw/2f, by = mH * 0.86f;
        float[] dimC  = {C_DARK[0], C_DARK[1], C_DARK[2], a};
        float[] fillC = {C_GOLD[0], C_GOLD[1], C_GOLD[2], a};
        rect(bx, by, bw, bh, dimC);
        if (prog > 0) rect(bx, by, bw * prog, bh, fillC);

        scanLines();
    }

    private void touchSplash(int action) {
        if (action == MotionEvent.ACTION_DOWN) mFrame = SPLASH_FRAMES;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANA MENÜ
    // ══════════════════════════════════════════════════════════════════════

    private static final String[] MENU_LABELS = { "YENİ OYUN","DEVAM ET","AYARLAR","CIKIS" };

    private void renderMenu() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.72f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.50f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;

        // Başlık — usableW'nin %80'ine sığacak kadar büyük
        float titlePS = fitTextPS("MUHUR", mUsableW * 0.80f, gPS * 2.0f);
        drawStringC("MUHUR", mH * 0.16f, titlePS, C_GOLD);

        // Motto — word-wrap aktif
        drawStringCWrapped("KADERIN, MUHRURUN UCUNDA.",
                mH * 0.29f, gPS * 0.68f, C_GDIM);

        // Geliştirici
        float devPS = fitTextPS("BONECASTOFFICIAL", mUsableW * 0.68f, gPS * 0.58f);
        drawStringC("BONECASTOFFICIAL", mH * 0.36f, devPS, C_GREY);

        // Butonlar
        float bw    = mUsableW * 0.72f;
        float bh    = Math.max(gPS * 8.5f, mH * 0.065f);
        float bx    = cx - bw / 2f;
        float gap   = bh + gPS * 2.5f;
        float startY= mH * 0.46f;

        for (int i = 0; i < MENU_LABELS.length; i++) {
            boolean disabled = (i == 1); // Devam Et pasif
            drawButton(MENU_LABELS[i], bx, startY + i * gap, bw, bh,
                    mMenuHover == i, mMenuPress == i, disabled);
        }

        // Versiyon
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.45f};
        drawString("v0.1.0", mPad, mH - gPS * 5f, gPS * 0.62f, greyA);

        scanLines();
    }

    private void touchMenu(int action, float x, float y) {
        float cx    = mW / 2f;
        float bw    = mUsableW * 0.72f;
        float bh    = Math.max(gPS * 8.5f, mH * 0.065f);
        float bx    = cx - bw / 2f;
        float gap   = bh + gPS * 2.5f;
        float startY= mH * 0.46f;

        if (action == MotionEvent.ACTION_DOWN) {
            mMenuPress = -1;
            for (int i = 0; i < MENU_LABELS.length; i++) {
                if (hit(x, y, bx, startY + i * gap, bw, bh)) {
                    mMenuPress = i; mMenuHover = i; return;
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            mMenuHover = -1;
            for (int i = 0; i < MENU_LABELS.length; i++) {
                if (hit(x, y, bx, startY + i * gap, bw, bh)) { mMenuHover = i; break; }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            int p = mMenuPress; mMenuPress = -1; mMenuHover = -1;
            if (p < 0) return;
            if (!hit(x, y, bx, startY + p * gap, bw, bh)) return;
            switch (p) {
                case 0: // ← YENİ OYUN: sinematik başlat
                    startCinema();
                    break;
                case 1: break; // Devam Et pasif
                case 2:
                    mSetHover = mSetPress = -1;
                    mState = ST_SETTINGS;
                    break;
                case 3:
                    if (mCtx instanceof android.app.Activity)
                        ((android.app.Activity) mCtx).finish();
                    break;
            }
        }
    }

    /** Sinematik başlatma — state değişkenleri sıfırlanır */
    private void startCinema() {
        mCinemaScene = 0;
        mCinemaChar  = 0;
        mCinemaDone  = false;
        mCinemaLast  = System.currentTimeMillis();
        mSkipHeld    = false;
        mState       = ST_CINEMA;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SİNEMATİK
    // ══════════════════════════════════════════════════════════════════════

    private void renderCinema() {
        int bg = mTexCinema[mCinemaScene];
        if (bg > 0) {
            drawTex(bg, 0, 0, mW, mH, 0.82f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.52f};
            rect(0, 0, mW, mH, ov);
        }

        // Sahne numarası — sağ üst köşe, padding içinde
        float snPS = gPS * 0.72f;
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.75f};
        String scn = (mCinemaScene + 1) + "/" + CINEMA_TEXT.length;
        drawString(scn, mW - mPad - charWidth(scn, snPS), mPad * 1.5f, snPS, greyA);

        // ── Daktilo metni — word-wrapped, otomatik ölçekleme ─────────────
        String fullText = CINEMA_TEXT[mCinemaScene];
        String visible  = fullText.substring(0, Math.min(mCinemaChar, fullText.length()));

        float textPS   = gPS * 0.90f;
        float maxLineW = mUsableW * 0.86f;

        List<String> lines = wrapText(visible, maxLineW, textPS);

        float lineH  = textPS * 11f;
        float totalH = lines.size() * lineH;
        // Alt %45'e ortalı (üst kısmı arka plan görseli için bırak)
        float textY  = mH * 0.52f - totalH / 2f;

        for (int i = 0; i < lines.size(); i++) {
            drawStringC(lines.get(i), textY + i * lineH, textPS, C_GOLD);
        }

        // Yanıp sönen imleç
        if (!mCinemaDone && (mFrame / 15) % 2 == 0) {
            String lastLine = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
            float curX = mW/2f + charWidth(lastLine, textPS)/2f + textPS;
            float curY = textY + (lines.size() - 1) * lineH;
            rect(curX, curY, textPS, textPS * 8f, C_GOLD);
        }

        // DEVAM butonu (metin bittiyse)
        if (mCinemaDone) {
            float bw = mUsableW * 0.42f;
            float bh = Math.max(gPS * 9f, mH * 0.06f);
            float bx = mW/2f - bw/2f, by = mH * 0.82f;
            drawButton("DEVAM", bx, by, bw, bh, mSetHover == 99, false, false);
        }

        // ATLA butonu (sol alt)
        float sw = mUsableW * 0.30f;
        float sh = Math.max(gPS * 7f, mH * 0.05f);
        float sx = mPad, sy = mH - sh - mPad * 2f;
        drawButton("ATLA", sx, sy, sw, sh, false, mSkipHeld, false);

        // ATLA basılı tutma çubuğu
        if (mSkipHeld) {
            long held = System.currentTimeMillis() - mSkipStart;
            float prog = Math.min(1f, held / (float) SKIP_HOLD_MS);
            rect(sx, sy + sh + gPS * 0.5f, sw * prog, gPS * 1.2f, C_GOLD);
            if (held >= SKIP_HOLD_MS) skipAllCinema();
        }

        scanLines();
    }

    private void touchCinema(int action, float x, float y, long time) {
        float bw = mUsableW * 0.42f;
        float bh = Math.max(gPS * 9f, mH * 0.06f);
        float bx = mW/2f - bw/2f, by = mH * 0.82f;
        float sw = mUsableW * 0.30f;
        float sh = Math.max(gPS * 7f, mH * 0.05f);
        float sx = mPad, sy = mH - sh - mPad * 2f;

        if (action == MotionEvent.ACTION_DOWN) {
            if (hit(x, y, sx, sy, sw, sh)) {
                mSkipHeld = true; mSkipStart = System.currentTimeMillis();
                return;
            }
            if (!mCinemaDone) {
                // Herhangi bir yere basınca metni tamamla
                mCinemaChar = CINEMA_TEXT[mCinemaScene].length();
                mCinemaDone = true;
                return;
            }
            if (hit(x, y, bx, by, bw, bh)) nextCinemaScene();

        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mSkipHeld = false;
            mSetHover = -1;
        }
    }

    private void nextCinemaScene() {
        mCinemaScene++;
        if (mCinemaScene >= CINEMA_TEXT.length) {
            mState = ST_GAME; // Tüm sahneler bitti → oyun
        } else {
            mCinemaChar = 0;
            mCinemaDone = false;
            mCinemaLast = System.currentTimeMillis();
        }
    }

    private void skipAllCinema() {
        mSkipHeld = false;
        mState    = ST_GAME;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AYARLAR
    // ══════════════════════════════════════════════════════════════════════

    private void renderSettings() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.5f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.65f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;
        float titlePS = fitTextPS("AYARLAR", mUsableW * 0.60f, gPS * 1.6f);
        drawStringC("AYARLAR", mH * 0.14f, titlePS, C_GOLD);

        float togW  = mUsableW * 0.28f;
        float togH  = Math.max(gPS * 9f, mH * 0.060f);
        float togX0 = cx - togW - mPad;
        float togX1 = cx + mPad;
        float row1Y = mH * 0.36f;
        float row2Y = mH * 0.52f;
        float labPS = gPS * 0.72f;

        drawString("SES:",  mPad, row1Y, labPS, C_GOLD);
        drawButton("ACIK",   togX0, row1Y, togW, togH, mSetHover==0, mSetPress==0, !mSoundOn);
        drawButton("KAPALI", togX1, row1Y, togW, togH, mSetHover==1, mSetPress==1,  mSoundOn);

        drawString("FPS:", mPad, row2Y, labPS, C_GOLD);
        drawButton("30", togX0, row2Y, togW, togH, mSetHover==2, mSetPress==2, mFps!=30);
        drawButton("60", togX1, row2Y, togW, togH, mSetHover==3, mSetPress==3, mFps!=60);

        float backW = mUsableW * 0.45f;
        float backH = Math.max(gPS * 9f, mH * 0.060f);
        drawButton("GERI", cx - backW/2f, mH * 0.76f, backW, backH,
                mSetHover==10, mSetPress==10, false);

        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx    = mW / 2f;
        float togW  = mUsableW * 0.28f;
        float togH  = Math.max(gPS * 9f, mH * 0.060f);
        float togX0 = cx - togW - mPad;
        float togX1 = cx + mPad;
        float row1Y = mH * 0.36f;
        float row2Y = mH * 0.52f;
        float backW = mUsableW * 0.45f;
        float backH = Math.max(gPS * 9f, mH * 0.060f);
        float backX = cx - backW/2f, backY = mH * 0.76f;

        if (action == MotionEvent.ACTION_DOWN) {
            mSetPress = -1;
            if      (hit(x,y,togX0,row1Y,togW,togH)) mSetPress=0;
            else if (hit(x,y,togX1,row1Y,togW,togH)) mSetPress=1;
            else if (hit(x,y,togX0,row2Y,togW,togH)) mSetPress=2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetPress=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetPress=10;
            mSetHover = mSetPress;
        } else if (action == MotionEvent.ACTION_UP) {
            int p = mSetPress; mSetPress = -1; mSetHover = -1;
            switch (p) {
                case 0:  mSoundOn=true;  break;
                case 1:  mSoundOn=false; break;
                case 2:  mFps=30;        break;
                case 3:  mFps=60;        break;
                case 10: if (hit(x,y,backX,backY,backW,backH)) mState=ST_MENU; break;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mSetPress = -1; mSetHover = -1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN (ilerleyen sürümde dolacak)
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        float ps = gPS * 1.1f;
        drawStringC("OYUN GELIYOR...", mH * 0.44f, ps, C_GOLD);
        drawStringCWrapped("(Beklenti: Kart Sistemi)", mH * 0.56f, gPS * 0.75f, C_GDIM);

        float bw = mUsableW * 0.50f;
        float bh = Math.max(gPS * 9f, mH * 0.060f);
        float bx = mW/2f - bw/2f, by = mH * 0.74f;
        drawButton("MENUYE DON", bx, by, bw, bh, false, false, false);

        if (mTouchAction == MotionEvent.ACTION_UP && hit(mTouchX, mTouchY, bx, by, bw, bh)) {
            mState = ST_MENU; mTouchAction = -1;
        }
        scanLines();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ÇİZİM YÖNTEMLERİ
    // ══════════════════════════════════════════════════════════════════════

    private void rect(float x, float y, float w, float h, float[] c) {
        GLES20.glUseProgram(mProgCol);
        GLES20.glUniform2f(mLocURes, mW, mH);
        GLES20.glUniform4f(mLocUColor, c[0], c[1], c[2], c[3]);
        mVtxBuf.position(0);
        mVtxBuf.put(x  ); mVtxBuf.put(y  );
        mVtxBuf.put(x+w); mVtxBuf.put(y  );
        mVtxBuf.put(x  ); mVtxBuf.put(y+h);
        mVtxBuf.put(x+w); mVtxBuf.put(y+h);
        mVtxBuf.position(0);
        GLES20.glEnableVertexAttribArray(mLocAPos);
        GLES20.glVertexAttribPointer(mLocAPos, 2, GLES20.GL_FLOAT, false, 0, mVtxBuf);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mLocAPos);
    }

    private void drawTex(int id, float x, float y, float w, float h, float alpha) {
        if (id <= 0) return;
        GLES20.glUseProgram(mProgTex);
        GLES20.glUniform2f(mLocTURes, mW, mH);
        GLES20.glUniform1i(mLocTUTex, 0);
        GLES20.glUniform1f(mLocTUAlpha, alpha);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        ByteBuffer bb = ByteBuffer.allocateDirect(16 * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(x  ); fb.put(y  ); fb.put(0f); fb.put(0f);
        fb.put(x+w); fb.put(y  ); fb.put(1f); fb.put(0f);
        fb.put(x  ); fb.put(y+h); fb.put(0f); fb.put(1f);
        fb.put(x+w); fb.put(y+h); fb.put(1f); fb.put(1f);
        fb.position(0);
        int stride = 4 * 4;
        GLES20.glEnableVertexAttribArray(mLocTAPos);
        GLES20.glVertexAttribPointer(mLocTAPos, 2, GLES20.GL_FLOAT, false, stride, fb);
        fb.position(2);
        GLES20.glEnableVertexAttribArray(mLocTAUV);
        GLES20.glVertexAttribPointer(mLocTAUV, 2, GLES20.GL_FLOAT, false, stride, fb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mLocTAPos);
        GLES20.glDisableVertexAttribArray(mLocTAUV);
    }

    /** CRT tarama çizgisi efekti */
    private void scanLines() {
        float step = Math.max(2f, mH / 400f) * 3f;
        for (float y = 0; y < mH; y += step) {
            rect(0, y, mW, Math.max(1f, step * 0.35f), C_SCAN);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PİXEL FONT  (Segment tabanlı — rect() çağrılarıyla)
    // ══════════════════════════════════════════════════════════════════════

    /** Bir stringin piksel cinsinden toplam genişliği */
    private float charWidth(String s, float ps) {
        return s.length() * ps * 7f;
    }

    /**
     * Verilen metin için maxW'ye sığacak en büyük ps'yi döndürür.
     * maxPS'in üstüne çıkmaz.
     */
    private float fitTextPS(String text, float maxW, float maxPS) {
        float needed = charWidth(text, maxPS);
        if (needed <= maxW) return maxPS;
        return maxPS * (maxW / needed);
    }

    /**
     * Word-wrap: metni maxLineW pikselini geçmeyecek şekilde satırlara böl.
     * \n karakterine de saygı gösterir.
     */
    private List<String> wrapText(String text, float maxLineW, float ps) {
        List<String> result = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { result.add(""); continue; }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String test = (line.length() == 0) ? word : line + " " + word;
                if (charWidth(test, ps) <= maxLineW) {
                    line = new StringBuilder(test);
                } else {
                    if (line.length() > 0) result.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            if (line.length() > 0) result.add(line.toString());
        }
        return result;
    }

    private void drawString(String s, float x, float y, float ps, float[] c) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            drawChar(Character.toUpperCase(s.charAt(i)), cx, y, ps, c);
            cx += ps * 7f;
        }
    }

    private void drawStringC(String s, float y, float ps, float[] c) {
        float sx = mW / 2f - charWidth(s, ps) / 2f;
        drawString(s, sx, y, ps, c);
    }

    /** Ortalanmış + word-wrap */
    private void drawStringCWrapped(String s, float startY, float ps, float[] c) {
        List<String> lines = wrapText(s, mUsableW * 0.88f, ps);
        float lineH = ps * 10f;
        for (int i = 0; i < lines.size(); i++) {
            drawStringC(lines.get(i), startY + i * lineH, ps, c);
        }
    }

    private void drawChar(char ch, float x, float y, float ps, float[] c) {
        switch (ch) {
            case 'A': fH(x,y,ps,1,c);fH(x,y,ps,4,c);fV(x,y,ps,0,0,3,c);fV(x,y,ps,4,0,3,c);fV(x,y,ps,0,4,6,c);fV(x,y,ps,4,4,6,c);break;
            case 'B': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,2,c);fV(x,y,ps,4,3,5,c);break;
            case 'C': fH(x,y,ps,0,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);break;
            case 'D': fH(x,y,ps,0,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,1,5,c);fV(x,y,ps,3,0,0,c);fV(x,y,ps,3,6,6,c);break;
            case 'E': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);break;
            case 'F': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fV(x,y,ps,0,0,6,c);break;
            case 'G': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,3,6,c);break;
            case 'H': fH(x,y,ps,3,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);break;
            case 'I': fH(x,y,ps,0,c);fH(x,y,ps,6,c);fV(x,y,ps,2,0,6,c);break;
            case 'J': fH(x,y,ps,0,c);fH(x,y,ps,5,c);fV(x,y,ps,4,0,6,c);fV(x,y,ps,0,5,6,c);break;
            case 'K': fV(x,y,ps,0,0,6,c);fH(x,y,ps,3,c);fV(x,y,ps,4,0,2,c);fV(x,y,ps,4,4,6,c);break;
            case 'L': fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);break;
            case 'M': fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);fV(x,y,ps,1,0,2,c);fV(x,y,ps,3,0,2,c);fV(x,y,ps,2,1,2,c);break;
            case 'N': fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);fV(x,y,ps,1,0,1,c);fV(x,y,ps,2,1,3,c);fV(x,y,ps,3,3,5,c);break;
            case 'O': fH(x,y,ps,0,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);break;
            case 'P': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,3,c);break;
            case 'Q': fH(x,y,ps,0,c);fH(x,y,ps,5,c);fV(x,y,ps,0,0,5,c);fV(x,y,ps,4,0,5,c);fV(x,y,ps,3,4,5,c);fV(x,y,ps,4,5,6,c);break;
            case 'R': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,3,c);fV(x,y,ps,3,3,4,c);fV(x,y,ps,4,4,6,c);break;
            case 'S': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,3,c);fV(x,y,ps,4,3,6,c);break;
            case 'T': fH(x,y,ps,0,c);fV(x,y,ps,2,0,6,c);break;
            case 'U': fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);break;
            case 'V': fV(x,y,ps,0,0,4,c);fV(x,y,ps,4,0,4,c);fV(x,y,ps,1,4,5,c);fV(x,y,ps,3,4,5,c);fV(x,y,ps,2,5,6,c);break;
            case 'W': fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);fV(x,y,ps,1,4,6,c);fV(x,y,ps,3,4,6,c);fV(x,y,ps,2,5,6,c);break;
            case 'X': fV(x,y,ps,0,0,2,c);fV(x,y,ps,4,0,2,c);fV(x,y,ps,2,2,4,c);fV(x,y,ps,0,4,6,c);fV(x,y,ps,4,4,6,c);fV(x,y,ps,1,2,3,c);fV(x,y,ps,3,3,4,c);break;
            case 'Y': fV(x,y,ps,0,0,2,c);fV(x,y,ps,4,0,2,c);fV(x,y,ps,2,2,6,c);break;
            case 'Z': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,4,0,3,c);fV(x,y,ps,0,3,6,c);break;
            case '0': fH(x,y,ps,0,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);fV(x,y,ps,1,1,2,c);fV(x,y,ps,3,3,4,c);break;
            case '1': fV(x,y,ps,2,0,6,c);fH(x,y,ps,6,c);fV(x,y,ps,1,0,1,c);break;
            case '2': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,4,0,3,c);fV(x,y,ps,0,3,6,c);break;
            case '3': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,4,0,6,c);break;
            case '4': fH(x,y,ps,3,c);fV(x,y,ps,0,0,3,c);fV(x,y,ps,4,0,6,c);break;
            case '5': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,3,c);fV(x,y,ps,4,3,6,c);break;
            case '6': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,3,6,c);break;
            case '7': fH(x,y,ps,0,c);fV(x,y,ps,4,0,6,c);break;
            case '8': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,6,c);fV(x,y,ps,4,0,6,c);break;
            case '9': fH(x,y,ps,0,c);fH(x,y,ps,3,c);fH(x,y,ps,6,c);fV(x,y,ps,0,0,3,c);fV(x,y,ps,4,0,6,c);break;
            case '.': fD(x,y,ps,2,6,c);break;
            case ',': fD(x,y,ps,2,6,c);fD(x,y,ps,1,7,c);break;
            case ':': fD(x,y,ps,2,2,c);fD(x,y,ps,2,4,c);break;
            case '!': fV(x,y,ps,2,0,4,c);fD(x,y,ps,2,6,c);break;
            case '?': fH(x,y,ps,0,c);fV(x,y,ps,4,0,2,c);fV(x,y,ps,2,2,3,c);fV(x,y,ps,3,2,3,c);fD(x,y,ps,2,6,c);break;
            case '/': fV(x,y,ps,4,0,2,c);fV(x,y,ps,3,2,4,c);fV(x,y,ps,2,4,5,c);fV(x,y,ps,1,5,6,c);break;
            case '-': fH(x,y,ps,3,c);break;
            case '+': fH(x,y,ps,3,c);fV(x,y,ps,2,1,5,c);break;
            case '(': fH(x,y,ps,1,c);fH(x,y,ps,5,c);fV(x,y,ps,0,1,5,c);break;
            case ')': fH(x,y,ps,1,c);fH(x,y,ps,5,c);fV(x,y,ps,4,1,5,c);break;
            case ' ': break;
            default:  fD(x,y,ps,2,3,c);break;
        }
    }

    private void fH(float x,float y,float ps,int row,float[] c){ rect(x,y+row*ps,5f*ps,ps,c); }
    private void fV(float x,float y,float ps,int col,int r0,int r1,float[] c){ rect(x+col*ps,y+r0*ps,ps,(r1-r0+1)*ps,c); }
    private void fD(float x,float y,float ps,int col,int row,float[] c){ rect(x+col*ps,y+row*ps,ps,ps,c); }

    // ══════════════════════════════════════════════════════════════════════
    //  BUTON
    // ══════════════════════════════════════════════════════════════════════

    private void drawButton(String label, float x, float y, float w, float h,
                            boolean hover, boolean pressed, boolean disabled) {
        float[] bg  = disabled ? C_DARK : (pressed ? C_PRES : C_DARK);
        float[] brd = disabled ? C_GREY : (pressed || hover ? C_GOLD : C_GDIM);
        float[] txt = disabled ? C_GREY : (pressed || hover ? C_GOLD : C_GDIM);

        rect(x, y, w, h, bg);
        float b = Math.max(1.5f, gPS * 0.4f);
        rect(x,     y,     w, b,   brd);
        rect(x,     y+h-b, w, b,   brd);
        rect(x,     y,     b, h,   brd);
        rect(x+w-b, y,     b, h,   brd);

        // Metni butona sığacak şekilde ölçekle
        float ps = fitTextPS(label, w * 0.78f, gPS * 0.80f);
        float tw = charWidth(label, ps);
        drawString(label, x + w/2f - tw/2f, y + h/2f - 3.5f*ps, ps, txt);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TEXTURE YÜKLEME
    // ══════════════════════════════════════════════════════════════════════

    private int loadTexture(String name) {
        try {
            InputStream is = mAssets.open(name);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return -1;
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            bmp.recycle();
            return ids[0];
        } catch (IOException e) { return -1; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHADER DERLEME
    // ══════════════════════════════════════════════════════════════════════

    private int buildProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER,   vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f);
        GLES20.glLinkProgram(p);
        int[] s = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, s, 0);
        if (s[0]==0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f);
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s,src); GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0]==0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
        return s;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YARDIMCI
    // ══════════════════════════════════════════════════════════════════════

    private boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px>=rx && px<=rx+rw && py>=ry && py<=ry+rh;
    }
}

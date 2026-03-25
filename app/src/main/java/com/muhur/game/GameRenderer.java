package com.muhur.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * MUHUR — GameRenderer  (Revizyon 5)
 *
 * FONT SiSTEMi TAMAMEN YENiLENDi:
 *   - Elle cizilen pixel font (drawChar, pH, pV, pD...) KALDIRILDI
 *   - game_font.ttf  Typeface.createFromAsset() ile yukleniyor
 *   - Pipeline: Paint.drawText() -> Bitmap -> GL Texture -> quad
 *   - Turkce karakter destegi fonttan geliyor
 *   - Font yuklenemezse ekran KIRMIZI -> hata gorunur
 *   - LRU texture onbellegi (60 slot)
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ══════════════════════════════════════════════════════════════════════
    //  MUSICController INTERFACE
    // ══════════════════════════════════════════════════════════════════════

    public interface MusicController {
        void onMusicStart();
        void onVolumeChanged(float volume);
        float getVolume();
    }

    // ─── STATE SABiTLERi ────────────────────────────────────────────────────
    private static final int ST_SPLASH   = 0;
    private static final int ST_MENU     = 1;
    private static final int ST_CINEMA   = 2;
    private static final int ST_SETTINGS = 3;
    private static final int ST_GAME     = 4;

    // ─── RENK PALETi ────────────────────────────────────────────────────────
    private static final float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f};
    private static final float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f};
    private static final float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f};
    private static final float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f};
    private static final float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f};
    private static final float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f};
    private static final float[] C_SCAN = {0.000f, 0.000f, 0.000f, 0.15f};
    private static final float[] C_RED  = {1.000f, 0.000f, 0.000f, 1.0f};

    // ─── SiNEMATiK VERi ─────────────────────────────────────────────────────
    private static final String[] CINEMA_TEXT = {
        "YIL 1999. PARALEL TURKIYE.\nBIR DARBE OLDU.\nAMA KIMSE KONUSMUYOR.",
        "YENI BIR DUZEN ILAN EDILDI.\nKURALLAR DEGISTI.\nSENIN KURALLARIN DA.",
        "BUROKRATIN MASASINDA\nBIR MUHUR VAR.\nO MUHUR SENIN.",
        "HALK SORMUYOR.\nHALK BEKLIYOR.\nSEN KARAR VERECEKSIN.",
        "GOLGEDEKI SESLER BUYUYOR.\nMUHALEFET ORGUTLUYOR.\nYA SEN?",
        "ISTE O MUHUR.\nKADERIN, MUHUR UCUNDA."
    };
    private static final String[] CINEMA_ASSETS = {
        "story_bg_1.png","story_bg_2.png","story_bg_3.png",
        "story_bg_4.png","story_bg_5.png","story_bg_6.png"
    };

    // ─── SHADER KAYNAK KODLARI ───────────────────────────────────────────────
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

    // uColor.a > 0  -> metin modu: texture alpha * uColor.rgb
    // uColor.a == 0 -> PNG modu:   orijinal texture rengi
    private static final String FRAG_TEX_SRC =
        "precision mediump float;" +
        "uniform sampler2D uTex;" +
        "uniform float uAlpha;" +
        "uniform vec4 uColor;" +
        "varying vec2 vUV;\n" +
        "void main(){\n" +
        "  vec4 t=texture2D(uTex,vUV);\n" +
        "  if(uColor.a>0.0){\n" +
        "    gl_FragColor=vec4(uColor.rgb,t.a*uAlpha*uColor.a);\n" +
        "  } else {\n" +
        "    gl_FragColor=vec4(t.rgb,t.a*uAlpha);\n" +
        "  }\n" +
        "}\n";

    // ─── GL HANDLES ─────────────────────────────────────────────────────────
    private int mProgCol, mLocAPos, mLocURes, mLocUColor;
    private int mProgTex, mLocTAPos, mLocTAUV, mLocTURes, mLocTUTex, mLocTUAlpha, mLocTUColor;
    private FloatBuffer mVtxBuf;

    // ─── TEXTURE ID'LERi ────────────────────────────────────────────────────
    private int    mTexLogo   = -1;
    private int    mTexMenuBg = -1;
    private int[]  mTexCinema = new int[6];

    // ─── FONT ───────────────────────────────────────────────────────────────
    private Typeface mTypeface   = null;
    private boolean  mFontLoaded = false;
    private Paint    mTextPaint  = null;

    // LRU metin texture onbellegi
    private final LinkedHashMap<String, int[]> mTexCache =
        new LinkedHashMap<String, int[]>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
                if (size() > 60) {
                    GLES20.glDeleteTextures(1, eldest.getValue(), 0);
                    return true;
                }
                return false;
            }
        };

    // ─── EKRAN & GUVENLI ALAN ───────────────────────────────────────────────
    private float mW, mH, mPad, mUsableW;
    private float mFontSize;   // temel font boyutu (px)

    // ─── OYUN DURUMU ────────────────────────────────────────────────────────
    private int   mState = ST_SPLASH;
    private int   mFrame = 0;

    // Splash
    private float mSplashAlpha   = 0f;
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

    // Menu
    private int mMenuHover = -1, mMenuPress = -1;

    // Settings
    private int mSetHover = -1, mSetPress = -1;

    // Ses slider
    private float   mVolume         = 0.75f;
    private boolean mSliderDragging = false;
    private int     mFps            = 60;
    private boolean mMusicStarted   = false;

    // Touch
    private float mTouchX, mTouchY;
    private int   mTouchAction = -1;

    // ─── OYUN EKRANi ────────────────────────────────────────────────────────
    private GameState mGameState  = new GameState();
    private float[] mBarCurrent   = {50f, 50f, 50f, 50f};
    private float[] mBarTarget    = {50f, 50f, 50f, 50f};

    // Swipe
    private float   mSwipeStartX  = 0f;
    private boolean mSwiping      = false;
    private float   mCardOffsetX  = 0f;
    private static final float SWIPE_THRESHOLD = 100f;

    // Kart veritabani
    private static final String[] CARD_TITLES = {
        "GREV TALEBI", "ASKERI BUTCE", "DINI VAKIF",
        "VERGI AFFI",  "BASIN YASAGI", "UNIVERSITE",
    };
    private static final String[] CARD_TEXTS = {
        "ISCILER 3 GUNLUK GREV\nICIN IZIN ISTIYOR.",
        "ORDU BUTCEDE\n%15 ARTIS TALEBI.",
        "YENI VAKIF KURULMASI\nICIN ONAY BEKLIYOR.",
        "10 YILLIK VERGI BORCLARI\nSILINSIN MI?",
        "MUHALEFET GAZETELERI\nKAPATILSIN MI?",
        "UNIVERSITE KONTENJANLAR\nARTIRILSIN MI?",
    };
    private static final int[][] CARD_RIGHT = {
        { 15, -5, -10,  5}, {-10, -5,  -5, 20},
        {  5, 20,  -5, -5}, { 10, -5,  15, -5},
        {-15, -5,   5,  5}, { 10,  5,  -5, -5},
    };
    private static final int[][] CARD_LEFT = {
        {-10,  5,  10, -5}, {  5,  5,   5,-15},
        { -5,-15,   5,  5}, { -5,  5, -10,  5},
        { 10,  5,  -5, -5}, {-10, -5,   5,  5},
    };
    private int mCardIndex   = 0;
    private int mKararSayisi = 0;
    private static final int YIL_BASLANGIC = 1999;

    // ─── CONTEXT & MUZIK ────────────────────────────────────────────────────
    private final Context         mCtx;
    private final AssetManager    mAssets;
    private final MusicController mMusic;

    // ══════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════

    public GameRenderer(Context ctx, MusicController music) {
        mCtx    = ctx;
        mAssets = ctx.getAssets();
        mMusic  = music;
        if (mMusic != null) mVolume = mMusic.getVolume();

        for (int i = 0; i < 6; i++) mTexCinema[i] = -1;
        ByteBuffer bb = ByteBuffer.allocateDirect(8 * 4);
        bb.order(ByteOrder.nativeOrder());
        mVtxBuf = bb.asFloatBuffer();

        // ── game_font.ttf yukle ─────────────────────────────────────────
        try {
            mTypeface   = Typeface.createFromAsset(mAssets, "game_font.ttf");
            mFontLoaded = (mTypeface != null);
        } catch (Exception e) {
            mTypeface   = null;
            mFontLoaded = false;
        }

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(false);      // piksel netligi icin KAPALI
        mTextPaint.setFilterBitmap(false);
        mTextPaint.setDither(false);
        mTextPaint.setStyle(Paint.Style.FILL);
        if (mFontLoaded) {
            mTextPaint.setTypeface(mTypeface);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GL CALLBACKS
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        mProgCol   = buildProgram(VERT_SRC, FRAG_SRC);
        mLocAPos   = GLES20.glGetAttribLocation (mProgCol, "aPos");
        mLocURes   = GLES20.glGetUniformLocation(mProgCol, "uRes");
        mLocUColor = GLES20.glGetUniformLocation(mProgCol, "uColor");

        mProgTex    = buildProgram(VERT_TEX_SRC, FRAG_TEX_SRC);
        mLocTAPos   = GLES20.glGetAttribLocation (mProgTex, "aPos");
        mLocTAUV    = GLES20.glGetAttribLocation (mProgTex, "aUV");
        mLocTURes   = GLES20.glGetUniformLocation(mProgTex, "uRes");
        mLocTUTex   = GLES20.glGetUniformLocation(mProgTex, "uTex");
        mLocTUAlpha = GLES20.glGetUniformLocation(mProgTex, "uAlpha");
        mLocTUColor = GLES20.glGetUniformLocation(mProgTex, "uColor");

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mTexLogo   = loadImageTexture("logo.png");
        mTexMenuBg = loadImageTexture("menu_bg.png");
        for (int i = 0; i < 6; i++) mTexCinema[i] = loadImageTexture(CINEMA_ASSETS[i]);

        mCinemaLast = System.currentTimeMillis();
        mGameState.sifirla();
        syncBarsFromState();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        mW = w; mH = h;
        GLES20.glViewport(0, 0, w, h);
        mPad      = mW * 0.05f;
        mUsableW  = mW - mPad * 2f;
        mFontSize = Math.max(12f, Math.min(mW / 20f, 42f));

        // Yuzey boyutu degisince metin onbellegini temizle
        for (int[] id : mTexCache.values()) GLES20.glDeleteTextures(1, id, 0);
        mTexCache.clear();
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
            case ST_GAME:     touchGame(action, x, y);         break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ══════════════════════════════════════════════════════════════════════

    private void update() {
        if (mState == ST_SPLASH) updateSplash();
        if (mState == ST_CINEMA) updateCinema();
    }

    private void updateSplash() {
        if      (mFrame < 30)                 mSplashAlpha = mFrame / 30f;
        else if (mFrame > SPLASH_FRAMES - 30) mSplashAlpha = (SPLASH_FRAMES - mFrame) / 30f;
        else                                  mSplashAlpha = 1f;
        if (mFrame >= SPLASH_FRAMES) {
            mFrame = 0; mState = ST_MENU;
            mMenuHover = mMenuPress = -1;
            triggerMusicStart();
        }
    }

    private void updateCinema() {
        if (mCinemaDone) return;
        long now = System.currentTimeMillis();
        if (now - mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast = now;
            String text = CINEMA_TEXT[mCinemaScene];
            if (mCinemaChar < text.length()) mCinemaChar++;
            else mCinemaDone = true;
        }
    }

    private void triggerMusicStart() {
        if (!mMusicStarted && mMusic != null) {
            mMusicStarted = true;
            mMusic.onMusicStart();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER ANA METODU
    // ══════════════════════════════════════════════════════════════════════

    private void render() {
        // Font yuklenemezse KIRMIZI ekran — asla siyah ekran
        if (!mFontLoaded) {
            GLES20.glClearColor(C_RED[0], C_RED[1], C_RED[2], 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            return;
        }

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
            drawTex(mTexLogo, cx - lw/2f, cy - lw/2f - mH*0.06f, lw, lw, a, null);
        }

        float[] col = {C_GOLD[0], C_GOLD[1], C_GOLD[2], a};
        drawTextC("BONECASTOFFICIAL", cy + mH * 0.09f, mFontSize * 0.80f, col);

        float prog = Math.min(1f, mFrame / (float) SPLASH_FRAMES);
        float bw   = mUsableW * 0.50f, bh = Math.max(4f, mFontSize * 0.3f);
        float bx   = cx - bw / 2f, by = mH * 0.86f;
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
    //  ANA MENU
    // ══════════════════════════════════════════════════════════════════════

    private static final String[] MENU_LABELS = {"YENI OYUN", "DEVAM ET", "AYARLAR", "CIKIS"};

    private void renderMenu() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.72f, null);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.50f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;
        drawTextC("MUHUR", mH * 0.15f, mFontSize * 2.0f, C_GOLD);
        drawTextC("KADERiN, MUHUR UCUNDA.", mH * 0.28f, mFontSize * 0.65f, C_GDIM);
        drawTextC("BONECASTOFFICIAL",       mH * 0.35f, mFontSize * 0.52f, C_GREY);

        float bw     = mUsableW * 0.72f;
        float bh     = Math.max(mFontSize * 2.8f, mH * 0.065f);
        float bx     = cx - bw / 2f;
        float gap    = bh + mFontSize * 0.6f;
        float startY = mH * 0.45f;

        for (int i = 0; i < MENU_LABELS.length; i++) {
            boolean disabled = (i == 1);
            drawButton(MENU_LABELS[i], bx, startY + i * gap, bw, bh,
                    mMenuHover == i, mMenuPress == i, disabled);
        }

        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.45f};
        drawText("v0.1.0", mPad, mH - mFontSize * 1.5f, mFontSize * 0.55f, greyA);
        scanLines();
    }

    private void touchMenu(int action, float x, float y) {
        float cx     = mW / 2f;
        float bw     = mUsableW * 0.72f;
        float bh     = Math.max(mFontSize * 2.8f, mH * 0.065f);
        float bx     = cx - bw / 2f;
        float gap    = bh + mFontSize * 0.6f;
        float startY = mH * 0.45f;

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
                case 0: startCinema(); break;
                case 1: break;
                case 2: mSetHover = mSetPress = -1; mState = ST_SETTINGS; break;
                case 3:
                    if (mCtx instanceof android.app.Activity)
                        ((android.app.Activity) mCtx).finish();
                    break;
            }
        }
    }

    private void startCinema() {
        mCinemaScene = 0; mCinemaChar = 0; mCinemaDone = false;
        mCinemaLast  = System.currentTimeMillis(); mSkipHeld = false;
        mState = ST_CINEMA;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SiNEMATiK
    // ══════════════════════════════════════════════════════════════════════

    private void renderCinema() {
        int bg = mTexCinema[mCinemaScene];
        if (bg > 0) {
            drawTex(bg, 0, 0, mW, mH, 0.82f, null);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.52f};
            rect(0, 0, mW, mH, ov);
        }

        float snSize  = mFontSize * 0.65f;
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.75f};
        String scn    = (mCinemaScene + 1) + "/" + CINEMA_TEXT.length;
        drawText(scn, mW - mPad - measureText(scn, snSize) - mPad,
                 mPad * 1.5f, snSize, greyA);

        String fullText = CINEMA_TEXT[mCinemaScene];
        String visible  = fullText.substring(0, Math.min(mCinemaChar, fullText.length()));
        float textSize  = mFontSize * 0.85f;
        List<String> lines = wrapText(visible, mUsableW * 0.86f, textSize);
        float lineH  = textSize * 2.2f;
        float totalH = lines.size() * lineH;
        float textY  = mH * 0.52f - totalH / 2f;
        for (int i = 0; i < lines.size(); i++) drawTextC(lines.get(i), textY + i*lineH, textSize, C_GOLD);

        // Imleç
        if (!mCinemaDone && (mFrame / 15) % 2 == 0) {
            String last = lines.isEmpty() ? "" : lines.get(lines.size()-1);
            float curX = mW/2f + measureText(last, textSize)/2f + textSize * 0.3f;
            float curY = textY + (lines.size()-1) * lineH;
            rect(curX, curY, textSize * 0.4f, textSize * 1.1f, C_GOLD);
        }

        if (mCinemaDone) {
            float bw = mUsableW * 0.42f, bh = Math.max(mFontSize * 2.5f, mH * 0.06f);
            drawButton("DEVAM", mW/2f-bw/2f, mH * 0.82f, bw, bh, mSetHover==99, false, false);
        }

        float sw = mUsableW * 0.30f, sh = Math.max(mFontSize * 2.2f, mH * 0.05f);
        float sx = mPad, sy = mH - sh - mPad * 2f;
        drawButton("ATLA", sx, sy, sw, sh, false, mSkipHeld, false);

        if (mSkipHeld) {
            long held = System.currentTimeMillis() - mSkipStart;
            float prog = Math.min(1f, held / (float) SKIP_HOLD_MS);
            rect(sx, sy + sh + 4f, sw * prog, Math.max(3f, mFontSize * 0.2f), C_GOLD);
            if (held >= SKIP_HOLD_MS) startGame();
        }
        scanLines();
    }

    private void touchCinema(int action, float x, float y, long time) {
        float bw = mUsableW * 0.42f, bh = Math.max(mFontSize * 2.5f, mH * 0.06f);
        float bx = mW/2f-bw/2f, by = mH * 0.82f;
        float sw = mUsableW * 0.30f, sh = Math.max(mFontSize * 2.2f, mH * 0.05f);
        float sx = mPad, sy = mH - sh - mPad * 2f;

        if (action == MotionEvent.ACTION_DOWN) {
            if (hit(x, y, sx, sy, sw, sh)) {
                mSkipHeld = true; mSkipStart = System.currentTimeMillis(); return;
            }
            if (!mCinemaDone) {
                mCinemaChar = CINEMA_TEXT[mCinemaScene].length();
                mCinemaDone = true; return;
            }
            if (hit(x, y, bx, by, bw, bh)) nextCinemaScene();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mSkipHeld = false; mSetHover = -1;
        }
    }

    private void nextCinemaScene() {
        mCinemaScene++;
        if (mCinemaScene >= CINEMA_TEXT.length) {
            startGame();
        } else {
            mCinemaChar = 0; mCinemaDone = false;
            mCinemaLast = System.currentTimeMillis();
        }
    }

    private void startGame() { restartGame(); mState = ST_GAME; }

    // ══════════════════════════════════════════════════════════════════════
    //  AYARLAR
    // ══════════════════════════════════════════════════════════════════════

    private float sliderX()      { return mW / 2f - sliderW() / 2f; }
    private float sliderW()      { return mUsableW * 0.85f; }
    private float sliderY()      { return mH * 0.42f; }
    private float sliderH()      { return Math.max(mFontSize * 0.5f, 6f); }
    private float thumbR()       { return Math.max(mFontSize * 0.75f, 10f); }
    private float volToThumbX()  { return sliderX() + mVolume * sliderW(); }

    private void renderSettings() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.5f, null);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.65f};
            rect(0, 0, mW, mH, ov);
        }
        float cx      = mW / 2f;
        float labSize = mFontSize * 0.70f;
        float labY    = mH * 0.34f;
        drawTextC("AYARLAR", mH * 0.14f, mFontSize * 1.5f, C_GOLD);
        drawText("SES SEViYESi:", mPad, labY, labSize, C_GOLD);
        String pctStr = "%" + Math.round(mVolume * 100f);
        drawText(pctStr, mW - mPad - measureText(pctStr, labSize) - mPad, labY, labSize, C_GOLD);

        float sx = sliderX(), sw = sliderW(), sy = sliderY(), sh = sliderH();
        float tr = thumbR(), thumbCX = volToThumbX();
        float brd = Math.max(1.5f, mFontSize * 0.08f);
        rect(sx, sy, sw, sh, C_DARK);
        if (mVolume > 0) rect(sx, sy, mVolume * sw, sh, C_GOLD);
        rect(sx, sy, sw, brd, C_GDIM); rect(sx, sy+sh-brd, sw, brd, C_GDIM);
        rect(sx, sy, brd, sh, C_GDIM); rect(sx+sw-brd, sy, brd, sh, C_GDIM);
        float[] tc = mSliderDragging ? C_GOLD : C_GDIM;
        rect(thumbCX-tr, sy+sh/2f-tr, tr*2f, tr*2f, tc);
        rect(thumbCX-tr, sy+sh/2f-tr, tr*2f, brd*1.5f, C_GOLD);
        rect(thumbCX-tr, sy+sh/2f+tr-brd*1.5f, tr*2f, brd*1.5f, C_GOLD);
        rect(thumbCX-tr, sy+sh/2f-tr, brd*1.5f, tr*2f, C_GOLD);
        rect(thumbCX+tr-brd*1.5f, sy+sh/2f-tr, brd*1.5f, tr*2f, C_GOLD);

        float togW = mUsableW*0.28f, togH = Math.max(mFontSize*2.5f, mH*0.060f);
        float togX0 = cx-togW-mPad*0.5f, togX1 = cx+mPad*0.5f, row2Y = mH*0.56f;
        drawText("FPS:", mPad, row2Y, labSize, C_GOLD);
        drawButton("30", togX0, row2Y, togW, togH, mSetHover==2, mSetPress==2, mFps!=30);
        drawButton("60", togX1, row2Y, togW, togH, mSetHover==3, mSetPress==3, mFps!=60);

        float backW = mUsableW*0.45f, backH = Math.max(mFontSize*2.5f, mH*0.060f);
        drawButton("GERi", cx-backW/2f, mH*0.76f, backW, backH, mSetHover==10, mSetPress==10, false);
        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx = mW/2f;
        float togW = mUsableW*0.28f, togH = Math.max(mFontSize*2.5f, mH*0.060f);
        float togX0 = cx-togW-mPad*0.5f, togX1 = cx+mPad*0.5f, row2Y = mH*0.56f;
        float backW = mUsableW*0.45f, backH = Math.max(mFontSize*2.5f, mH*0.060f);
        float backX = cx-backW/2f, backY = mH*0.76f;
        float sx = sliderX(), sw = sliderW(), sy = sliderY(), sh = sliderH();
        float tr = thumbR(), thumbCX = volToThumbX(), hitPad = tr*1.4f;

        if (action == MotionEvent.ACTION_DOWN) {
            if (x>=thumbCX-hitPad&&x<=thumbCX+hitPad&&y>=sy+sh/2f-hitPad&&y<=sy+sh/2f+hitPad) {
                mSliderDragging = true; updateSliderFromX(x, sx, sw); return;
            }
            if (hit(x,y,sx,sy-tr,sw,sh+tr*2f)) { mSliderDragging=true; updateSliderFromX(x,sx,sw); return; }
            mSetPress = -1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetPress = 2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetPress = 3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetPress = 10;
            mSetHover = mSetPress;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mSliderDragging) { updateSliderFromX(x, sx, sw); return; }
            mSetHover = -1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetHover = 2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetHover = 3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetHover = 10;
        } else if (action == MotionEvent.ACTION_UP) {
            if (mSliderDragging) { mSliderDragging=false; updateSliderFromX(x,sx,sw); return; }
            int p = mSetPress; mSetPress=-1; mSetHover=-1;
            switch (p) {
                case 2:  mFps=30; break;
                case 3:  mFps=60; break;
                case 10: if (hit(x,y,backX,backY,backW,backH)) mState=ST_MENU; break;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mSliderDragging=false; mSetPress=-1; mSetHover=-1;
        }
    }

    private void updateSliderFromX(float tx, float trackX, float trackW) {
        mVolume = Math.max(0f, Math.min(1f, (tx-trackX)/trackW));
        if (mMusic != null) mMusic.onVolumeChanged(mVolume);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN EKRANI — Reigns Mekanigi
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        rect(0, 0, mW, mH, C_BG);
        updateBarLerp();
        drawStatBars();
        drawGameCard();
        drawSwipeHints();
        drawInfoBar();
        if (mGameState.oyunBitti) drawGameOver();
        scanLines();
    }

    // ── Stat Barlari ────────────────────────────────────────────────────────

    private static final String[] BAR_LABELS = {"HALK", "INANC", "EKO", "ORDU"};

    private void drawStatBars() {
        float barsTop   = mPad * 0.8f;
        float barsH     = mH  * 0.14f;
        float barW      = (mUsableW - mPad * 3f) / 4f;
        float barGap    = mPad;
        float labelSize = mFontSize * 0.48f;
        float valueBarH = barsH * 0.42f;
        float valueBarY = barsTop + barsH * 0.42f;

        for (int i = 0; i < 4; i++) {
            float bx = mPad + i * (barW + barGap);

            // Etiket ortalanmis
            float lw = measureText(BAR_LABELS[i], labelSize);
            drawText(BAR_LABELS[i], bx + barW/2f - lw/2f, barsTop, labelSize, C_GOLD);

            // Bar arka plani
            rect(bx, valueBarY, barW, valueBarH, C_DARK);

            // Dolgu
            float fillW = barW * (mBarCurrent[i] / 100f);
            if (fillW > 0f) rect(bx, valueBarY, fillW, valueBarH, barFillColor(mBarCurrent[i]));

            // Cerceve
            float brd = Math.max(1f, mFontSize * 0.06f);
            rect(bx,          valueBarY,               barW, brd,       C_GDIM);
            rect(bx,          valueBarY+valueBarH-brd,  barW, brd,       C_GDIM);
            rect(bx,          valueBarY,               brd,   valueBarH, C_GDIM);
            rect(bx+barW-brd, valueBarY,               brd,   valueBarH, C_GDIM);

            // Yuzde degeri
            String valStr  = (int)mBarCurrent[i] + "%";
            float  valSize = mFontSize * 0.42f;
            float  vw      = measureText(valStr, valSize);
            drawText(valStr, bx + barW/2f - vw/2f,
                     valueBarY + valueBarH + 4f, valSize, C_GDIM);
        }

        // Ayrac cizgisi
        float sepY = barsTop + barsH + mFontSize * 0.4f;
        float[] sc = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.5f};
        rect(mPad, sepY, mUsableW, Math.max(1f, mFontSize * 0.07f), sc);
    }

    private float[] barFillColor(float val) {
        if      (val <= 25f) return new float[]{0.72f, 0.12f, 0.08f, 1f};  // kirmizi
        else if (val >= 75f) return new float[]{0.45f, 0.72f, 0.18f, 1f};  // yesil
        else                 return C_GOLD;
    }

    // ── Kart ──────────────────────────────────────────────────────────────────

    private void drawGameCard() {
        if (mGameState.oyunBitti) return;
        float cardW = mUsableW * 0.82f;
        float cardH = mH * 0.48f;
        float cardX = mW/2f - cardW/2f + mCardOffsetX;
        float cardY = mH * 0.21f;
        float swipeRatio = mCardOffsetX / (mW * 0.5f);

        // Golge
        float[] sc = {0.02f, 0.01f, 0.005f, 0.50f};
        rect(cardX+6f, cardY+6f, cardW, cardH, sc);

        // Arka plan
        rect(cardX, cardY, cardW, cardH, C_DARK);

        // Kenarlik rengi swipe'a gore
        float[] bc;
        if (swipeRatio > 0.15f) {
            float t = Math.min(1f, (swipeRatio-0.15f)/0.6f);
            bc = new float[]{0.15f*(1-t)+0.3f*t, 0.5f*(1-t)+0.85f*t, 0.1f*t, 1f};
        } else if (swipeRatio < -0.15f) {
            float t = Math.min(1f, (-swipeRatio-0.15f)/0.6f);
            bc = new float[]{0.5f*(1-t)+0.85f*t, 0.08f*t, 0.04f*t, 1f};
        } else { bc = C_GDIM; }

        float brd = Math.max(2f, mFontSize * 0.12f);
        rect(cardX,           cardY,           cardW, brd,   bc);
        rect(cardX,           cardY+cardH-brd,  cardW, brd,   bc);
        rect(cardX,           cardY,           brd,   cardH, bc);
        rect(cardX+cardW-brd, cardY,           brd,   cardH, bc);

        // Kart numarasi
        String numStr = "#" + (mCardIndex + 1);
        float[] gd = {C_GREY[0], C_GREY[1], C_GREY[2], 0.55f};
        drawText(numStr, cardX+8f, cardY+8f, mFontSize*0.50f, gd);

        // Baslik
        String title    = CARD_TITLES[mCardIndex % CARD_TITLES.length];
        float  titleSize = Math.min(mFontSize*0.90f, cardW*0.80f/Math.max(1, title.length())*1.5f);
        float  tw        = measureText(title, titleSize);
        float  titleY    = cardY + cardH * 0.13f;
        drawText(title, cardX + cardW/2f - tw/2f, titleY, titleSize, C_GOLD);

        // Ayrac
        float[] dc = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.65f};
        rect(cardX+12f, titleY+titleSize*1.6f, cardW-24f, Math.max(1f, mFontSize*0.06f), dc);

        // Kart metni
        String text   = CARD_TEXTS[mCardIndex % CARD_TEXTS.length];
        float textSize = mFontSize * 0.70f;
        List<String> lines = wrapText(text, cardW * 0.76f, textSize);
        float lineH = textSize * 2.0f;
        float tY    = cardY + cardH*0.38f - lines.size()*lineH/2f;
        float minTY = titleY + titleSize * 2.2f;
        if (tY < minTY) tY = minTY;
        for (int i = 0; i < lines.size(); i++) {
            float lw = measureText(lines.get(i), textSize);
            drawText(lines.get(i), cardX + cardW/2f - lw/2f, tY + i*lineH, textSize, C_GOLD);
        }

        // Hint
        String hint = "< RET  |  ONAYLA >";
        float hintSize = mFontSize * 0.48f;
        float[] hc = {C_GREY[0], C_GREY[1], C_GREY[2], 0.55f};
        float hw = measureText(hint, hintSize);
        drawText(hint, cardX + cardW/2f - hw/2f, cardY + cardH*0.83f, hintSize, hc);

        // EVET / HAYIR overlay
        if (Math.abs(swipeRatio) > 0.18f) {
            float la = Math.min(1f, (Math.abs(swipeRatio)-0.18f)/0.5f);
            String lbl = swipeRatio > 0 ? "EVET" : "HAYIR";
            float[] lc = swipeRatio > 0
                ? new float[]{0.3f, 0.85f, 0.25f, la}
                : new float[]{0.85f, 0.2f, 0.15f, la};
            float lblSize = mFontSize * 1.5f;
            float lw = measureText(lbl, lblSize);
            drawText(lbl, cardX + cardW/2f - lw/2f, cardY + cardH*0.46f, lblSize, lc);
        }
    }

    private void drawSwipeHints() {
        if (mGameState.oyunBitti || mSwiping) return;
        float[] ac = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.35f};
        float arSize = mFontSize * 1.1f;
        drawText("<", mPad*0.5f, mH*0.45f, arSize, ac);
        float gw = measureText(">", arSize);
        drawText(">", mW - mPad*0.5f - gw, mH*0.45f, arSize, ac);
    }

    private void drawInfoBar() {
        float barY = mH * 0.88f, barH = mH * 0.12f;
        float[] ibg = {C_DARK[0]*0.7f, C_DARK[1]*0.7f, C_DARK[2]*0.7f, 0.96f};
        rect(0, barY, mW, barH, ibg);
        float[] sc = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.55f};
        rect(0, barY, mW, Math.max(1f, mFontSize*0.07f), sc);

        int    yil  = YIL_BASLANGIC + mKararSayisi / 10;
        String info = "YIL:" + yil + "  " + getUnvan(mKararSayisi) + "  KARAR:" + mKararSayisi;
        float  infoSize = mFontSize * 0.58f;
        float  iw   = measureText(info, infoSize);
        if (iw > mUsableW) infoSize *= (mUsableW / iw);
        float ix = mW/2f - measureText(info, infoSize)/2f;
        float iy = barY + barH/2f - infoSize*0.6f;
        drawText(info, ix, iy, infoSize, C_GOLD);
    }

    private String getUnvan(int k) {
        if (k <  5) return "YENI UYE";
        if (k < 15) return "BUROKRAT";
        if (k < 30) return "MUSAVIR";
        if (k < 50) return "BAKAN";
        return "BASKAN";
    }

    private void drawGameOver() {
        float[] ov = {0.02f, 0.01f, 0.005f, 0.85f};
        rect(0, 0, mW, mH, ov);
        float cx = mW / 2f;

        drawTextC("OYUN BiTTi", mH*0.20f, mFontSize*1.4f, C_GOLD);

        String reason = getGameOverReason();
        List<String> rLines = wrapText(reason, mUsableW*0.80f, mFontSize*0.70f);
        float rLH = mFontSize * 1.8f, rY = mH * 0.32f;
        for (int i = 0; i < rLines.size(); i++) {
            float lw = measureText(rLines.get(i), mFontSize*0.70f);
            drawText(rLines.get(i), cx-lw/2f, rY+i*rLH, mFontSize*0.70f, C_GDIM);
        }

        String ks = mKararSayisi + " KARAR VERDiN";
        float kSize = mFontSize * 0.78f;
        drawTextC(ks, mH*0.52f, kSize, C_GOLD);

        float bw = mUsableW*0.60f, bh = Math.max(mFontSize*2.5f, mH*0.065f);
        float bx = cx-bw/2f, by = mH*0.64f;
        drawButton("YENiDEN OYNA", bx, by, bw, bh, false, false, false);

        float bw2 = mUsableW*0.50f, bh2 = Math.max(mFontSize*2.2f, mH*0.058f);
        float bx2 = cx-bw2/2f, by2 = mH*0.76f;
        drawButton("MENU", bx2, by2, bw2, bh2, false, false, false);

        if (mTouchAction == MotionEvent.ACTION_UP) {
            if (hit(mTouchX, mTouchY, bx, by, bw, bh)) {
                restartGame(); mTouchAction = -1;
            } else if (hit(mTouchX, mTouchY, bx2, by2, bw2, bh2)) {
                mState = ST_MENU; mTouchAction = -1;
            }
        }
    }

    private String getGameOverReason() {
        if (mGameState.halk    <=  0) return "HALK AYAKLANDISI!";
        if (mGameState.halk    >= 100) return "HALK KONTROLDEN CIKTI.";
        if (mGameState.inanc   <=  0) return "DiN KURUMU SANA KARSI DONDU.";
        if (mGameState.inanc   >= 100) return "DiNi BASKILAR REJiMi BOGDU.";
        if (mGameState.ekonomi <=  0) return "HAZiNE COKTU. DEVLET iFLAS ETTi.";
        if (mGameState.ekonomi >= 100) return "SERMAYE DEVLETi ELE GECiRDi.";
        if (mGameState.ordu    <=  0) return "ORDU SADAKATi SONA ERDi.";
        if (mGameState.ordu    >= 100) return "ASKERi DARBE GERCEKLESTi.";
        return "BiR DENGE BOZULDU.";
    }

    private void syncBarsFromState() {
        mBarTarget[0] = mGameState.halk;
        mBarTarget[1] = mGameState.inanc;
        mBarTarget[2] = mGameState.ekonomi;
        mBarTarget[3] = mGameState.ordu;
    }

    private void updateBarLerp() {
        for (int i = 0; i < 4; i++) {
            float d = mBarTarget[i] - mBarCurrent[i];
            mBarCurrent[i] = Math.abs(d) < 0.4f ? mBarTarget[i] : mBarCurrent[i] + d*0.12f;
        }
    }

    private void applyCardDecision(boolean right) {
        int idx = mCardIndex % CARD_TITLES.length;
        int[] e = right ? CARD_RIGHT[idx] : CARD_LEFT[idx];
        mGameState.halkEkle(e[0]); mGameState.inancEkle(e[1]);
        mGameState.ekonomiEkle(e[2]); mGameState.orduEkle(e[3]);
        syncBarsFromState();
        mCardIndex++; mKararSayisi++;
        mCardOffsetX = 0f; mSwiping = false;
    }

    private void restartGame() {
        mGameState.sifirla(); syncBarsFromState();
        for (int i = 0; i < 4; i++) mBarCurrent[i] = mBarTarget[i];
        mCardIndex = 0; mKararSayisi = 0;
        mCardOffsetX = 0f; mSwiping = false;
    }

    private void touchGame(int action, float x, float y) {
        if (mGameState.oyunBitti) return;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mSwipeStartX = x; mSwiping = true; break;
            case MotionEvent.ACTION_MOVE:
                if (mSwiping) mCardOffsetX = x - mSwipeStartX; break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mSwiping) break;
                float dx = x - mSwipeStartX;
                if (Math.abs(dx) >= SWIPE_THRESHOLD) applyCardDecision(dx > 0);
                else { mCardOffsetX = 0f; mSwiping = false; }
                break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT SiSTEMi — game_font.ttf -> Canvas -> Bitmap -> GL Texture
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Metni ekrana ciz.
     * x = sol kenar px, y = ust kenar px, size = font yuksekligi px
     */
    private void drawText(String text, float x, float y, float size, float[] color) {
        if (text == null || text.isEmpty()) return;
        int texId = getTextTexture(text, size);
        if (texId <= 0) return;
        mTextPaint.setTextSize(size);
        float tw = mTextPaint.measureText(text);
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float th = fm.descent - fm.ascent;
        drawTexColored(texId, x, y, tw, th, 1.0f, color);
    }

    /** Yatay ortali metin */
    private void drawTextC(String text, float y, float size, float[] color) {
        if (text == null || text.isEmpty()) return;
        mTextPaint.setTextSize(size);
        float tw = mTextPaint.measureText(text);
        drawText(text, mW/2f - tw/2f, y, size, color);
    }

    /** Metin genisligini olc (px) */
    private float measureText(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        mTextPaint.setTextSize(size);
        return mTextPaint.measureText(text);
    }

    /**
     * LRU onbellekli metin texture olusturucu.
     * Anahtar: "metin|boyut_int"
     */
    private int getTextTexture(String text, float size) {
        String key = text + "|" + (int)size;
        int[] cached = mTexCache.get(key);
        if (cached != null && cached[0] > 0) return cached[0];

        mTextPaint.setTextSize(size);
        float tw = mTextPaint.measureText(text);
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        int bw = Math.max(1, (int)Math.ceil(tw));
        int bh = Math.max(1, (int)Math.ceil(fm.descent - fm.ascent));

        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return -1;
        }

        Canvas canvas = new Canvas(bmp);
        bmp.eraseColor(0x00000000);          // seffaf
        mTextPaint.setColor(0xFFFFFFFF);     // beyaz — shader renk uygular
        canvas.drawText(text, 0, -fm.ascent, mTextPaint);

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        bmp.recycle();

        mTexCache.put(key, ids);
        return ids[0];
    }

    // ── Metin satirlarini sar ───────────────────────────────────────────────

    private List<String> wrapText(String text, float maxW, float size) {
        List<String> result = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { result.add(""); continue; }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (measureText(test, size) <= maxW) {
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

    // ══════════════════════════════════════════════════════════════════════
    //  BUTON
    // ══════════════════════════════════════════════════════════════════════

    private void drawButton(String label, float x, float y, float w, float h,
                            boolean hover, boolean pressed, boolean disabled) {
        float[] bg  = disabled ? C_DARK : (pressed ? C_PRES : C_DARK);
        float[] brd = disabled ? C_GREY : (pressed || hover ? C_GOLD : C_GDIM);
        float[] txt = disabled ? C_GREY : (pressed || hover ? C_GOLD : C_GDIM);

        rect(x, y, w, h, bg);
        float b = Math.max(1.5f, mFontSize * 0.08f);
        rect(x,     y,     w, b,   brd);
        rect(x,     y+h-b, w, b,   brd);
        rect(x,     y,     b, h,   brd);
        rect(x+w-b, y,     b, h,   brd);

        float btnSize = Math.min(mFontSize * 0.75f,
                        w * 0.78f / Math.max(1, label.length()) * 1.5f);
        float tw = measureText(label, btnSize);
        float th = btnSize;
        drawText(label, x + w/2f - tw/2f, y + h/2f - th/2f, btnSize, txt);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CiZiM YONTEMLERi
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

    /** PNG goruntu texturelari (logo, menu bg, sinematik) */
    private void drawTex(int id, float x, float y, float w, float h, float alpha, float[] color) {
        drawTexColored(id, x, y, w, h, alpha, color);
    }

    /** Tum texture cizimi buradan geciyor */
    private void drawTexColored(int id, float x, float y, float w, float h,
                                 float alpha, float[] color) {
        if (id <= 0) return;
        GLES20.glUseProgram(mProgTex);
        GLES20.glUniform2f(mLocTURes, mW, mH);
        GLES20.glUniform1i(mLocTUTex, 0);
        GLES20.glUniform1f(mLocTUAlpha, alpha);
        if (color != null) {
            GLES20.glUniform4f(mLocTUColor, color[0], color[1], color[2], color[3]);
        } else {
            GLES20.glUniform4f(mLocTUColor, 0f, 0f, 0f, 0f);  // PNG modu
        }
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

    private void scanLines() {
        float step = Math.max(2f, mH / 400f) * 3f;
        for (float sy = 0; sy < mH; sy += step)
            rect(0, sy, mW, Math.max(1f, step * 0.35f), C_SCAN);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PNG TEXTURE YUKLEME
    // ══════════════════════════════════════════════════════════════════════

    private int loadImageTexture(String name) {
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
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] s = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, s, 0);
        if (s[0] == 0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f);
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
        return s;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YARDIMCI
    // ══════════════════════════════════════════════════════════════════════

    private boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx+rw && py >= ry && py <= ry+rh;
    }
}

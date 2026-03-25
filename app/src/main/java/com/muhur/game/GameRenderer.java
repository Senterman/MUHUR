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
 * MÜHÜR — GameRenderer  (Revizyon 6)
 *
 * Değişiklikler (Rev 6):
 *   1. cards.json entegrasyonu: loadCardsFromJson() ile
 *      assets/scenario/opposition_1/cards.json okunur (org.json, API 1+).
 *   2. Kart içeriği ekranda gösterilir: karakter etiketi, flavor metni,
 *      ana metin — tümü pixel fontuyla, sarılmış (wrapText).
 *   3. Swipe sırasında kart üzerinde "EVET" (sağ) / "HAYIR" (sol) etiketi
 *      beliren çerçeveli kutu ile gösterilir; opaklık kaydırmayla artar.
 *   4. Alt durum çubuğu: Perde numarası | Kart ID | Karakter adı.
 *   5. onCardChosen() → loadCardAtIndex() zinciri ile sıradaki kart
 *      otomatik yüklenir.
 *   6. Gizli köşe (sol üst) ve font test geçişi ilk kartı sıfırlar.
 *
 * Korunan / DEĞİŞMEYEN:
 *   - Tüm state machine akışı (ST_SPLASH → ST_MENU → ST_CINEMA → ST_GAME)
 *   - Shader'lar, texture yükleme, scanLines, CRT efekti
 *   - fitTextPS(), wrapText(), drawButton(), tüm çizim primitifleri
 *   - MusicController interface ve ses slider sistemi
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

    // ─── STATE SABİTLERİ ───────────────────────────────────────────────────
    private static final int ST_SPLASH   = 0;
    private static final int ST_MENU     = 1;
    private static final int ST_CINEMA   = 2;
    private static final int ST_SETTINGS = 3;
    private static final int ST_GAME     = 4;
    private static final int ST_FONTTEST = 5;  // Font test ekranı

    // ─── RENK PALETİ ───────────────────────────────────────────────────────
    private static final float[] C_BG   = {0.051f, 0.039f, 0.020f, 1.0f};
    private static final float[] C_GOLD = {0.831f, 0.659f, 0.325f, 1.0f};
    private static final float[] C_GDIM = {0.420f, 0.330f, 0.160f, 1.0f};
    private static final float[] C_DARK = {0.090f, 0.070f, 0.035f, 1.0f};
    private static final float[] C_PRES = {0.180f, 0.140f, 0.070f, 1.0f};
    private static final float[] C_GREY = {0.260f, 0.240f, 0.220f, 1.0f};
    private static final float[] C_SCAN = {0.000f, 0.000f, 0.000f, 0.15f};

    // ─── SİNEMATİK VERİ — intro.md'den yüklenir ───────────────────────────
    // Sabit metin GÖMÜLMEMİŞTİR. Veriler loadIntroFromAssets() ile
    // src/scenario/Opposition_1/intro.md dosyasından okunur.
    private String[]   mCinemaText   = new String[0];
    private String[]   mCinemaAssets = new String[0];

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
    private int   mTexLogo   = -1;
    private int   mTexMenuBg = -1;
    private int[] mTexCinema = new int[0]; // intro.md yüklendikten sonra boyut belirlenir

    // ─── EKRAN & GÜVENLI ALAN ──────────────────────────────────────────────
    private float mW, mH;
    private float mPad;
    private float mUsableW;

    // ─── FONT ──────────────────────────────────────────────────────────────
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

    // Menu
    private int mMenuHover = -1, mMenuPress = -1;

    // Settings
    private int mSetHover  = -1, mSetPress  = -1;

    // ─── SES SLIDER ────────────────────────────────────────────────────────
    private float   mVolume        = 0.75f;
    private boolean mSliderDragging = false;
    private int     mFps           = 60;
    private boolean mMusicStarted  = false;

    // ─── FONT TEST ─────────────────────────────────────────────────────────
    // Satır 1-2: Büyük harfler + Türkçe büyük
    // Satır 3-4: Küçük harfler + Türkçe küçük
    // Satır 5:   Rakamlar
    // Satır 6:   Noktalama + tırnak
    private static final String[] FONT_TEST_LINES = {
        "ABCC\u00C7DEFGG\u011EHHII\u0130",
        "JKLMNOO\u00D6PRSS\u015ETUU\u00DCVYZ",
        "abc\u00E7defg\u011Fh\u0131ijklmn",
        "oo\u00F6prs\u015Ftuu\u00FCvyz",
        "0123456789",
        ". , ! ? : ( ) \" ' - + = /"
    };
    private int  mFtChar  = 0;
    private long mFtLast  = 0;
    private boolean mFtDone = false;
    private static final long FT_CHAR_MS = 40;

    // ─── KART SİSTEMİ — Swipe & Nokta Geri Bildirimi ──────────────────────
    // Kart kaydırma
    private float   mCardSwipeX      = 0f;   // anlık yatay kaydırma miktarı
    private boolean mCardSwiping     = false;
    private float   mCardSwipeStartX = 0f;
    // Aktif kartın bar etkileri (cards.json'dan yüklenir)
    // Sıra: [halk, din, para, ordu]  — BAR_LABELS ile aynı sıra
    private int[]   mCardEffectLeft  = {0, 0, 0, 0};
    private int[]   mCardEffectRight = {0, 0, 0, 0};
    // Bar etiketleri (GameState ile aynı sıra)
    private static final String[] BAR_LABELS = {"HALK", "DİN", "PARA", "ORDU"};

    // ─── AKTİF KART VERİSİ (cards.json'dan yüklenir) ─────────────────────
    private String  mCardId        = "";
    private String  mCardCharacter = "";
    private String  mCardFlavor    = "";  // italik üst metin
    private String  mCardText      = "";  // ana kart metni
    private String  mCardLabelLeft = "HAYIR";
    private String  mCardLabelRight= "EVET";
    private int     mCardAct       = 1;
    // JSON'dan yüklenen tüm kart listesi
    private final List<String[]> mAllCards = new ArrayList<>();
    // [0]=id [1]=char [2]=flavor [3]=text [4]=labelL [5]=labelR
    // [6]=halkL [7]=dinL [8]=paraL [9]=orduL
    // [10]=halkR [11]=dinR [12]=paraR [13]=orduR
    private int     mCurrentCardIdx = 0;

    // ─── CONTEXT & MÜZİK ───────────────────────────────────────────────────
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

        // intro.md'yi yükle; mCinemaText, mCinemaAssets ve mTexCinema doldurulur
        loadIntroFromAssets();
        // cards.json'ı yükle
        loadCardsFromJson();
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
        // intro.md'den okunan asset listesiyle texture'ları yükle
        mTexCinema = new int[mCinemaAssets.length];
        for (int i = 0; i < mCinemaAssets.length; i++) {
            mTexCinema[i] = loadTexture(mCinemaAssets[i]);
        }

        mCinemaLast = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        mW = w; mH = h;
        GLES20.glViewport(0, 0, w, h);

        mPad     = mW * 0.05f;
        mUsableW = mW - mPad * 2f;

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
            case ST_FONTTEST: touchFontTest(action, x, y);     break;
            case ST_GAME:     touchGame(action, x, y);         break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ══════════════════════════════════════════════════════════════════════

    private void update() {
        switch (mState) {
            case ST_SPLASH:   updateSplash();   break;
            case ST_CINEMA:   updateCinema();   break;
            case ST_FONTTEST: updateFontTest(); break;
        }
    }

    private void updateSplash() {
        if      (mFrame < 30)                  mSplashAlpha = mFrame / 30f;
        else if (mFrame > SPLASH_FRAMES - 30)  mSplashAlpha = (SPLASH_FRAMES - mFrame) / 30f;
        else                                   mSplashAlpha = 1f;

        if (mFrame >= SPLASH_FRAMES) {
            mFrame = 0;
            mState     = ST_MENU;
            mMenuHover = mMenuPress = -1;
            triggerMusicStart();
        }
    }

    private void updateCinema() {
        if (mCinemaDone || mCinemaText.length == 0) return;
        String text = mCinemaText[mCinemaScene];
        long now = System.currentTimeMillis();
        if (now - mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast = now;
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
        GLES20.glClearColor(C_BG[0], C_BG[1], C_BG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        switch (mState) {
            case ST_SPLASH:   renderSplash();    break;
            case ST_MENU:     renderMenu();      break;
            case ST_CINEMA:   renderCinema();    break;
            case ST_SETTINGS: renderSettings();  break;
            case ST_FONTTEST: renderFontTest();  break;
            case ST_GAME:     renderGame();      break;
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

        float ps = fitTextPS("BONECASTOFFICIAL", mUsableW * 0.88f, gPS);
        float[] col = {C_GOLD[0], C_GOLD[1], C_GOLD[2], a};
        drawStringC("BONECASTOFFICIAL", cy + mH * 0.09f, ps, col);

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

    private static final String[] MENU_LABELS = { "YENİ OYUN","DEVAM ET","AYARLAR","ÇIKIŞ" };

    private void renderMenu() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.72f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.50f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;

        float titlePS = fitTextPS("MÜHÜR", mUsableW * 0.80f, gPS * 2.0f);
        drawStringC("MÜHÜR", mH * 0.16f, titlePS, C_GOLD);

        drawStringCWrapped("KADERİN, MÜHÜRÜN UCUNDA.",
                mH * 0.29f, gPS * 0.68f, C_GDIM);

        float devPS = fitTextPS("BONECASTOFFICIAL", mUsableW * 0.68f, gPS * 0.58f);
        drawStringC("BONECASTOFFICIAL", mH * 0.36f, devPS, C_GREY);

        float bw    = mUsableW * 0.72f;
        float bh    = Math.max(gPS * 8.5f, mH * 0.065f);
        float bx    = cx - bw / 2f;
        float gap   = bh + gPS * 2.5f;
        float startY= mH * 0.46f;

        for (int i = 0; i < MENU_LABELS.length; i++) {
            boolean disabled = (i == 1);
            drawButton(MENU_LABELS[i], bx, startY + i * gap, bw, bh,
                    mMenuHover == i, mMenuPress == i, disabled);
        }

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
                case 0: startCinema(); break;
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
        if (mCinemaText.length == 0) return;
        int bg = (mCinemaScene < mTexCinema.length) ? mTexCinema[mCinemaScene] : -1;
        if (bg > 0) {
            drawTex(bg, 0, 0, mW, mH, 0.82f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.52f};
            rect(0, 0, mW, mH, ov);
        }

        float snPS = gPS * 0.72f;
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.75f};
        String scn = (mCinemaScene + 1) + "/" + mCinemaText.length;
        drawString(scn, mW - mPad - charWidth(scn, snPS), mPad * 1.5f, snPS, greyA);

        String fullText = mCinemaText[mCinemaScene];
        String visible  = fullText.substring(0, Math.min(mCinemaChar, fullText.length()));

        float textPS   = gPS * 0.90f;
        float maxLineW = mUsableW * 0.86f;

        List<String> lines = wrapText(visible, maxLineW, textPS);

        float lineH  = textPS * 11f;
        float totalH = lines.size() * lineH;
        float textY  = mH * 0.52f - totalH / 2f;

        for (int i = 0; i < lines.size(); i++) {
            drawStringC(lines.get(i), textY + i * lineH, textPS, C_GOLD);
        }

        if (!mCinemaDone && (mFrame / 15) % 2 == 0) {
            String lastLine = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
            float curX = mW/2f + charWidth(lastLine, textPS)/2f + textPS;
            float curY = textY + (lines.size() - 1) * lineH;
            rect(curX, curY, textPS, textPS * 8f, C_GOLD);
        }

        if (mCinemaDone) {
            float bw = mUsableW * 0.42f;
            float bh = Math.max(gPS * 9f, mH * 0.06f);
            float bx = mW/2f - bw/2f, by = mH * 0.82f;
            drawButton("DEVAM", bx, by, bw, bh, mSetHover == 99, false, false);
        }

        float sw = mUsableW * 0.30f;
        float sh = Math.max(gPS * 7f, mH * 0.05f);
        float sx = mPad, sy = mH - sh - mPad * 2f;
        drawButton("ATLA", sx, sy, sw, sh, false, mSkipHeld, false);

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
                mCinemaChar = mCinemaText.length > 0 ? mCinemaText[mCinemaScene].length() : 0;
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
        if (mCinemaScene >= mCinemaText.length) {
            // Sinematik bitti → Font test ekranına geç
            mFtChar = 0;
            mFtLast = System.currentTimeMillis();
            mFtDone = false;
            mState  = ST_FONTTEST;
        } else {
            mCinemaChar = 0;
            mCinemaDone = false;
            mCinemaLast = System.currentTimeMillis();
        }
    }

    private void skipAllCinema() {
        mSkipHeld = false;
        mFtChar   = 0;
        mFtLast   = System.currentTimeMillis();
        mFtDone   = false;
        mState    = ST_FONTTEST;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AYARLAR  — Ses Slider + FPS Seçimi
    // ══════════════════════════════════════════════════════════════════════

    private float sliderX()  { return mW / 2f - sliderW() / 2f; }
    private float sliderW()  { return mUsableW * 0.85f; }
    private float sliderY()  { return mH * 0.42f; }
    private float sliderH()  { return Math.max(gPS * 3f, 6f); }
    private float thumbR()   { return Math.max(gPS * 4.5f, 10f); }

    private float volToThumbX() {
        return sliderX() + mVolume * sliderW();
    }

    private void renderSettings() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.5f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.65f};
            rect(0, 0, mW, mH, ov);
        }

        float cx = mW / 2f;

        float titlePS = fitTextPS("AYARLAR", mUsableW * 0.60f, gPS * 1.6f);
        drawStringC("AYARLAR", mH * 0.14f, titlePS, C_GOLD);

        float labPS  = gPS * 0.72f;
        float labY   = mH * 0.34f;
        drawString("SES SEVİYESİ:", mPad, labY, labPS, C_GOLD);

        int pct = Math.round(mVolume * 100f);
        String pctStr = "%" + pct;
        float pctPS = fitTextPS(pctStr, mUsableW * 0.18f, labPS);
        float pctX  = mW - mPad - charWidth(pctStr, pctPS);
        drawString(pctStr, pctX, labY, pctPS, C_GOLD);

        float sx = sliderX(), sw = sliderW();
        float sy = sliderY(), sh = sliderH();
        float tr = thumbR();
        float thumbCX = volToThumbX();

        rect(sx, sy, sw, sh, C_DARK);
        float filled = mVolume * sw;
        if (filled > 0) rect(sx, sy, filled, sh, C_GOLD);
        float brd = Math.max(1.5f, gPS * 0.3f);
        rect(sx,      sy,      sw, brd, C_GDIM);
        rect(sx,      sy+sh-brd, sw, brd, C_GDIM);
        rect(sx,      sy,      brd, sh, C_GDIM);
        rect(sx+sw-brd, sy,   brd, sh, C_GDIM);

        float[] thumbCol = mSliderDragging ? C_GOLD : C_GDIM;
        rect(thumbCX - tr, sy + sh/2f - tr, tr*2f, tr*2f, thumbCol);
        rect(thumbCX - tr,      sy + sh/2f - tr,      tr*2f, brd*1.5f, C_GOLD);
        rect(thumbCX - tr,      sy + sh/2f + tr-brd*1.5f, tr*2f, brd*1.5f, C_GOLD);
        rect(thumbCX - tr,      sy + sh/2f - tr,      brd*1.5f, tr*2f, C_GOLD);
        rect(thumbCX + tr-brd*1.5f, sy + sh/2f - tr, brd*1.5f, tr*2f, C_GOLD);

        float togW  = mUsableW * 0.28f;
        float togH  = Math.max(gPS * 9f, mH * 0.060f);
        float togX0 = cx - togW - mPad * 0.5f;
        float togX1 = cx + mPad * 0.5f;
        float row2Y = mH * 0.56f;

        drawString("FPS:", mPad, row2Y, labPS, C_GOLD);
        drawButton("30", togX0, row2Y, togW, togH, mSetHover==2, mSetPress==2, mFps!=30);
        drawButton("60", togX1, row2Y, togW, togH, mSetHover==3, mSetPress==3, mFps!=60);

        float backW = mUsableW * 0.45f;
        float backH = Math.max(gPS * 9f, mH * 0.060f);
        drawButton("GERİ", cx - backW/2f, mH * 0.76f, backW, backH,
                mSetHover==10, mSetPress==10, false);

        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx    = mW / 2f;
        float togW  = mUsableW * 0.28f;
        float togH  = Math.max(gPS * 9f, mH * 0.060f);
        float togX0 = cx - togW - mPad * 0.5f;
        float togX1 = cx + mPad * 0.5f;
        float row2Y = mH * 0.56f;
        float backW = mUsableW * 0.45f;
        float backH = Math.max(gPS * 9f, mH * 0.060f);
        float backX = cx - backW/2f, backY = mH * 0.76f;

        float sx = sliderX(), sw = sliderW();
        float sy = sliderY(), sh = sliderH();
        float tr = thumbR();
        float thumbCX = volToThumbX();
        float hitPad = tr * 1.4f;

        if (action == MotionEvent.ACTION_DOWN) {
            if (x >= thumbCX - hitPad && x <= thumbCX + hitPad
                    && y >= sy + sh/2f - hitPad && y <= sy + sh/2f + hitPad) {
                mSliderDragging = true;
                updateSliderFromX(x, sx, sw);
                return;
            }
            if (hit(x, y, sx, sy - tr, sw, sh + tr*2f)) {
                mSliderDragging = true;
                updateSliderFromX(x, sx, sw);
                return;
            }

            mSetPress = -1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetPress=2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetPress=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetPress=10;
            mSetHover = mSetPress;

        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mSliderDragging) {
                updateSliderFromX(x, sx, sw);
                return;
            }
            mSetHover = -1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetHover=2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetHover=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetHover=10;

        } else if (action == MotionEvent.ACTION_UP) {
            if (mSliderDragging) {
                mSliderDragging = false;
                updateSliderFromX(x, sx, sw);
                return;
            }
            int p = mSetPress; mSetPress = -1; mSetHover = -1;
            switch (p) {
                case 2:  mFps=30; break;
                case 3:  mFps=60; break;
                case 10: if (hit(x,y,backX,backY,backW,backH)) mState=ST_MENU; break;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mSliderDragging = false;
            mSetPress = -1; mSetHover = -1;
        }
    }

    private void updateSliderFromX(float touchX, float trackX, float trackW) {
        float raw = (touchX - trackX) / trackW;
        mVolume = Math.max(0f, Math.min(1f, raw));
        if (mMusic != null) mMusic.onVolumeChanged(mVolume);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT TEST EKRANı
    //
    //  Sinematik bittikten sonra otomatik açılır.
    //  3 satır alfabeyi daktilo efektiyle gösterir:
    //    Satır 1: A-Z + Türkçe büyükler (ikişerli: Latin + Türkçe yan yana)
    //    Satır 2: Rakamlar
    //    Satır 3: Noktalama
    //  Dokunulunca → metin tamamlanır / tamamlandıysa ST_GAME'e geçer.
    //  Sol üst köşeye (mPad*3 kare) dokunulunca her zaman ST_GAME'e geçer.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * FONT_TEST_LINES'ın tüm karakterlerini tek bir düzleştirilmiş
     * karakter dizisi olarak döndürür. Daktilo sayacı bunu kullanır.
     */
    private int ftTotalChars() {
        int n = 0;
        for (String s : FONT_TEST_LINES) n += s.length();
        return n;
    }

    private void updateFontTest() {
        if (mFtDone) return;
        long now = System.currentTimeMillis();
        if (now - mFtLast >= FT_CHAR_MS) {
            mFtLast = now;
            if (mFtChar < ftTotalChars()) mFtChar++;
            else mFtDone = true;
        }
    }

    private void renderFontTest() {
        // ── Arka plan ───────────────────────────────────────────────────
        float[] dimOv = {C_BG[0], C_BG[1], C_BG[2], 1.0f};
        rect(0, 0, mW, mH, dimOv);

        // ── Satırları daktilo efektiyle çiz ─────────────────────────────
        // "FONT TEST" başlığı kaldırıldı — üretim build'inde görünmez.
        float startY   = mH * 0.06f;
        float maxLineH = (mH * 0.72f) / FONT_TEST_LINES.length;
        float lineH    = maxLineH * 0.82f;  // satır içi yükseklik (boşluk bırak)

        int charsLeft = mFtChar;  // kalan gösterilecek karakter sayısı

        for (int li = 0; li < FONT_TEST_LINES.length; li++) {
            String line = FONT_TEST_LINES[li];
            float rowY  = startY + li * maxLineH;

            // Bu satır için ps: satır mUsableW'ya sığsın, max gPS*1.05
            float ps = fitTextPS(line, mUsableW * 0.92f, gPS * 1.05f);

            // Kaç karakter gösterilecek?
            int show = Math.min(charsLeft, line.length());
            charsLeft -= show;
            if (show <= 0) break;

            String visible = line.substring(0, show);

            // Satır içi baseline çizgisi (referans — hangi satırda olduğu belli olsun)
            float baselineY = rowY + ps * 8f;
            float[] baseCol = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.25f};
            rect(mPad, baselineY, mUsableW, Math.max(1f, gPS * 0.3f), baseCol);

            // Karakterleri sol hizalı çiz
            drawString(visible, mPad, rowY, ps, C_GOLD);

            // Yanıp sönen imleç (bu satır henüz yazılıyorsa)
            if (show < line.length() && (mFrame / 12) % 2 == 0) {
                float curX = mPad + charWidth(visible, ps);
                rect(curX, rowY, ps * 0.8f, ps * 9f, C_GOLD);
            }
        }

        // ── Sağ alt: durum mesajı ────────────────────────────────────────
        float infoPS = gPS * 0.58f;
        float[] infoCol = {C_GREY[0], C_GREY[1], C_GREY[2], 0.70f};

        if (!mFtDone) {
            drawString("YAZILIYOR...", mPad, mH * 0.90f, infoPS, infoCol);
        } else {
            // Tamamlandıysa devam butonu göster
            float bw = mUsableW * 0.50f;
            float bh = Math.max(gPS * 9f, mH * 0.058f);
            float bx = mW/2f - bw/2f, by = mH * 0.88f;
            drawButton("DEVAM", bx, by, bw, bh, false, false, false);
        }

        // Sol üst köşe: gizli "atla" bölgesi (mPad*4 kare)
        float[] skipCol = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.12f};
        rect(0, 0, mPad * 4f, mPad * 4f, skipCol);

        scanLines();
    }

    private void touchFontTest(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return;

        // Sol üst gizli köşe → anında ST_GAME
        if (hit(x, y, 0, 0, mPad * 4f, mPad * 4f)) {
            mState = ST_GAME;
            return;
        }

        if (!mFtDone) {
            // Metin yazılıyorsa → anında tamamla
            mFtChar = ftTotalChars();
            mFtDone = true;
        } else {
            // Tamamlandıysa → oyuna geç, ilk kartı hazırla
            mCurrentCardIdx = 0;
            if (!mAllCards.isEmpty()) loadCardAtIndex(0);
            mState = ST_GAME;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN — Kart Ekranı
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        // ── Arka plan ───────────────────────────────────────────────────
        rect(0, 0, mW, mH, C_BG);

        // ── Üst bar şeridi ───────────────────────────────────────────────
        float barAreaH = mH * 0.10f;
        float barAreaY = mPad * 1.2f;
        drawStatBars(barAreaY, barAreaH);

        // ── Alt durum çubuğu (yıl, karar, unvan) ────────────────────────
        renderStatusBar();

        // ── Kart geometrisi ───────────────────────────────────────────────
        float cardW      = mUsableW * 0.88f;
        float cardH      = mH * 0.52f;
        float cardCenterY= mH * 0.22f;
        float absSwipe   = Math.abs(mCardSwipeX);
        float tiltOffset = mCardSwipeX * 0.08f;  // üst kısmı sabit, alt kayar
        float cardX      = mW / 2f - cardW / 2f + mCardSwipeX;

        // Kart gölgesi (hafif, sabit)
        float[] shadowCol = {0f, 0f, 0f, 0.30f};
        rect(cardX + gPS * 0.8f, cardCenterY + tiltOffset + gPS * 0.8f, cardW, cardH, shadowCol);

        // Kart arkaplanı
        rect(cardX, cardCenterY + tiltOffset, cardW, cardH, C_DARK);

        // Kart çerçeve rengi — kaydırma yönüne göre
        float[] frameCol;
        float swipeRatio = mCardSwipeX / (mW * 0.28f);  // -1..+1 normalize
        if (absSwipe > mW * 0.06f) {
            frameCol = (mCardSwipeX > 0) ? C_GOLD : C_GDIM;
        } else {
            frameCol = C_GDIM;
        }
        float b = Math.max(1.5f, gPS * 0.4f);
        rect(cardX,             cardCenterY + tiltOffset,            cardW, b,     frameCol);
        rect(cardX,             cardCenterY + tiltOffset + cardH - b, cardW, b,     frameCol);
        rect(cardX,             cardCenterY + tiltOffset,             b,     cardH, frameCol);
        rect(cardX + cardW - b, cardCenterY + tiltOffset,             b,     cardH, frameCol);

        // ── Kart içeriği ─────────────────────────────────────────────────
        float innerX  = cardX + gPS * 3f;
        float innerW  = cardW - gPS * 6f;
        float innerY  = cardCenterY + tiltOffset + gPS * 3f;

        // Karakter etiketi (üst)
        float charPS    = gPS * 0.58f;
        float[] charCol = {C_GREY[0], C_GREY[1], C_GREY[2], 0.85f};
        String charLabel= mCardCharacter.replace("_", " ").toUpperCase();
        drawString(charLabel, innerX, innerY, charPS, charCol);

        // Ayırıcı çizgi
        float divY = innerY + charPS * 10f + gPS;
        rect(innerX, divY, innerW, Math.max(1f, gPS * 0.3f), C_GDIM);

        // Flavor metni (italik yok ama soluk renk)
        float flavPS  = gPS * 0.62f;
        float[] flavCol = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.80f};
        List<String> flavLines = wrapText(mCardFlavor, innerW, flavPS);
        float flavY = divY + gPS * 2f;
        for (int i = 0; i < Math.min(flavLines.size(), 2); i++) {
            drawString(flavLines.get(i), innerX, flavY + i * flavPS * 11f, flavPS, flavCol);
        }

        // Ana kart metni
        float textPS  = gPS * 0.75f;
        float textY   = flavY + Math.min(flavLines.size(), 2) * flavPS * 11f + gPS * 2f;
        List<String> textLines = wrapText(mCardText, innerW, textPS);
        int maxTextLines = 6;
        for (int i = 0; i < Math.min(textLines.size(), maxTextLines); i++) {
            drawString(textLines.get(i), innerX, textY + i * textPS * 11f, textPS, C_GOLD);
        }

        // ── EVET / HAYIR etiketi — kaydırma aktifse göster ───────────────
        if (absSwipe > mW * 0.04f) {
            boolean goRight = mCardSwipeX > 0;
            String  swipeLabel  = goRight ? "EVET" : "HAYIR";
            float[] swipeLabelCol;
            float   labelAlpha  = Math.min(1f, absSwipe / (mW * 0.15f));

            if (goRight) {
                // Sağa — altın (kabul)
                swipeLabelCol = new float[]{C_GOLD[0], C_GOLD[1], C_GOLD[2], labelAlpha};
            } else {
                // Sola — soluk (ret)
                swipeLabelCol = new float[]{C_GDIM[0], C_GDIM[1], C_GDIM[2], labelAlpha};
            }

            float labelPS = fitTextPS(swipeLabel, cardW * 0.55f, gPS * 2.2f);
            float labelW  = charWidth(swipeLabel, labelPS);
            float labelX  = mW / 2f - labelW / 2f + mCardSwipeX * 0.4f;
            float labelY  = cardCenterY + cardH * 0.38f + tiltOffset;

            // Etiket arka kutu (yarı saydam)
            float pad = gPS * 2f;
            float[] boxCol = {C_BG[0], C_BG[1], C_BG[2], labelAlpha * 0.75f};
            rect(labelX - pad, labelY - pad, labelW + pad * 2f, labelPS * 10f + pad * 2f, boxCol);
            // Etiket çerçeve çizgisi
            float bl = Math.max(1f, gPS * 0.35f);
            rect(labelX - pad,               labelY - pad,               labelW + pad*2f, bl,   swipeLabelCol);
            rect(labelX - pad,               labelY + labelPS*10f + pad - bl, labelW + pad*2f, bl, swipeLabelCol);
            rect(labelX - pad,               labelY - pad,               bl, labelPS*10f + pad*2f, swipeLabelCol);
            rect(labelX + labelW + pad - bl, labelY - pad,               bl, labelPS*10f + pad*2f, swipeLabelCol);

            drawString(swipeLabel, labelX, labelY, labelPS, swipeLabelCol);

            // Bar etki noktaları
            drawBarHintDots(goRight, barAreaY, barAreaH);
        }

        // ── Yön okları — swipe yapılmıyorken göster ──────────────────────
        if (!mCardSwiping) {
            float arrowPS  = gPS * 0.65f;
            float[] dimArr = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.45f};
            drawString("<", mPad, mH * 0.46f, arrowPS, dimArr);
            float arrRight = mW - mPad - charWidth(">", arrowPS);
            drawString(">", arrRight, mH * 0.46f, arrowPS, dimArr);
        }

        scanLines();
    }

    /**
     * Alt durum çubuğu: Yıl | Karar No | Karakter adı
     */
    private void renderStatusBar() {
        float barY   = mH * 0.91f;
        float barH   = mH - barY;
        float[] sepCol = {C_GDIM[0], C_GDIM[1], C_GDIM[2], 0.30f};
        rect(0, barY - Math.max(1f, gPS * 0.3f), mW, Math.max(1f, gPS * 0.3f), sepCol);
        rect(0, barY, mW, barH, C_DARK);

        float ps = gPS * 0.55f;
        String actStr  = "PERDE " + mCardAct;
        String cardStr = mCardId.isEmpty() ? "" : "#" + mCardId.replace("OPP1-", "");
        String charStr = mCardCharacter.replace("_", " ").toUpperCase();

        drawString(actStr, mPad, barY + gPS, ps, C_GOLD);
        if (!cardStr.isEmpty()) {
            float cw = charWidth(cardStr, ps);
            drawString(cardStr, mW / 2f - cw / 2f, barY + gPS, ps, C_GDIM);
        }
        float nw = charWidth(charStr, ps);
        drawString(charStr, mW - mPad - nw, barY + gPS, ps, C_GREY);
    }

    /**
     * Oyun ekranı dokunma — kart kaydırma (swipe) algılar.
     * Kart sağa veya sola belirli eşiği geçince seçim yapılır.
     */
    private void touchGame(int action, float x, float y) {
        float cardW   = mUsableW * 0.88f;
        float cardH   = mH * 0.52f;
        float cardX0  = mW / 2f - cardW / 2f;
        float cardY0  = mH * 0.22f;
        float threshold = mW * 0.28f; // bu kadar kaydırırsan seçim yapılır

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (hit(x, y, cardX0, cardY0, cardW, cardH)) {
                    mCardSwiping     = true;
                    mCardSwipeStartX = x;
                    mCardSwipeX      = 0f;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mCardSwiping) {
                    mCardSwipeX = x - mCardSwipeStartX;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCardSwiping) {
                    if (mCardSwipeX > threshold) {
                        // SAĞ seçim — kart kabul edildi
                        onCardChosen(true);
                    } else if (mCardSwipeX < -threshold) {
                        // SOL seçim — kart reddedildi
                        onCardChosen(false);
                    }
                    // Eşiğe ulaşmadıysa kartı geri çek
                    mCardSwipeX  = 0f;
                    mCardSwiping = false;
                }
                break;
        }
    }

    /**
     * Kart seçimi yapıldığında çağrılır.
     * Seçim efektlerini uygular ve sıradaki kartı yükler.
     *
     * @param right true = sağ (EVET/kabul), false = sol (HAYIR/ret)
     */
    private void onCardChosen(boolean right) {
        // Sıradaki kartı yükle
        mCurrentCardIdx++;
        if (mCurrentCardIdx < mAllCards.size()) {
            loadCardAtIndex(mCurrentCardIdx);
        } else {
            // Kart listesi bitti
            mCardId = ""; mCardText = ""; mCardFlavor = "";
            mCardCharacter = ""; mCardAct = 1;
            mCardEffectLeft  = new int[]{0, 0, 0, 0};
            mCardEffectRight = new int[]{0, 0, 0, 0};
        }
    }

    /** Kart listesinden belirtilen indeksteki kartı aktif kart olarak yükler. */
    private void loadCardAtIndex(int idx) {
        if (idx < 0 || idx >= mAllCards.size()) return;
        String[] c = mAllCards.get(idx);
        mCardId          = c[0];
        mCardCharacter   = c[1];
        mCardFlavor      = c[2];
        mCardText        = c[3];
        mCardLabelLeft   = c[4];
        mCardLabelRight  = c[5];
        try {
            mCardEffectLeft  = new int[]{ Integer.parseInt(c[6]),  Integer.parseInt(c[7]),
                                          Integer.parseInt(c[8]),  Integer.parseInt(c[9])  };
            mCardEffectRight = new int[]{ Integer.parseInt(c[10]), Integer.parseInt(c[11]),
                                          Integer.parseInt(c[12]), Integer.parseInt(c[13]) };
            mCardAct = Integer.parseInt(c[14]);
        } catch (NumberFormatException e) {
            mCardEffectLeft  = new int[]{0,0,0,0};
            mCardEffectRight = new int[]{0,0,0,0};
        }
    }

    /**
     * Dört stat barını (HALK, DİN, PARA, ORDU) yatay olarak çizer.
     * Her bar GameState'ten bağımsız — şimdilik varsayılan 50 gösterir,
     * ilerleyen entegrasyonda GameState referansı buraya geçirilir.
     */
    private void drawStatBars(float areaY, float areaH) {
        int barCount = BAR_LABELS.length;
        float slotW  = mUsableW / (float) barCount;
        float barH   = Math.max(3f, gPS * 1.2f);
        float labPS  = gPS * 0.52f;
        float barY   = areaY + areaH * 0.55f;
        float labY   = areaY + areaH * 0.05f;

        // Varsayılan değer 50 — GameState entegrasyonunda değiştirilecek
        int[] vals = {50, 50, 50, 50};

        for (int i = 0; i < barCount; i++) {
            float bx = mPad + i * slotW;
            float bw = slotW * 0.82f;

            // Arka plan
            rect(bx, barY, bw, barH, C_DARK);
            // Dolgu
            float fill = (vals[i] / 100f) * bw;
            if (fill > 0) rect(bx, barY, fill, barH, C_GDIM);

            // Etiket
            float lw = charWidth(BAR_LABELS[i], labPS);
            drawString(BAR_LABELS[i], bx + bw / 2f - lw / 2f, labY, labPS, C_GDIM);
        }
    }

    /**
     * Kart kaydırılırken etkilenecek barların üzerinde nokta gösterir.
     * Etki değeri ±0 olan barlar için nokta çizmez.
     *
     * @param goRight true = sağa kaydırma (kabul/SAĞ etkileri), false = sola (SOL etkileri)
     */
    private void drawBarHintDots(boolean goRight, float areaY, float areaH) {
        int[] effects = goRight ? mCardEffectRight : mCardEffectLeft;
        int barCount  = BAR_LABELS.length;
        float slotW   = mUsableW / (float) barCount;
        float barH    = Math.max(3f, gPS * 1.2f);
        float barY    = areaY + areaH * 0.55f;
        float dotR    = Math.max(gPS * 1.4f, 4f);

        for (int i = 0; i < barCount && i < effects.length; i++) {
            if (effects[i] == 0) continue; // etki yoksa nokta çıkma

            float bx       = mPad + i * slotW;
            float bw       = slotW * 0.82f;
            float dotCX    = bx + bw / 2f;
            float dotCY    = barY - dotR * 2.2f;

            // Pozitif etki → altın, negatif → soluk
            float[] dotCol = (effects[i] > 0) ? C_GOLD : C_GDIM;
            // Yanıp sönen efekt
            float alpha = 0.65f + 0.35f * (float) Math.sin(mFrame * 0.18f);
            float[] blinkCol = {dotCol[0], dotCol[1], dotCol[2], alpha};

            rect(dotCX - dotR, dotCY - dotR, dotR * 2f, dotR * 2f, blinkCol);

            // Yön oku (+ / -)
            float labPS  = gPS * 0.44f;
            String sign  = (effects[i] > 0) ? "+" : "-";
            float  signW = charWidth(sign, labPS);
            drawString(sign, dotCX - signW / 2f, dotCY - labPS * 4.5f, labPS, blinkCol);
        }
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

    private void scanLines() {
        float step = Math.max(2f, mH / 400f) * 3f;
        for (float y = 0; y < mH; y += step) {
            rect(0, y, mW, Math.max(1f, step * 0.35f), C_SCAN);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PİXEL FONT  — 5×9 Grid  (Papers Please esintisi)
    // ══════════════════════════════════════════════════════════════════════
    //
    //  Grid: 5 sütun (0-4), 9 satır (0-8)
    //  Harf gövdesi: satır 1-7 arası
    //  İşaret katmanı: satır 0 (üst) veya satır 8 (alt)
    //  Hücre adımı: ps*8  → drawString adımı da 8f
    //
    //  Primitifler (kalınlık = T = 1.5*ps):
    //    pH(row)        → tam yatay bar (x+0 … x+5.5*ps)
    //    pHh(row)       → sağ yarı yatay bar (col2.5 → col5.5)
    //    pV(col,r0,r1)  → dikey bar, T kalınlığında
    //    pD(col,row)    → T×T kare nokta
    //
    //  TÜRKÇE AKSANLAR (tamamı 0-8 satır içinde):
    //    Satır 0 → üst işaret (nokta, breve, umlaut)
    //    Satır 8 → alt işaret (cedilla — satır 7-8 içinde kalır)
    // ══════════════════════════════════════════════════════════════════════

    private float charWidth(String s, float ps) { return s.length() * ps * 8f; }

    private float fitTextPS(String text, float maxW, float maxPS) {
        float needed = charWidth(text, maxPS);
        if (needed <= maxW) return maxPS;
        return maxPS * (maxW / needed);
    }

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
            drawChar(s.charAt(i), cx, y, ps, c);
            cx += ps * 8f;
        }
    }

    /**
     * Türkçe-güvenli büyük harf dönüşümü.
     * Java'nın varsayılan toUpperCase() Türkçe için yanlış sonuç üretir.
     * Bu metod Türkçeye özgü durumları elle yönetir.
     */
    private char trUpper(char ch) {
        switch (ch) {
            case 'i':          return '\u0130'; // i → İ  (noktalı)
            case '\u0131':     return 'I';      // ı → I  (noktasız)
            case '\u00FC':     return '\u00DC'; // ü → Ü
            case '\u00F6':     return '\u00D6'; // ö → Ö
            case '\u00E7':     return '\u00C7'; // ç → Ç
            case '\u015F':     return '\u015E'; // ş → Ş
            case '\u011F':     return '\u011E'; // ğ → Ğ
            default:           return Character.toUpperCase(ch);
        }
    }

    private void drawStringC(String s, float y, float ps, float[] c) {
        float sx = mW / 2f - charWidth(s, ps) / 2f;
        drawString(s, sx, y, ps, c);
    }

    private void drawStringCWrapped(String s, float startY, float ps, float[] c) {
        List<String> lines = wrapText(s, mUsableW * 0.88f, ps);
        float lineH = ps * 11f;
        for (int i = 0; i < lines.size(); i++) {
            drawStringC(lines.get(i), startY + i * lineH, ps, c);
        }
    }

    private void drawChar(char ch, float x, float y, float ps, float[] c) {
        switch (ch) {

            // ── HARFLER ──────────────────────────────────────────────────

            // A: iki bacak (satır 1-7) + üst çatı + orta köprü
            //   bacaklar satır 7'de biter → diğer harflerle aynı hiza
            case 'A':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pH(x,y,ps,1,c);
                pH(x,y,ps,4,c);   // orta köprü tam genişlikte
                break;

            // B: sol dikey + üst/orta/alt yatay + sağ kısa dikey×2
            case 'B':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,4,1,3,c); pV(x,y,ps,4,5,7,c);
                break;

            // C: sol/üst/alt, açık sağ
            case 'C':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c);
                break;

            // D: sol dikey + üst/alt KISA yatay (col0-col3) + sağ orta dikey
            //   pH yerine rect kullanılır → üst/alt barlar col3'te biter
            //   Bu şekilde O'dan net ayrılır: O'da tam pH var, D'de kısa bar+sağ dikey
            case 'D':
                pV(x,y,ps,0,1,7,c);
                rect(x, y+ps*1, ps*3.5f, T(ps), c);   // üst kısa bar (col0→col3)
                rect(x, y+ps*7, ps*3.5f, T(ps), c);   // alt kısa bar (col0→col3)
                pV(x,y,ps,4,2,6,c);                   // sağ orta dikey (köşe değil)
                pV(x,y,ps,3,1,2,c);                   // sağ üst eğim
                pV(x,y,ps,3,6,7,c);                   // sağ alt eğim
                break;

            // E: sol dikey + üst/orta/alt yatay
            case 'E':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                rect(x,y+4*ps, ps*4f,T(ps),c);
                break;

            // F: sol dikey + üst/orta yatay
            case 'F':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,1,c);
                rect(x,y+4*ps, ps*4f,T(ps),c);
                break;

            // G: C + sağda orta çıkıntı
            case 'G':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c);
                pV(x,y,ps,4,5,7,c);
                rect(x+ps*2.5f, y+ps*4, ps*2.5f+T(ps), T(ps), c);
                break;

            // H: iki dikey + orta köprü
            case 'H':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pH(x,y,ps,4,c);
                break;

            // I: üst/alt yatay + merkez dikey
            case 'I':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,2,1,7,c);
                break;

            // J: üst yatay + sağ dikey + sol alt kısa + alt yatay
            case 'J':
                pH(x,y,ps,1,c);
                pV(x,y,ps,4,1,7,c);
                pV(x,y,ps,0,6,7,c);
                pH(x,y,ps,7,c);
                break;

            // K: sol dikey + üst sağ kol (2 segment) + orta bağlantı + alt sağ kol (2 segment)
            //   Üst kol: col2(satır1-2) → col3(satır1) → col4(satır1) ile belirgin
            //   Alt kol: col2(satır5-6) → col3(satır6-7) → col4(satır7) ile simetrik
            case 'K':
                pV(x,y,ps,0,1,7,c);
                // Üst kol: sola yakın → sağa çıkış
                pV(x,y,ps,2,1,2,c); pV(x,y,ps,3,1,1,c);
                // Orta kavuşma
                pV(x,y,ps,1,3,5,c);
                // Alt kol: sol → sağa iniş
                pV(x,y,ps,2,5,6,c); pV(x,y,ps,3,6,7,c);
                break;

            // L: sol dikey + alt yatay
            case 'L':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,7,c);
                break;

            // M: iki dış dikey + iç "V" tepe
            case 'M':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pV(x,y,ps,1,1,3,c);
                pV(x,y,ps,3,1,3,c);
                pV(x,y,ps,2,3,4,c);
                break;

            // N: iki dış dikey + eğik iç çizgi (3 belirgin segment)
            //   İç çapraz col1(satır1-3) + col2(satır3-5) + col3(satır5-7)
            //   Böylece eğri daha kalın ve okunaklı görünür
            case 'N':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pV(x,y,ps,1,1,3,c);
                pV(x,y,ps,2,3,5,c);
                pV(x,y,ps,3,5,7,c);
                break;

            // O: çerçeve
            case 'O':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                break;

            // P: sol dikey + üst yarı kapalı
            case 'P':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,1,c); pH(x,y,ps,4,c);
                pV(x,y,ps,4,1,4,c);
                break;

            // Q: O + sağ alt köşe çıkıntı
            case 'Q':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,6,c);
                pV(x,y,ps,3,6,7,c); pV(x,y,ps,4,7,7,c);
                break;

            // R: P + sağ alt bacak
            case 'R':
                pV(x,y,ps,0,1,7,c);
                pH(x,y,ps,1,c); pH(x,y,ps,4,c);
                pV(x,y,ps,4,1,4,c);
                pV(x,y,ps,3,5,6,c); pV(x,y,ps,4,6,7,c);
                break;

            // S: üst sol + üst/orta/alt yatay + alt sağ
            case 'S':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,4,c);
                pV(x,y,ps,4,4,7,c);
                break;

            // T: üst yatay + merkez dikey
            case 'T':
                pH(x,y,ps,1,c);
                pV(x,y,ps,2,1,7,c);
                break;

            // U: iki dikey + alt yatay
            case 'U':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pH(x,y,ps,7,c);
                break;

            // V: iki dış diagonal → birleşim altta
            case 'V':
                pV(x,y,ps,0,1,4,c); pV(x,y,ps,4,1,4,c);
                pV(x,y,ps,1,5,6,c); pV(x,y,ps,3,5,6,c);
                pV(x,y,ps,2,7,7,c);
                break;

            // W: iki dış dikey + iç "^" alt
            case 'W':
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pV(x,y,ps,1,5,7,c); pV(x,y,ps,3,5,7,c);
                pV(x,y,ps,2,4,5,c);
                break;

            // X: çapraz çift
            case 'X':
                pV(x,y,ps,0,1,2,c); pV(x,y,ps,4,1,2,c);
                pV(x,y,ps,1,3,3,c); pV(x,y,ps,3,3,3,c);
                pV(x,y,ps,2,4,4,c);
                pV(x,y,ps,1,5,5,c); pV(x,y,ps,3,5,5,c);
                pV(x,y,ps,0,6,7,c); pV(x,y,ps,4,6,7,c);
                break;

            // Y: üst çatal + alt merkez
            case 'Y':
                pV(x,y,ps,0,1,3,c); pV(x,y,ps,4,1,3,c);
                pV(x,y,ps,1,4,4,c); pV(x,y,ps,3,4,4,c);
                pV(x,y,ps,2,5,7,c);
                break;

            // Z: üst/alt yatay + ters çapraz
            case 'Z':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,3,2,3,c);
                pV(x,y,ps,2,4,5,c);
                pV(x,y,ps,1,6,7,c);
                pV(x,y,ps,4,1,2,c);
                pV(x,y,ps,0,6,7,c);
                break;

            // ── RAKAMLAR ─────────────────────────────────────────────────

            // 0: O çerçevesi + içinde ters eğik çizgi (O'dan ayırt için)
            //   sol üst → sağ alt yönünde 3 segment
            case '0':
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pV(x,y,ps,1,2,3,c);
                pV(x,y,ps,2,4,4,c);
                pV(x,y,ps,3,5,6,c);
                break;
            case '1':
                pV(x,y,ps,2,1,7,c);
                pH(x,y,ps,7,c);
                pV(x,y,ps,1,2,3,c);
                break;
            case '2':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,4,1,4,c);
                pV(x,y,ps,0,4,7,c);
                break;
            case '3':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,4,1,7,c);
                break;
            case '4':
                pV(x,y,ps,0,1,4,c); pV(x,y,ps,4,1,7,c);
                pH(x,y,ps,4,c);
                break;
            case '5':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,4,c);
                pV(x,y,ps,4,4,7,c);
                break;
            case '6':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c);
                pV(x,y,ps,4,4,7,c);
                break;
            case '7':
                pH(x,y,ps,1,c);
                pV(x,y,ps,4,1,4,c);
                pV(x,y,ps,3,5,6,c);
                pV(x,y,ps,2,7,7,c);
                break;
            case '8':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                break;
            case '9':
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,4,c); pV(x,y,ps,4,1,7,c);
                break;

            // ── NOKTALAMA ────────────────────────────────────────────────

            case '.': pD(x,y,ps,2,7,c); break;
            case ',': pD(x,y,ps,2,7,c); pD(x,y,ps,1,8,c); break;
            case ':': pD(x,y,ps,2,3,c); pD(x,y,ps,2,5,c); break;
            case '!': pV(x,y,ps,2,1,5,c); pD(x,y,ps,2,7,c); break;
            case '?':
                pH(x,y,ps,1,c); pV(x,y,ps,4,1,3,c);
                pV(x,y,ps,2,3,4,c); pV(x,y,ps,3,3,4,c);
                pD(x,y,ps,2,6,c);
                break;
            case '/':
                pV(x,y,ps,4,1,2,c); pV(x,y,ps,3,3,4,c);
                pV(x,y,ps,2,5,6,c); pV(x,y,ps,1,7,7,c);
                break;
            case '-': rect(x+ps*0.5f, y+ps*4, ps*4f, T(ps), c); break;
            case '+':
                rect(x+ps*0.5f, y+ps*4, ps*4f, T(ps), c);
                pV(x,y,ps,2,2,6,c);
                break;
            case '=':
                rect(x+ps*0.5f, y+ps*3, ps*4f, T(ps), c);
                rect(x+ps*0.5f, y+ps*5, ps*4f, T(ps), c);
                break;

            // ── Parantezler ──────────────────────────────────────────────
            // (  : sol dikey col1, üst/alt kısa yatay col1-col0 sola uzanır
            case '(':
                pV(x,y,ps,1,1,7,c);
                rect(x+ps*1f, y+ps*1f, ps*1.5f, T(ps), c);  // üst yatay kısa (col1→col2)
                rect(x+ps*1f, y+ps*7f, ps*1.5f, T(ps), c);  // alt yatay kısa
                pV(x,y,ps,0,2,6,c);                          // sol kısa dikey (iç eğri)
                break;

            // )  : sağ dikey col3, üst/alt kısa yatay col3→col4 sağa uzanır
            case ')':
                pV(x,y,ps,3,1,7,c);
                rect(x+ps*2f, y+ps*1f, ps*1.5f, T(ps), c);  // üst yatay kısa (col2→col3)
                rect(x+ps*2f, y+ps*7f, ps*1.5f, T(ps), c);  // alt yatay kısa
                pV(x,y,ps,4,2,6,c);                          // sağ kısa dikey (iç eğri)
                break;

            case '%':
                pD(x,y,ps,0,1,c); pD(x,y,ps,1,1,c);
                pD(x,y,ps,0,2,c); pD(x,y,ps,1,2,c);
                pV(x,y,ps,3,1,2,c); pV(x,y,ps,2,3,4,c); pV(x,y,ps,1,5,6,c);
                pD(x,y,ps,3,5,c); pD(x,y,ps,4,5,c);
                pD(x,y,ps,3,6,c); pD(x,y,ps,4,6,c);
                break;

            // ── Tırnak işaretleri ─────────────────────────────────────────
            // "  çift tırnak: iki kısa dikey sütun 1 ve 3, satır 1-2
            case '"':
                pV(x,y,ps,1,1,2,c);
                pV(x,y,ps,3,1,2,c);
                break;
            // '  tek tırnak: kısa dikey sütun 2, satır 1-2
            case '\'':
                pV(x,y,ps,2,1,2,c);
                break;

            case ' ': break;

            // ══════════════════════════════════════════════════════════════
            //  TÜRKÇE BÜYÜK HARFLER — 5×9 Grid, satır 0-8 içinde
            // ══════════════════════════════════════════════════════════════

            case '\u00C7': // Ç
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c);
                pD(x,y,ps,2,8,c);
                rect(x+ps*1f, y+ps*8f+T(ps)*0.5f, ps*1.5f, T(ps)*0.6f, c);
                break;

            case '\u011E': // Ğ
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c);
                pV(x,y,ps,4,5,7,c);
                rect(x+ps*2.5f, y+ps*4, ps*2.5f+T(ps), T(ps), c);
                pD(x,y,ps,1,0,c);
                pD(x,y,ps,3,0,c);
                rect(x+ps*2f, y+ps*0f+T(ps)*0.4f, ps*1.5f, T(ps)*0.7f, c);
                break;

            case '\u0130': // İ
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,2,1,7,c);
                pD(x,y,ps,2,0,c);
                break;

            case '\u00D6': // Ö
                pH(x,y,ps,1,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pD(x,y,ps,1,0,c);
                pD(x,y,ps,3,0,c);
                break;

            case '\u015E': // Ş
                pH(x,y,ps,1,c); pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,1,4,c);
                pV(x,y,ps,4,4,7,c);
                pD(x,y,ps,2,8,c);
                rect(x+ps*1f, y+ps*8f+T(ps)*0.5f, ps*1.5f, T(ps)*0.6f, c);
                break;

            case '\u00DC': // Ü
                pV(x,y,ps,0,1,7,c); pV(x,y,ps,4,1,7,c);
                pH(x,y,ps,7,c);
                pD(x,y,ps,1,0,c);
                pD(x,y,ps,3,0,c);
                break;

            // ══════════════════════════════════════════════════════════════
            //  KÜÇÜK HARFLER  — 5×9 Grid
            //
            //  Yükseklik kuralları:
            //    Kısa gövde (a,c,e,m,n,o,r,s,u,v,w,x,z) : satır 3-7
            //    Uzun gövde (b,d,f,h,k,l,t)              : satır 2-7
            //    İnen harf  (g,j,p,q,y)                  : satır 3-7 gövde + satır 7-8 inen
            //
            //  Türkçe küçük harfler (ç,ğ,ı,ö,ş,ü) büyük harflerle aynı
            //  glyph'i paylaşır (zaten trUpper çağrılmıyor, case fall-through ile yönetilir).
            // ══════════════════════════════════════════════════════════════

            // a: kısa O + sağ dikey
            case 'a':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                break;

            // b: uzun sol dikey + alt yarı kapalı (büyük P'nin alt sürümü)
            case 'b':
                pV(x,y,ps,0,2,7,c);
                pH(x,y,ps,4,c); pH(x,y,ps,7,c);
                pV(x,y,ps,4,4,7,c);
                break;

            // c: kısa C
            case 'c':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c);
                break;

            // ç: kısa C + cedilla
            case '\u00E7':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c);
                pD(x,y,ps,2,8,c);
                rect(x+ps*1f, y+ps*8f+T(ps)*0.5f, ps*1.5f, T(ps)*0.6f, c);
                break;

            // d: uzun sağ dikey + üst yarı kapalı (b'nin aynası)
            case 'd':
                pV(x,y,ps,4,2,7,c);
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c);
                break;

            // e: kısa O + orta çizgi + sağ üst açık
            case 'e':
                pH(x,y,ps,3,c); pH(x,y,ps,5,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c);
                pV(x,y,ps,4,3,5,c);
                break;

            // f: uzun sol dikey + üst yatay + orta yatay kısa
            case 'f':
                pV(x,y,ps,1,2,7,c);
                pH(x,y,ps,2,c);
                rect(x+ps*1f, y+ps*4, ps*3f, T(ps), c);
                break;

            // g: inen harf — kısa O gövde + sağ dikey iner, alt yatay
            case 'g':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,8,c);
                pH(x,y,ps,8,c);  // alt yatay (inen kısım)
                break;

            // ğ: g + üst breve
            case '\u011F':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,8,c);
                pH(x,y,ps,8,c);
                pD(x,y,ps,1,2,c); pD(x,y,ps,3,2,c);
                rect(x+ps*2f, y+ps*2f+T(ps)*0.4f, ps*1.5f, T(ps)*0.7f, c);
                break;

            // h: uzun sol dikey + sağ kısa (satır 4-7)
            case 'h':
                pV(x,y,ps,0,2,7,c);
                pH(x,y,ps,4,c);
                pV(x,y,ps,4,4,7,c);
                break;

            // ı: noktasız i — kısa merkez dikey
            case '\u0131':
                pV(x,y,ps,2,3,7,c);
                break;

            // i: noktalı i — kısa merkez dikey + nokta
            case 'i':
                pV(x,y,ps,2,3,7,c);
                pD(x,y,ps,2,2,c);  // nokta satır 2'de (gövdenin hemen üstü)
                break;

            // j: inen harf — sağ kısa dikey + nokta + inen sol kanca
            case 'j':
                pV(x,y,ps,3,3,7,c);
                pD(x,y,ps,3,2,c);
                pV(x,y,ps,0,7,8,c);
                rect(x+ps*1f, y+ps*8f, ps*2f, T(ps), c);
                break;

            // k: uzun sol dikey + kollar (küçük versiyon)
            case 'k':
                pV(x,y,ps,0,2,7,c);
                pV(x,y,ps,2,4,5,c);
                pV(x,y,ps,3,3,4,c); pV(x,y,ps,4,3,3,c);
                pV(x,y,ps,3,5,6,c); pV(x,y,ps,4,7,7,c);
                break;

            // l: uzun ince dikey
            case 'l':
                pV(x,y,ps,2,2,7,c);
                break;

            // m: kısa iki tepe (M'nin küçüğü, satır 3-7)
            case 'm':
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pV(x,y,ps,1,3,5,c); pV(x,y,ps,3,3,5,c);
                pV(x,y,ps,2,5,7,c);
                break;

            // n: iki kısa dikey + üst köprü
            case 'n':
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pH(x,y,ps,3,c);
                break;

            // o: kısa O çerçeve
            case 'o':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                break;

            // ö: kısa O + umlaut
            case '\u00F6':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pD(x,y,ps,1,2,c); pD(x,y,ps,3,2,c);
                break;

            // p: inen harf — uzun sol dikey (iner) + üst yarı kapalı
            case 'p':
                pV(x,y,ps,0,3,8,c);
                pH(x,y,ps,3,c); pH(x,y,ps,6,c);
                pV(x,y,ps,4,3,6,c);
                break;

            // q: inen harf — uzun sağ dikey (iner) + üst yarı kapalı
            case 'q':
                pV(x,y,ps,4,3,8,c);
                pH(x,y,ps,3,c); pH(x,y,ps,6,c);
                pV(x,y,ps,0,3,6,c);
                break;

            // r: kısa sol dikey + üst sağ çıkıntı
            case 'r':
                pV(x,y,ps,0,3,7,c);
                pH(x,y,ps,3,c);
                pV(x,y,ps,3,3,4,c);
                break;

            // s: kısa S
            case 's':
                pH(x,y,ps,3,c); pH(x,y,ps,5,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,5,c);
                pV(x,y,ps,4,5,7,c);
                break;

            // ş: kısa S + cedilla
            case '\u015F':
                pH(x,y,ps,3,c); pH(x,y,ps,5,c); pH(x,y,ps,7,c);
                pV(x,y,ps,0,3,5,c);
                pV(x,y,ps,4,5,7,c);
                pD(x,y,ps,2,8,c);
                rect(x+ps*1f, y+ps*8f+T(ps)*0.5f, ps*1.5f, T(ps)*0.6f, c);
                break;

            // t: uzun merkez dikey + üst yatay kısa
            case 't':
                pV(x,y,ps,2,2,7,c);
                rect(x+ps*1f, y+ps*3, ps*3f, T(ps), c);
                break;

            // u: iki kısa dikey + alt yatay
            case 'u':
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pH(x,y,ps,7,c);
                break;

            // ü: u + umlaut
            case '\u00FC':
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pH(x,y,ps,7,c);
                pD(x,y,ps,1,2,c); pD(x,y,ps,3,2,c);
                break;

            // v: kısa V
            case 'v':
                pV(x,y,ps,0,3,5,c); pV(x,y,ps,4,3,5,c);
                pV(x,y,ps,1,6,6,c); pV(x,y,ps,3,6,6,c);
                pV(x,y,ps,2,7,7,c);
                break;

            // w: kısa W
            case 'w':
                pV(x,y,ps,0,3,7,c); pV(x,y,ps,4,3,7,c);
                pV(x,y,ps,1,5,7,c); pV(x,y,ps,3,5,7,c);
                pV(x,y,ps,2,4,5,c);
                break;

            // x: kısa çapraz çift
            case 'x':
                pV(x,y,ps,0,3,4,c); pV(x,y,ps,4,3,4,c);
                pV(x,y,ps,1,4,5,c); pV(x,y,ps,3,4,5,c);
                pV(x,y,ps,2,5,5,c);
                pV(x,y,ps,1,5,6,c); pV(x,y,ps,3,5,6,c);
                pV(x,y,ps,0,6,7,c); pV(x,y,ps,4,6,7,c);
                break;

            // y: inen harf — üst çatal + inen merkez
            case 'y':
                pV(x,y,ps,0,3,5,c); pV(x,y,ps,4,3,5,c);
                pV(x,y,ps,1,5,6,c); pV(x,y,ps,3,5,6,c);
                pV(x,y,ps,2,6,8,c);
                break;

            // z: kısa Z
            case 'z':
                pH(x,y,ps,3,c); pH(x,y,ps,7,c);
                pV(x,y,ps,3,4,5,c);
                pV(x,y,ps,2,5,6,c);
                pV(x,y,ps,1,6,7,c);
                pV(x,y,ps,4,3,4,c);
                pV(x,y,ps,0,6,7,c);
                break;

            // Bilinmeyen karakter → merkez nokta
            default: pD(x,y,ps,2,4,c); break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT PRİMİTİFLERİ
    // ══════════════════════════════════════════════════════════════════════

    /** Çizgi kalınlığı = 1.5 × ps */
    private float T(float ps) { return ps * 1.5f; }

    /**
     * pH  — Tam yatay bar, satır 'row', soldan sağa tam genişlik (5.5*ps)
     * Grid col0'dan col4+T'ye uzanır → dikey barlarla köşe örtüşümü tam.
     */
    private void pH(float x, float y, float ps, int row, float[] c) {
        rect(x, y + row * ps, ps * 5.5f, T(ps), c);
    }

    /**
     * pHh — Sağ yarı yatay bar (col2.5'ten col4+T'ye), G ve benzeri için
     */
    private void pHh(float x, float y, float ps, int row, float[] c) {
        rect(x + ps * 2.5f, y + row * ps, ps * 3f, T(ps), c);
    }

    /**
     * pV  — Dikey bar, sütun 'col', satır r0'dan r1'e (dahil), kalınlık T
     */
    private void pV(float x, float y, float ps, int col, int r0, int r1, float[] c) {
        rect(x + col * ps, y + r0 * ps, T(ps), (r1 - r0 + 1) * ps, c);
    }

    /**
     * pD  — T×T kare nokta, sütun col, satır row
     */
    private void pD(float x, float y, float ps, int col, int row, float[] c) {
        rect(x + col * ps, y + row * ps, T(ps), T(ps), c);
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
        float b = Math.max(1.5f, gPS * 0.4f);
        rect(x,     y,     w, b,   brd);
        rect(x,     y+h-b, w, b,   brd);
        rect(x,     y,     b, h,   brd);
        rect(x+w-b, y,     b, h,   brd);

        float ps = fitTextPS(label, w * 0.78f, gPS * 0.80f);
        float tw = charWidth(label, ps);
        drawString(label, x + w/2f - tw/2f, y + h/2f - 4.5f*ps, ps, txt);
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
    //  KART YÜKLEYICI — assets/scenario/opposition_1/cards.json
    // ══════════════════════════════════════════════════════════════════════

    /**
     * cards.json'ı parse eder. Android'in kendi JSON kütüphanesini kullanır
     * (org.json — dış bağımlılık gerekmez, API 1'den beri mevcut).
     *
     * Her kart mAllCards listesine String[15] olarak eklenir:
     * [0]=id  [1]=character  [2]=flavor  [3]=text  [4]=labelL  [5]=labelR
     * [6]=halkL [7]=dinL [8]=paraL [9]=orduL
     * [10]=halkR [11]=dinR [12]=paraR [13]=orduR
     * [14]=act
     */
    private void loadCardsFromJson() {
        final String PATH = "scenario/opposition_1/cards.json";
        try {
            InputStream is = mAssets.open(PATH);
            byte[] buf = new byte[is.available()];
            //noinspection ResultOfMethodCallIgnored
            is.read(buf);
            is.close();
            String raw = new String(buf, "UTF-8");

            org.json.JSONObject root  = new org.json.JSONObject(raw);
            org.json.JSONArray  cards = root.getJSONArray("cards");

            mAllCards.clear();
            for (int i = 0; i < cards.length(); i++) {
                org.json.JSONObject card = cards.getJSONObject(i);
                org.json.JSONObject cl   = card.getJSONObject("choiceLeft");
                org.json.JSONObject cr   = card.getJSONObject("choiceRight");

                String[] entry = new String[15];
                entry[0]  = card.optString("id",        "");
                entry[1]  = card.optString("character", "");
                entry[2]  = card.optString("flavor",    "");
                entry[3]  = card.optString("text",      "");
                entry[4]  = cl.optString("label",  "HAYIR");
                entry[5]  = cr.optString("label",  "EVET");
                entry[6]  = String.valueOf(cl.optInt("halk", 0));
                entry[7]  = String.valueOf(cl.optInt("din",  0));
                entry[8]  = String.valueOf(cl.optInt("para", 0));
                entry[9]  = String.valueOf(cl.optInt("ordu", 0));
                entry[10] = String.valueOf(cr.optInt("halk", 0));
                entry[11] = String.valueOf(cr.optInt("din",  0));
                entry[12] = String.valueOf(cr.optInt("para", 0));
                entry[13] = String.valueOf(cr.optInt("ordu", 0));
                entry[14] = String.valueOf(card.optInt("act", 1));
                mAllCards.add(entry);
            }

            // İlk kartı aktif olarak yükle
            if (!mAllCards.isEmpty()) loadCardAtIndex(0);

        } catch (Exception e) {
            // JSON yoksa veya parse hatası — kart metni boş kalır
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INTRO YÜKLEYICI — src/scenario/Opposition_1/intro.md
    // ══════════════════════════════════════════════════════════════════════

    /**
     * intro.md dosyasını assets'ten okur; INTRO-N bloklarını parse eder.
     * Her blok için:
     *   - `bg: story_bg_N.png` satırından asset adını çıkarır
     *   - Altındaki metin satırlarını birleştirip sahne metni olarak saklar
     *
     * Dosya okunamazsa veya parse başarısız olursa diziler boş kalır,
     * sinematik ekranı sessizce atlanır.
     */
    private void loadIntroFromAssets() {
        final String PATH = "src/scenario/Opposition_1/intro.md";
        try {
            InputStream is = mAssets.open(PATH);
            byte[] buf = new byte[is.available()];
            //noinspection ResultOfMethodCallIgnored
            is.read(buf);
            is.close();
            String raw = new String(buf, "UTF-8");

            List<String> texts  = new ArrayList<>();
            List<String> assets = new ArrayList<>();

            // Blok ayırıcı: "---" satırları
            // Her blok **[INTRO-N]** ile başlar, `bg:` satırı içerir,
            // ardından boş satır, sonra sahne metni gelir.
            String[] blocks = raw.split("(?m)^---\\s*$");
            for (String block : blocks) {
                block = block.trim();
                if (!block.contains("[INTRO-")) continue;

                String bgAsset  = "";
                StringBuilder sceneTxt = new StringBuilder();
                boolean inText  = false;

                for (String line : block.split("\n")) {
                    String trimmed = line.trim();

                    if (trimmed.startsWith("`bg:")) {
                        // `bg: story_bg_1.png`
                        bgAsset = trimmed.replace("`bg:", "").replace("`", "").trim();
                        inText  = false;
                        continue;
                    }
                    if (trimmed.startsWith("**[INTRO-")) {
                        inText = false;
                        continue;
                    }
                    // # başlıkları ve boş satırları atla
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                        if (!sceneTxt.toString().isEmpty()) inText = true;
                        continue;
                    }
                    // bg satırı geçildikten sonra gelen satırlar sahne metni
                    if (!bgAsset.isEmpty()) {
                        if (sceneTxt.length() > 0) sceneTxt.append("\n");
                        sceneTxt.append(trimmed);
                    }
                }

                if (!bgAsset.isEmpty() && sceneTxt.length() > 0) {
                    assets.add(bgAsset);
                    texts.add(sceneTxt.toString());
                }
            }

            mCinemaText   = texts.toArray(new String[0]);
            mCinemaAssets = assets.toArray(new String[0]);

        } catch (Exception e) {
            // Dosya yoksa veya parse hatası — diziler boş kalır, sinematik atlanır
            mCinemaText   = new String[0];
            mCinemaAssets = new String[0];
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YARDIMCI
    // ══════════════════════════════════════════════════════════════════════

    private boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px>=rx && px<=rx+rw && py>=ry && py<=ry+rh;
    }
}

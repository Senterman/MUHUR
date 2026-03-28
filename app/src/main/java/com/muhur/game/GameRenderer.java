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
 * MÜHÜR — GameRenderer (Revizyon 8)
 *
 * Değişiklikler (Rev 8):
 *   1. BUILD HATASI DÜZELTMESİ:
 *      - mTouchAction, mTouchX, mTouchY class seviyesinde tanımlandı (zaten Rev7'de var, kontrol edildi ✓)
 *      - android.view.MotionEvent import'u eklendi ✓
 *   2. Parti bilgisi GameState.getPartyDisplayName() ve getScenarioFolder() üzerinden alınıyor.
 *      Ekran metinlerinde "HSP" yerine seçilen partinin adı gösteriliyor.
 *   3. PARTY_NAMES: "HALK SEVER PARTISI" → "HALKCI SECIM PARTISI" düzeltmesi.
 *      PARTY_SLOGANS güncellendi.
 *   4. startGameWithParty(int): mGameState.partyChoice = party set edildi, ardından
 *      getScenarioFolder() ile path oluşturuluyor — tutarsızlık giderildi.
 *   5. renderElection() sonuç metni artık parti adını dinamik olarak içeriyor.
 *   6. Sinematik → ST_PARTY_SELECT geçişi düzeltildi (ST_FONTTEST araya giriyordu).
 *   7. mTexPartyBg: parti_seçim_bg.png yükleniyor.
 *   8. renderGame() içinde "MENÜ" butonu konumu düzeltildi.
 *
 * Korunan (DEĞİŞMEYEN):
 *   - Tüm shader, texture, scanLines, CRT sistemi
 *   - Piksel font sistemi (tüm Türkçe karakterler)
 *   - Splash, Menü, Ayarlar, Sinematik, FontTest render + touch metodları
 *   - MusicController interface
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ══════════════════════════════════════════════════════════════════════
    //  MUSIC CONTROLLER
    // ══════════════════════════════════════════════════════════════════════

    public interface MusicController {
        void onMusicStart();
        void onVolumeChanged(float volume);
        float getVolume();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STATE SABİTLERİ
    // ══════════════════════════════════════════════════════════════════════

    private static final int ST_SPLASH       = 0;
    private static final int ST_MENU         = 1;
    private static final int ST_CINEMA       = 2;
    private static final int ST_SETTINGS     = 3;
    private static final int ST_GAME         = 4;
    private static final int ST_FONTTEST     = 5;
    private static final int ST_ENDING       = 6;
    private static final int ST_ELECTION     = 7;
    private static final int ST_PARTY_SELECT = 8;

    // ══════════════════════════════════════════════════════════════════════
    //  RENK PALETİ
    // ══════════════════════════════════════════════════════════════════════

    private static final float[] C_BG     = {0.051f, 0.039f, 0.020f, 1.0f};
    private static final float[] C_GOLD   = {0.831f, 0.659f, 0.325f, 1.0f};
    private static final float[] C_GDIM   = {0.420f, 0.330f, 0.160f, 1.0f};
    private static final float[] C_DARK   = {0.090f, 0.070f, 0.035f, 1.0f};
    private static final float[] C_PRES   = {0.180f, 0.140f, 0.070f, 1.0f};
    private static final float[] C_GREY   = {0.260f, 0.240f, 0.220f, 1.0f};
    private static final float[] C_SCAN   = {0.000f, 0.000f, 0.000f, 0.15f};
    private static final float[] C_WARN   = {0.820f, 0.720f, 0.100f, 1.0f};
    private static final float[] C_DANGER = {0.820f, 0.200f, 0.150f, 1.0f};

    // ══════════════════════════════════════════════════════════════════════
    //  PARTİ VERİSİ
    // Rev 8: Doğru parti adları ve sloganlar
    // ══════════════════════════════════════════════════════════════════════

    /** GameState.PARTY_HSP/MNP/LDP index sırasıyla aynı */
    private static final String[] PARTY_IDS   = { "HSP", "MNP", "LDP" };
    private static final String[] PARTY_NAMES = {
        "HALKCI SECIM PARTISI",    // Halkçı Seçim Partisi
        "MILLI NIZAM PARTISI",     // Milli Nizam Partisi
        "LIBERAL DEMOKRAT PARTI"   // Liberal Demokrat Parti
    };
    private static final String[] PARTY_SLOGANS = {
        "SESINIZI DUYURUYORUZ.",
        "DUZEN VE ISTIKRAR.",
        "OZGURLUK, KALKINMA, REFAH."
    };

    // ══════════════════════════════════════════════════════════════════════
    //  SHADER KAYNAK KODLARI
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    //  GL HANDLES
    // ══════════════════════════════════════════════════════════════════════

    private int mProgCol, mLocAPos, mLocURes, mLocUColor;
    private int mProgTex, mLocTAPos, mLocTAUV, mLocTURes, mLocTUTex, mLocTUAlpha;
    private FloatBuffer mVtxBuf;

    // ══════════════════════════════════════════════════════════════════════
    //  TEXTURE ID'LERİ
    // ══════════════════════════════════════════════════════════════════════

    private int   mTexLogo      = -1;
    private int   mTexMenuBg    = -1;
    private int   mTexPartyBg   = -1;  // party_select_bg.png
    private int[] mTexCinema    = new int[0];

    // ══════════════════════════════════════════════════════════════════════
    //  EKRAN & FONT
    // ══════════════════════════════════════════════════════════════════════

    private float mW, mH, mPad, mUsableW;
    private float gPS;

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN DURUMU
    // ══════════════════════════════════════════════════════════════════════

    private int   mState = ST_SPLASH;
    private int   mFrame = 0;

    // Splash
    private float   mSplashAlpha = 0f;
    private static final int SPLASH_FRAMES = 120;

    // Sinematik
    private String[] mCinemaText   = new String[0];
    private String[] mCinemaAssets = new String[0];
    private int      mCinemaScene  = 0;
    private int      mCinemaChar   = 0;
    private long     mCinemaLast   = 0;
    private boolean  mCinemaDone   = false;
    private boolean  mSkipHeld     = false;
    private long     mSkipStart    = 0;
    private static final long TYPEWRITER_MS = 55;
    private static final long SKIP_HOLD_MS  = 3000;

    // Menü
    private int mMenuHover = -1, mMenuPress = -1;

    // Ayarlar
    private int mSetHover = -1, mSetPress = -1;

    // Ses / FPS
    private float   mVolume         = 0.75f;
    private boolean mSliderDragging = false;
    private int     mFps            = 60;
    private boolean mMusicStarted   = false;

    // Font Test
    private static final String[] FONT_TEST_LINES = {
        "ABCC\u00C7DEFGG\u011EHHII\u0130",
        "JKLMNOO\u00D6PRSS\u015ETUU\u00DCVYZ",
        "abc\u00E7defg\u011Fh\u0131ijklmn",
        "oo\u00F6prs\u015Ftuu\u00FCvyz",
        "0123456789",
        ". , ! ? : ( ) \" ' - + = /"
    };
    private int     mFtChar = 0;
    private long    mFtLast = 0;
    private boolean mFtDone = false;
    private static final long FT_CHAR_MS = 40;

    // Kart swipe
    private float   mCardSwipeX      = 0f;
    private boolean mCardSwiping     = false;
    private float   mCardSwipeStartX = 0f;
    private int[]   mCardEffectLeft  = {0, 0, 0, 0};
    private int[]   mCardEffectRight = {0, 0, 0, 0};
    private static final String[] BAR_LABELS = {"HALK", "DIN", "PARA", "ORDU"};

    // Senaryo & GameState
    private final ScenarioEngine mEngine;
    private final GameState      mGameState;

    // Felaket sonu (ST_ENDING)
    private String  mEndingTitle  = "";
    private String  mEndingBody   = "";
    private int     mEndingChar   = 0;
    private long    mEndingLast   = 0;
    private boolean mEndingDone   = false;
    private static final long ENDING_CHAR_MS = 35;

    // Seçim (ST_ELECTION)
    private int mElectionResult = -1;
    private int mElectionScore  = 0;

    // ── TOUCH DURUMU (class seviyesinde — build hatası buradan geliyordu) ─
    private int   mTouchAction = -1;
    private float mTouchX      = 0f;
    private float mTouchY      = 0f;

    // Parti seçim ekranı
    private int mPartyHover = -1;
    private int mPartyPress = -1;

    // Context & Müzik
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

        mGameState = new GameState();
        mEngine    = new ScenarioEngine(mAssets);

        // Varsayılan yükleme (gerçek yükleme startGameWithParty() içinde)
        mEngine.loadScenario("scenario/HSP/cards.json");
        mEngine.loadEndings("scenario/HSP/endings.json");

        loadIntroFromAssets();

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
        mLocAPos   = GLES20.glGetAttribLocation (mProgCol, "aPos");
        mLocURes   = GLES20.glGetUniformLocation(mProgCol, "uRes");
        mLocUColor = GLES20.glGetUniformLocation(mProgCol, "uColor");

        mProgTex    = buildProgram(VERT_TEX_SRC, FRAG_TEX_SRC);
        mLocTAPos   = GLES20.glGetAttribLocation (mProgTex, "aPos");
        mLocTAUV    = GLES20.glGetAttribLocation (mProgTex, "aUV");
        mLocTURes   = GLES20.glGetUniformLocation(mProgTex, "uRes");
        mLocTUTex   = GLES20.glGetUniformLocation(mProgTex, "uTex");
        mLocTUAlpha = GLES20.glGetUniformLocation(mProgTex, "uAlpha");

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mTexLogo    = loadTexture("logo.png");
        mTexMenuBg  = loadTexture("menu_bg.png");
        mTexPartyBg = loadTexture("party_select_bg.png");

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
        mTouchX = x;
        mTouchY = y;
        switch (mState) {
            case ST_SPLASH:       touchSplash(action);              break;
            case ST_CINEMA:       touchCinema(action, x, y, time);  break;
            case ST_MENU:         touchMenu(action, x, y);          break;
            case ST_SETTINGS:     touchSettings(action, x, y);      break;
            case ST_FONTTEST:     touchFontTest(action, x, y);      break;
            case ST_GAME:         touchGame(action, x, y);          break;
            case ST_ENDING:       touchEnding(action);              break;
            case ST_ELECTION:     touchElection(action, x, y);      break;
            case ST_PARTY_SELECT: touchPartySelect(action, x, y);   break;
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
            case ST_ENDING:   updateEnding();   break;
        }
    }

    private void updateSplash() {
        if      (mFrame < 30)                 mSplashAlpha = mFrame / 30f;
        else if (mFrame > SPLASH_FRAMES - 30) mSplashAlpha = (SPLASH_FRAMES - mFrame) / 30f;
        else                                  mSplashAlpha = 1f;
        if (mFrame >= SPLASH_FRAMES) {
            mFrame = 0;
            mState = ST_MENU;
            mMenuHover = mMenuPress = -1;
            triggerMusicStart();
        }
    }

    private void updateCinema() {
        if (mCinemaDone || mCinemaText.length == 0) return;
        long now = System.currentTimeMillis();
        if (now - mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast = now;
            String text = mCinemaText[mCinemaScene];
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
            case ST_SPLASH:       renderSplash();          break;
            case ST_MENU:         renderMenu();             break;
            case ST_CINEMA:       renderCinema();           break;
            case ST_SETTINGS:     renderSettings();         break;
            case ST_FONTTEST:     renderFontTest();         break;
            case ST_GAME:         renderGame();             break;
            case ST_ENDING:       renderEnding();           break;
            case ST_ELECTION:     renderElection();         break;
            case ST_PARTY_SELECT: renderPartySelection();   break;
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
        drawStringC("BONECASTOFFICIAL", cy + mH*0.09f, ps, col);
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

    private static final String[] MENU_LABELS = {"YENI OYUN", "DEVAM ET", "AYARLAR", "CIKIS"};

    private void renderMenu() {
        if (mTexMenuBg > 0) {
            drawTex(mTexMenuBg, 0, 0, mW, mH, 0.72f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.50f};
            rect(0, 0, mW, mH, ov);
        }
        float cx = mW / 2f;
        float titlePS = fitTextPS("MUHUR", mUsableW * 0.80f, gPS * 2.0f);
        drawStringC("MUHUR", mH * 0.16f, titlePS, C_GOLD);
        drawStringCWrapped("KADERİN, MUHRURUN UCUNDA.", mH * 0.29f, gPS * 0.68f, C_GDIM);
        float devPS = fitTextPS("BONECASTOFFICIAL", mUsableW * 0.68f, gPS * 0.58f);
        drawStringC("BONECASTOFFICIAL", mH * 0.36f, devPS, C_GREY);
        float bw = mUsableW * 0.72f, bh = Math.max(gPS * 8.5f, mH * 0.065f);
        float bx = cx - bw/2f, gap = bh + gPS * 2.5f, startY = mH * 0.46f;
        for (int i = 0; i < MENU_LABELS.length; i++) {
            drawButton(MENU_LABELS[i], bx, startY + i * gap, bw, bh,
                    mMenuHover == i, mMenuPress == i, i == 1);
        }
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.45f};
        drawString("v0.2.0", mPad, mH - gPS * 5f, gPS * 0.62f, greyA);
        scanLines();
    }

    private void touchMenu(int action, float x, float y) {
        float cx = mW/2f, bw = mUsableW*0.72f, bh = Math.max(gPS*8.5f, mH*0.065f);
        float bx = cx-bw/2f, gap = bh+gPS*2.5f, startY = mH*0.46f;
        if (action == MotionEvent.ACTION_DOWN) {
            mMenuPress = -1;
            for (int i = 0; i < MENU_LABELS.length; i++) {
                if (hit(x, y, bx, startY+i*gap, bw, bh)) { mMenuPress=i; mMenuHover=i; return; }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            mMenuHover = -1;
            for (int i = 0; i < MENU_LABELS.length; i++) {
                if (hit(x, y, bx, startY+i*gap, bw, bh)) { mMenuHover=i; break; }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            int p = mMenuPress; mMenuPress=-1; mMenuHover=-1;
            if (p < 0 || !hit(x, y, bx, startY+p*gap, bw, bh)) return;
            switch (p) {
                case 0: startCinema(); break;
                case 1: break;
                case 2: mSetHover=mSetPress=-1; mState=ST_SETTINGS; break;
                case 3:
                    if (mCtx instanceof android.app.Activity)
                        ((android.app.Activity)mCtx).finish();
                    break;
            }
        }
    }

    private void startCinema() {
        mCinemaScene=0; mCinemaChar=0; mCinemaDone=false;
        mCinemaLast=System.currentTimeMillis(); mSkipHeld=false;
        mState = ST_CINEMA;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SİNEMATİK
    // ══════════════════════════════════════════════════════════════════════

    private void renderCinema() {
        if (mCinemaText.length == 0) { startPartySelect(); return; }
        int bg = (mCinemaScene < mTexCinema.length) ? mTexCinema[mCinemaScene] : -1;
        if (bg > 0) {
            drawTex(bg, 0, 0, mW, mH, 0.82f);
            float[] ov = {C_BG[0], C_BG[1], C_BG[2], 0.52f};
            rect(0, 0, mW, mH, ov);
        }
        float snPS = gPS*0.72f;
        float[] greyA = {C_GREY[0], C_GREY[1], C_GREY[2], 0.75f};
        String scn = (mCinemaScene+1)+"/"+mCinemaText.length;
        drawString(scn, mW-mPad-charWidth(scn, snPS), mPad*1.5f, snPS, greyA);
        String full = mCinemaText[mCinemaScene];
        String vis  = full.substring(0, Math.min(mCinemaChar, full.length()));
        float textPS=gPS*0.90f, maxW=mUsableW*0.86f;
        List<String> lines = wrapText(vis, maxW, textPS);
        float lineH=textPS*11f, totalH=lines.size()*lineH, textY=mH*0.52f-totalH/2f;
        for (int i = 0; i < lines.size(); i++) drawStringC(lines.get(i), textY+i*lineH, textPS, C_GOLD);
        if (!mCinemaDone && (mFrame/15)%2==0) {
            String last = lines.isEmpty()?"":lines.get(lines.size()-1);
            rect(mW/2f+charWidth(last,textPS)/2f+textPS, textY+(lines.size()-1)*lineH,
                 textPS, textPS*8f, C_GOLD);
        }
        if (mCinemaDone) {
            float bw=mUsableW*0.42f, bh=Math.max(gPS*9f,mH*0.06f);
            drawButton("DEVAM", mW/2f-bw/2f, mH*0.82f, bw, bh, mSetHover==99, false, false);
        }
        float sw=mUsableW*0.30f, sh=Math.max(gPS*7f,mH*0.05f);
        float sx=mPad, sy=mH-sh-mPad*2f;
        drawButton("ATLA", sx, sy, sw, sh, false, mSkipHeld, false);
        if (mSkipHeld) {
            long held = System.currentTimeMillis()-mSkipStart;
            float prog = Math.min(1f, held/(float)SKIP_HOLD_MS);
            rect(sx, sy+sh+gPS*0.5f, sw*prog, gPS*1.2f, C_GOLD);
            if (held >= SKIP_HOLD_MS) skipAllCinema();
        }
        scanLines();
    }

    private void touchCinema(int action, float x, float y, long time) {
        float bw=mUsableW*0.42f, bh=Math.max(gPS*9f,mH*0.06f);
        float bx=mW/2f-bw/2f, by=mH*0.82f;
        float sw=mUsableW*0.30f, sh=Math.max(gPS*7f,mH*0.05f);
        float sx=mPad, sy=mH-sh-mPad*2f;
        if (action == MotionEvent.ACTION_DOWN) {
            if (hit(x,y,sx,sy,sw,sh)) { mSkipHeld=true; mSkipStart=System.currentTimeMillis(); return; }
            if (!mCinemaDone) { mCinemaChar=mCinemaText[mCinemaScene].length(); mCinemaDone=true; return; }
            if (hit(x,y,bx,by,bw,bh)) nextCinemaScene();
        } else if (action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) {
            mSkipHeld=false; mSetHover=-1;
        }
    }

    private void nextCinemaScene() {
        mCinemaScene++;
        if (mCinemaScene >= mCinemaText.length) { startPartySelect(); }
        else { mCinemaChar=0; mCinemaDone=false; mCinemaLast=System.currentTimeMillis(); }
    }

    private void skipAllCinema() {
        mSkipHeld=false; startPartySelect();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AYARLAR
    // ══════════════════════════════════════════════════════════════════════

    private float sliderX() { return mW/2f-sliderW()/2f; }
    private float sliderW() { return mUsableW*0.85f; }
    private float sliderY() { return mH*0.42f; }
    private float sliderH() { return Math.max(gPS*3f, 6f); }
    private float thumbR()  { return Math.max(gPS*4.5f, 10f); }
    private float volToThumbX() { return sliderX()+mVolume*sliderW(); }

    private void renderSettings() {
        if (mTexMenuBg > 0) { drawTex(mTexMenuBg,0,0,mW,mH,0.5f); float[] ov={C_BG[0],C_BG[1],C_BG[2],0.65f}; rect(0,0,mW,mH,ov); }
        float cx=mW/2f;
        float titlePS=fitTextPS("AYARLAR",mUsableW*0.60f,gPS*1.6f);
        drawStringC("AYARLAR", mH*0.14f, titlePS, C_GOLD);
        float labPS=gPS*0.72f, labY=mH*0.34f;
        drawString("SES SEVIYESI:", mPad, labY, labPS, C_GOLD);
        int pct=Math.round(mVolume*100f); String pctStr="%"+pct;
        float pctPS=fitTextPS(pctStr,mUsableW*0.18f,labPS);
        drawString(pctStr, mW-mPad-charWidth(pctStr,pctPS), labY, pctPS, C_GOLD);
        float sx=sliderX(),sw=sliderW(),sy=sliderY(),sh=sliderH(),tr=thumbR();
        float thumbCX=volToThumbX(), brd=Math.max(1.5f,gPS*0.3f);
        rect(sx,sy,sw,sh,C_DARK); if (mVolume>0) rect(sx,sy,mVolume*sw,sh,C_GOLD);
        rect(sx,sy,sw,brd,C_GDIM); rect(sx,sy+sh-brd,sw,brd,C_GDIM);
        rect(sx,sy,brd,sh,C_GDIM); rect(sx+sw-brd,sy,brd,sh,C_GDIM);
        float[] tCol=mSliderDragging?C_GOLD:C_GDIM;
        rect(thumbCX-tr,sy+sh/2f-tr,tr*2f,tr*2f,tCol);
        float togW=mUsableW*0.28f,togH=Math.max(gPS*9f,mH*0.060f);
        float togX0=cx-togW-mPad*0.5f,togX1=cx+mPad*0.5f,row2Y=mH*0.56f;
        drawString("FPS:", mPad, row2Y, labPS, C_GOLD);
        drawButton("30",togX0,row2Y,togW,togH,mSetHover==2,mSetPress==2,mFps!=30);
        drawButton("60",togX1,row2Y,togW,togH,mSetHover==3,mSetPress==3,mFps!=60);
        float backW=mUsableW*0.45f,backH=Math.max(gPS*9f,mH*0.060f);
        drawButton("GERI",cx-backW/2f,mH*0.76f,backW,backH,mSetHover==10,mSetPress==10,false);
        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx=mW/2f,togW=mUsableW*0.28f,togH=Math.max(gPS*9f,mH*0.060f);
        float togX0=cx-togW-mPad*0.5f,togX1=cx+mPad*0.5f,row2Y=mH*0.56f;
        float backW=mUsableW*0.45f,backH=Math.max(gPS*9f,mH*0.060f);
        float backX=cx-backW/2f,backY=mH*0.76f;
        float sx=sliderX(),sw=sliderW(),sy=sliderY(),sh=sliderH(),tr=thumbR();
        float thumbCX=volToThumbX(),hitPad=tr*1.4f;
        if (action==MotionEvent.ACTION_DOWN) {
            if (x>=thumbCX-hitPad&&x<=thumbCX+hitPad&&y>=sy+sh/2f-hitPad&&y<=sy+sh/2f+hitPad) { mSliderDragging=true; updateSliderFromX(x,sx,sw); return; }
            if (hit(x,y,sx,sy-tr,sw,sh+tr*2f)) { mSliderDragging=true; updateSliderFromX(x,sx,sw); return; }
            mSetPress=-1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetPress=2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetPress=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetPress=10;
            mSetHover=mSetPress;
        } else if (action==MotionEvent.ACTION_MOVE) {
            if (mSliderDragging) { updateSliderFromX(x,sx,sw); return; }
            mSetHover=-1;
            if      (hit(x,y,togX0,row2Y,togW,togH)) mSetHover=2;
            else if (hit(x,y,togX1,row2Y,togW,togH)) mSetHover=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetHover=10;
        } else if (action==MotionEvent.ACTION_UP) {
            if (mSliderDragging) { mSliderDragging=false; updateSliderFromX(x,sx,sw); return; }
            int p=mSetPress; mSetPress=-1; mSetHover=-1;
            switch (p) { case 2: mFps=30; break; case 3: mFps=60; break;
                case 10: if(hit(x,y,backX,backY,backW,backH)) mState=ST_MENU; break; }
        } else if (action==MotionEvent.ACTION_CANCEL) { mSliderDragging=false; mSetPress=-1; mSetHover=-1; }
    }

    private void updateSliderFromX(float touchX, float trackX, float trackW) {
        mVolume = Math.max(0f, Math.min(1f, (touchX-trackX)/trackW));
        if (mMusic != null) mMusic.onVolumeChanged(mVolume);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT TEST
    // ══════════════════════════════════════════════════════════════════════

    private int ftTotalChars() { int n=0; for (String s:FONT_TEST_LINES) n+=s.length(); return n; }

    private void updateFontTest() {
        if (mFtDone) return;
        long now=System.currentTimeMillis();
        if (now-mFtLast>=FT_CHAR_MS) { mFtLast=now; if (mFtChar<ftTotalChars()) mFtChar++; else mFtDone=true; }
    }

    private void renderFontTest() {
        rect(0,0,mW,mH,C_BG);
        float startY=mH*0.06f,maxLineH=(mH*0.72f)/FONT_TEST_LINES.length;
        int charsLeft=mFtChar;
        for (int li=0; li<FONT_TEST_LINES.length; li++) {
            String line=FONT_TEST_LINES[li]; float rowY=startY+li*maxLineH;
            float ps=fitTextPS(line,mUsableW*0.92f,gPS*1.05f);
            int show=Math.min(charsLeft,line.length()); charsLeft-=show;
            if (show<=0) break;
            String vis=line.substring(0,show);
            float baseY=rowY+ps*8f; float[] baseC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.25f};
            rect(mPad,baseY,mUsableW,Math.max(1f,gPS*0.3f),baseC);
            drawString(vis,mPad,rowY,ps,C_GOLD);
            if (show<line.length()&&(mFrame/12)%2==0) rect(mPad+charWidth(vis,ps),rowY,ps*0.8f,ps*9f,C_GOLD);
        }
        float infoPS=gPS*0.58f; float[] infoC={C_GREY[0],C_GREY[1],C_GREY[2],0.70f};
        if (!mFtDone) { drawString("YAZILIYOR...",mPad,mH*0.90f,infoPS,infoC); }
        else {
            float bw=mUsableW*0.50f,bh=Math.max(gPS*9f,mH*0.058f);
            drawButton("DEVAM",mW/2f-bw/2f,mH*0.88f,bw,bh,false,false,false);
        }
        float[] skipC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.12f};
        rect(0,0,mPad*4f,mPad*4f,skipC);
        scanLines();
    }

    private void touchFontTest(int action, float x, float y) {
        if (action!=MotionEvent.ACTION_UP) return;
        if (hit(x,y,0,0,mPad*4f,mPad*4f)) { startPartySelect(); return; }
        if (!mFtDone) { mFtChar=ftTotalChars(); mFtDone=true; }
        else startPartySelect();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARTİ SEÇİM EKRANI (ST_PARTY_SELECT) — Rev 8: Tam Kapasite
    // ══════════════════════════════════════════════════════════════════════

    private void startPartySelect() {
        mPartyHover=-1; mPartyPress=-1; mState=ST_PARTY_SELECT;
    }

    /**
     * Rev 8: startGameWithParty()
     * - mGameState.partyChoice set edilir (önceden eksikti).
     * - Yol mGameState.getScenarioFolder() üzerinden alınır.
     */
    private void startGameWithParty(int party) {
        mGameState.sifirla();
        mGameState.partyChoice = party;
        String folder = "scenario/" + mGameState.getScenarioFolder();
        mEngine.loadScenario(folder + "/cards.json");
        mEngine.loadEndings(folder + "/endings.json");
        mEngine.startScenario(mGameState);
        mCardSwipeX=0f; mCardSwiping=false;
        mTouchAction=-1;
        mState=ST_GAME;
    }

    private void renderPartySelection() {
        // Arka plan
        int bgTex = (mTexPartyBg > 0) ? mTexPartyBg : mTexMenuBg;
        if (bgTex > 0) {
            drawTex(bgTex, 0, 0, mW, mH, 0.55f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.62f};
            rect(0,0,mW,mH,ov);
        } else {
            rect(0,0,mW,mH,C_BG);
        }

        float cx=mW/2f;

        // Başlık
        float titlePS=fitTextPS("PARTINIZI SECIN",mUsableW*0.82f,gPS*1.2f);
        drawStringC("PARTINIZI SECIN", mH*0.07f, titlePS, C_GOLD);

        // Alt başlık
        float subPS=gPS*0.58f; float[] subC={C_GREY[0],C_GREY[1],C_GREY[2],0.70f};
        drawStringC("1999 - TURKIYE GENEL SECIMLER", mH*0.15f, subPS, subC);

        // Parti Kartları
        float cardW=mUsableW*0.90f, cardH=mH*0.175f;
        float cardX=cx-cardW/2f, gap=gPS*4f, startY=mH*0.23f;

        for (int i=0; i<PARTY_IDS.length; i++) {
            float cardY=startY+i*(cardH+gap);
            boolean hover=(mPartyHover==i), pressed=(mPartyPress==i);

            // Kart zemin
            float[] bgC=pressed?C_PRES:(hover?C_DARK:C_BG);
            rect(cardX, cardY, cardW, cardH, bgC);

            // Sol renkli şerit
            float stripW=gPS*2.2f, stripAlpha=hover?1.0f:0.55f;
            float[] stripC={
                C_GOLD[0]*(1f-i*0.18f),
                C_GOLD[1]*(1f-i*0.08f),
                C_GOLD[2]*(0.4f+i*0.15f),
                stripAlpha
            };
            rect(cardX, cardY, stripW, cardH, stripC);

            // Çerçeve
            float b=Math.max(1.5f,gPS*0.35f);
            float[] brdC=(hover||pressed)?C_GOLD:C_GDIM;
            rect(cardX,         cardY,         cardW, b,     brdC);
            rect(cardX,         cardY+cardH-b, cardW, b,     brdC);
            rect(cardX,         cardY,         b, cardH,     brdC);
            rect(cardX+cardW-b, cardY,         b, cardH,     brdC);

            // Kısa ad (HSP / MNP / LDP)
            float idPS=gPS*0.80f;
            float[] idC=hover?C_GOLD:C_GDIM;
            float idX=cardX+stripW+gPS*2.5f;
            drawString(PARTY_IDS[i], idX, cardY+gPS*1.5f, idPS, idC);

            // Ayırıcı
            float divX=idX+charWidth(PARTY_IDS[i],idPS)+gPS*2f;
            float[] divC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.35f};
            rect(divX, cardY+cardH*0.2f, Math.max(1f,gPS*0.25f), cardH*0.6f, divC);

            // Tam ad
            float namePS=fitTextPS(PARTY_NAMES[i],cardW*0.55f,gPS*0.62f);
            float nameX=divX+gPS*2.5f;
            drawString(PARTY_NAMES[i], nameX, cardY+gPS*1.5f, namePS, C_GOLD);

            // Slogan
            float slogPS=gPS*0.50f;
            float[] slogC={C_GDIM[0],C_GDIM[1],C_GDIM[2],hover?0.90f:0.65f};
            drawString(PARTY_SLOGANS[i], nameX, cardY+gPS*1.5f+namePS*11f+gPS, slogPS, slogC);

            // "SEC >" sadece hover'da
            if (hover) {
                float arrPS=gPS*0.60f; String arrStr="SEC >";
                float arrW=charWidth(arrStr,arrPS);
                drawString(arrStr, cardX+cardW-b-arrW-gPS*2f,
                        cardY+cardH/2f-arrPS*4.5f, arrPS, C_GOLD);
            }
        }

        // Alt bilgi
        float infoPS=gPS*0.54f; float[] infoC={C_GREY[0],C_GREY[1],C_GREY[2],0.50f};
        drawStringC("BIR PARTIYE DOKUN VE SEC", mH*0.88f, infoPS, infoC);

        // Geri butonu
        float backW=mUsableW*0.40f, backH=Math.max(gPS*8f,mH*0.054f);
        float backX=cx-backW/2f, backY=mH*0.92f;
        drawButton("GERI", backX, backY, backW, backH, false, false, false);

        scanLines();
    }

    private void touchPartySelect(int action, float x, float y) {
        float cx=mW/2f, cardW=mUsableW*0.90f, cardH=mH*0.175f;
        float cardX=cx-cardW/2f, gap=gPS*4f, startY=mH*0.23f;
        float backW=mUsableW*0.40f, backH=Math.max(gPS*8f,mH*0.054f);
        float backX=cx-backW/2f, backY=mH*0.92f;

        if (action==MotionEvent.ACTION_DOWN) {
            mPartyPress=-1;
            for (int i=0; i<PARTY_IDS.length; i++) {
                if (hit(x,y,cardX,startY+i*(cardH+gap),cardW,cardH)) { mPartyPress=i; mPartyHover=i; return; }
            }
        } else if (action==MotionEvent.ACTION_MOVE) {
            mPartyHover=-1;
            for (int i=0; i<PARTY_IDS.length; i++) {
                if (hit(x,y,cardX,startY+i*(cardH+gap),cardW,cardH)) { mPartyHover=i; break; }
            }
        } else if (action==MotionEvent.ACTION_UP) {
            int p=mPartyPress; mPartyPress=-1; mPartyHover=-1;
            if (hit(x,y,backX,backY,backW,backH)) { mState=ST_MENU; return; }
            if (p>=0 && hit(x,y,cardX,startY+p*(cardH+gap),cardW,cardH)) {
                startGameWithParty(p);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN — KART EKRANI (ST_GAME) — Rev 8: Tam Kapasite
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        rect(0,0,mW,mH,C_BG);

        // Üst bar şeridi
        float barAreaY=mPad*1.2f, barAreaH=mH*0.10f;
        drawStatBars(barAreaY, barAreaH);

        // Aktif kart
        ScenarioEngine.Card card=mEngine.getCurrentCard();
        if (card==null) {
            // Felaket yoksa seçim ekranı
            if (!mGameState.oyunBitti) triggerElectionScreen();
            scanLines();
            return;
        }

        syncCardEffects(card);

        // Kart geometrisi
        float cardW=mUsableW*0.88f, cardH=mH*0.54f;
        float cardCX=mW/2f-cardW/2f, cardCY=mH*0.20f;
        float absSwipe=Math.abs(mCardSwipeX), tiltOffset=mCardSwipeX*0.07f;
        float cardX=cardCX+mCardSwipeX;

        // Gölge
        float[] shadowC={0f,0f,0f,0.28f};
        rect(cardX+gPS, cardCY+tiltOffset+gPS, cardW, cardH, shadowC);

        // Kart arka plan + çerçeve
        rect(cardX, cardCY+tiltOffset, cardW, cardH, C_DARK);
        float[] frameCol=(absSwipe>mW*0.05f)?((mCardSwipeX>0)?C_GOLD:C_GDIM):C_GDIM;
        float b=Math.max(1.5f,gPS*0.4f);
        rect(cardX,           cardCY+tiltOffset,             cardW, b,     frameCol);
        rect(cardX,           cardCY+tiltOffset+cardH-b,     cardW, b,     frameCol);
        rect(cardX,           cardCY+tiltOffset,             b,     cardH, frameCol);
        rect(cardX+cardW-b,   cardCY+tiltOffset,             b,     cardH, frameCol);

        // İç içerik
        float innerX=cardX+gPS*3f, innerW=cardW-gPS*6f;
        float innerY=cardCY+tiltOffset+gPS*2.5f;

        // Karakter etiketi
        float charPS=gPS*0.56f;
        float[] dimC={C_GREY[0],C_GREY[1],C_GREY[2],0.80f};
        drawString(card.character.replace("_"," ").toUpperCase(), innerX, innerY, charPS, dimC);

        // Ayırıcı
        float divY=innerY+charPS*10f+gPS*0.5f;
        float[] lineC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.45f};
        rect(innerX, divY, innerW, Math.max(1f,gPS*0.25f), lineC);

        // Flavor
        float flavPS=gPS*0.60f;
        float[] flavC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.75f};
        List<String> flavLines=wrapText(card.flavor, innerW, flavPS);
        float flavY=divY+gPS*1.5f;
        for (int i=0; i<Math.min(flavLines.size(),2); i++)
            drawString(flavLines.get(i), innerX, flavY+i*flavPS*11f, flavPS, flavC);

        // Ana metin
        float textPS=gPS*0.72f;
        float textY=flavY+Math.min(flavLines.size(),2)*flavPS*11f+gPS*2f;
        List<String> textLines=wrapText(card.text, innerW, textPS);
        for (int i=0; i<Math.min(textLines.size(),6); i++)
            drawString(textLines.get(i), innerX, textY+i*textPS*11f, textPS, C_GOLD);

        // EVET / HAYIR etiketi
        if (absSwipe > mW*0.04f) {
            boolean goRight=(mCardSwipeX>0);
            String swipeLabel=goRight?"EVET":"HAYIR";
            float labelAlpha=Math.min(1f, absSwipe/(mW*0.14f));
            float[] swipeCol=goRight
                ? new float[]{C_GOLD[0],C_GOLD[1],C_GOLD[2],labelAlpha}
                : new float[]{C_GDIM[0],C_GDIM[1],C_GDIM[2],labelAlpha};
            float labelPS=fitTextPS(swipeLabel,cardW*0.55f,gPS*2.0f);
            float labelW=charWidth(swipeLabel,labelPS);
            float labelX=mW/2f-labelW/2f+mCardSwipeX*0.3f;
            float labelY=cardCY+cardH*0.40f+tiltOffset;
            float pad=gPS*2.5f;
            float[] boxC={C_BG[0],C_BG[1],C_BG[2],labelAlpha*0.80f};
            rect(labelX-pad,labelY-pad,labelW+pad*2f,labelPS*10f+pad*2f,boxC);
            float bl=Math.max(1f,gPS*0.35f);
            rect(labelX-pad,               labelY-pad,                      labelW+pad*2f,bl,swipeCol);
            rect(labelX-pad,               labelY+labelPS*10f+pad-bl,        labelW+pad*2f,bl,swipeCol);
            rect(labelX-pad,               labelY-pad,                      bl,labelPS*10f+pad*2f,swipeCol);
            rect(labelX+labelW+pad-bl,     labelY-pad,                      bl,labelPS*10f+pad*2f,swipeCol);
            drawString(swipeLabel, labelX, labelY, labelPS, swipeCol);
            drawBarHintDots(goRight, barAreaY, barAreaH);
        }

        // Yön okları (swipe yokken)
        if (!mCardSwiping) {
            float[] arC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.40f};
            float arPS=gPS*0.65f;
            drawString("<", mPad, mH*0.46f, arPS, arC);
            drawString(">", mW-mPad-charWidth(">",arPS), mH*0.46f, arPS, arC);
        }

        // Seçenek etiketleri (kart altı)
        float optY=cardCY+cardH+gPS*3f;
        float optPS=gPS*0.58f;
        float[] leftOptC={(mCardSwipeX<0)?C_GOLD[0]:C_GDIM[0],
                          (mCardSwipeX<0)?C_GOLD[1]:C_GDIM[1],
                          (mCardSwipeX<0)?C_GOLD[2]:C_GDIM[2], 1f};
        float[] rightOptC={(mCardSwipeX>0)?C_GOLD[0]:C_GDIM[0],
                           (mCardSwipeX>0)?C_GOLD[1]:C_GDIM[1],
                           (mCardSwipeX>0)?C_GOLD[2]:C_GDIM[2], 1f};
        drawString("< "+card.choiceLeft.label, mPad, optY, optPS, leftOptC);
        String rightStr=card.choiceRight.label+" >";
        drawString(rightStr, mW-mPad-charWidth(rightStr,optPS), optY, optPS, rightOptC);

        // Alt durum çubuğu
        renderStatusBar();

        scanLines();
    }

    private void syncCardEffects(ScenarioEngine.Card card) {
        mCardEffectLeft  = new int[]{card.choiceLeft.halk,  card.choiceLeft.din,
                                     card.choiceLeft.para,  card.choiceLeft.ordu};
        mCardEffectRight = new int[]{card.choiceRight.halk, card.choiceRight.din,
                                     card.choiceRight.para, card.choiceRight.ordu};
    }

    private void triggerElectionScreen() {
        mElectionResult=mGameState.getElectionResult();
        mElectionScore=mGameState.getElectionScore();
        mState=ST_ELECTION;
    }

    private void touchGame(int action, float x, float y) {
        float cardW=mUsableW*0.88f, cardH=mH*0.54f;
        float cardX0=mW/2f-cardW/2f, cardY0=mH*0.20f;
        float threshold=mW*0.27f;
        if (action==MotionEvent.ACTION_UP) {
            float bw=mUsableW*0.32f, bh=Math.max(gPS*7f,mH*0.050f);
            float bx=mW/2f-bw/2f, by=mH*0.88f;
            if (hit(x,y,bx,by,bw,bh)) {
                mCardSwipeX=0f; mCardSwiping=false; mState=ST_MENU; return;
            }
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (hit(x,y,cardX0,cardY0,cardW,cardH)) {
                    mCardSwiping=true; mCardSwipeStartX=x; mCardSwipeX=0f;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCardSwiping) mCardSwipeX=x-mCardSwipeStartX;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCardSwiping) {
                    if      (mCardSwipeX >  threshold) onCardChosen(true);
                    else if (mCardSwipeX < -threshold) onCardChosen(false);
                    mCardSwipeX=0f; mCardSwiping=false;
                }
                break;
        }
    }

    private void onCardChosen(boolean right) {
        mEngine.choose(mGameState, right);
        if (mGameState.oyunBitti) {
            ScenarioEngine.Ending ending=mEngine.getEnding(mGameState);
            mEndingTitle=(ending!=null)?ending.title:"OYUN BITTI";
            mEndingBody =(ending!=null)?ending.body:"";
            mEndingChar=0; mEndingLast=System.currentTimeMillis(); mEndingDone=false;
            mState=ST_ENDING;
        }
    }

    // ── STAT BARLARI ─────────────────────────────────────────────────────

    private void drawStatBars(float areaY, float areaH) {
        int barCount=BAR_LABELS.length;
        float slotW=mUsableW/(float)barCount, barH=Math.max(4f,gPS*1.4f);
        float labPS=gPS*0.52f, barY=areaY+areaH*0.50f, labY=areaY+areaH*0.02f;
        int[] vals={mGameState.halk, mGameState.din, mGameState.para, mGameState.ordu};
        for (int i=0; i<barCount; i++) {
            float bx=mPad+i*slotW, bw=slotW*0.84f;
            int v=vals[i], danger=mGameState.getDangerLevel(v);
            float[] fillCol=(danger==2)?C_DANGER:(danger==1)?C_WARN:C_GOLD;
            rect(bx,barY,bw,barH,C_DARK);
            float fill=(v/100f)*bw; if (fill>0) rect(bx,barY,fill,barH,fillCol);
            float bl=Math.max(1f,gPS*0.2f);
            float[] frmC={fillCol[0],fillCol[1],fillCol[2],0.55f};
            rect(bx,barY,bw,bl,frmC); rect(bx,barY+barH-bl,bw,bl,frmC);
            rect(bx,barY,bl,barH,frmC); rect(bx+bw-bl,barY,bl,barH,frmC);
            float[] labCol=(danger>0)?fillCol:C_GDIM;
            float lw=charWidth(BAR_LABELS[i],labPS);
            drawString(BAR_LABELS[i], bx+bw/2f-lw/2f, labY, labPS, labCol);
            if (danger>0) {
                String numStr=String.valueOf(v); float numPS=labPS*0.85f;
                float nw=charWidth(numStr,numPS);
                float[] numC={fillCol[0],fillCol[1],fillCol[2],0.90f};
                drawString(numStr, bx+bw/2f-nw/2f, barY+barH+gPS*0.5f, numPS, numC);
            }
        }
    }

    private void drawBarHintDots(boolean goRight, float areaY, float areaH) {
        int[] effects=goRight?mCardEffectRight:mCardEffectLeft;
        int barCount=BAR_LABELS.length;
        float slotW=mUsableW/(float)barCount, barY=areaY+areaH*0.50f;
        float dotR=Math.max(gPS*1.5f,4f);
        for (int i=0; i<barCount&&i<effects.length; i++) {
            if (effects[i]==0) continue;
            float bx=mPad+i*slotW, bw=slotW*0.84f;
            float dotCX=bx+bw/2f, dotCY=barY-dotR*2.4f;
            float[] dotBase=(effects[i]>0)?C_GOLD:C_GDIM;
            float alpha=0.60f+0.40f*(float)Math.sin(mFrame*0.17f);
            float[] blinkC={dotBase[0],dotBase[1],dotBase[2],alpha};
            rect(dotCX-dotR,dotCY-dotR,dotR*2f,dotR*2f,blinkC);
            float sPS=gPS*0.44f; String sign=(effects[i]>0)?"+":"-";
            drawString(sign, dotCX-charWidth(sign,sPS)/2f, dotCY-sPS*5f, sPS, blinkC);
        }
    }

    private void renderStatusBar() {
        float barY=mH*0.91f, barH=mH-barY;
        float[] sepC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.28f};
        rect(0,barY-Math.max(1f,gPS*0.25f),mW,Math.max(1f,gPS*0.25f),sepC);
        rect(0,barY,mW,barH,C_DARK);
        float ps=gPS*0.54f;
        String yearStr="YIL "+mGameState.year;
        String actStr ="PERDE "+mGameState.currentAct;
        String titleStr=mGameState.getTitle();
        drawString(yearStr, mPad, barY+gPS*0.8f, ps, C_GOLD);
        float aw=charWidth(actStr,ps);
        drawString(actStr, mW/2f-aw/2f, barY+gPS*0.8f, ps, C_GDIM);
        float tw=charWidth(titleStr,ps);
        drawString(titleStr, mW-mPad-tw, barY+gPS*0.8f, ps, C_GREY);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FELAKET SONU (ST_ENDING) — Rev 8: Tam Kapasite
    // ══════════════════════════════════════════════════════════════════════

    private void updateEnding() {
        if (mEndingDone) return;
        long now=System.currentTimeMillis();
        String full=mEndingTitle+"\n\n"+mEndingBody;
        if (now-mEndingLast>=ENDING_CHAR_MS) {
            mEndingLast=now;
            if (mEndingChar<full.length()) mEndingChar++;
            else mEndingDone=true;
        }
    }

    private void renderEnding() {
        rect(0,0,mW,mH,C_BG);
        float cx=mW/2f;
        String full=mEndingTitle+"\n\n"+mEndingBody;
        String vis=full.substring(0, Math.min(mEndingChar,full.length()));

        // Başlık / body ayır
        String[] parts=vis.split("\n",2);
        String titleVis=parts[0];
        String bodyVis=(parts.length>1)?parts[1].replaceFirst("^\n",""):"";

        // Başlık
        float titlePS=fitTextPS(titleVis.isEmpty()?"X":titleVis, mUsableW*0.80f, gPS*1.3f);
        drawStringC(titleVis, mH*0.10f, titlePS, C_GOLD);

        // Ayırıcı
        rect(mPad*2f, mH*0.10f+titlePS*10f+gPS, mUsableW-mPad*2f, Math.max(1f,gPS*0.3f), C_GDIM);

        // Body (daktilo)
        float bodyPS=gPS*0.70f, bodyY=mH*0.10f+titlePS*10f+gPS*4f;
        List<String> bodyLines=wrapText(bodyVis, mUsableW*0.86f, bodyPS);
        for (int i=0; i<bodyLines.size(); i++)
            drawStringC(bodyLines.get(i), bodyY+i*bodyPS*11.5f, bodyPS, C_GOLD);

        // İmleç
        if (!mEndingDone&&(mFrame/15)%2==0)
            rect(cx, bodyY+bodyLines.size()*bodyPS*11.5f, bodyPS, bodyPS*8f, C_GOLD);

        // Butonlar (tamamlanınca)
        if (mEndingDone) {
            float bh=Math.max(gPS*8f,mH*0.056f);
            float bw1=mUsableW*0.44f, bw2=mUsableW*0.44f, gap=gPS*3f, by=mH*0.84f;
            float bx1=cx-gap/2f-bw1, bx2=cx+gap/2f;
            drawButton("YENIDEN", bx1, by, bw1, bh, false, false, false);
            drawButton("ANA MENU", bx2, by, bw2, bh, false, false, false);
            if (mTouchAction==MotionEvent.ACTION_UP) {
                if (hit(mTouchX,mTouchY,bx1,by,bw1,bh)) {
                    startGameWithParty(mGameState.partyChoice); mTouchAction=-1;
                } else if (hit(mTouchX,mTouchY,bx2,by,bw2,bh)) {
                    mGameState.sifirla(); mState=ST_MENU; mTouchAction=-1;
                }
            }
        }
        scanLines();
    }

    private void touchEnding(int action) {
        if (action==MotionEvent.ACTION_DOWN&&!mEndingDone) {
            mEndingChar=(mEndingTitle+"\n\n"+mEndingBody).length();
            mEndingDone=true;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEÇİM SONUCU (ST_ELECTION) — Rev 8: Dinamik parti adı
    // ══════════════════════════════════════════════════════════════════════

    private void renderElection() {
        rect(0,0,mW,mH,C_BG);
        float cx=mW/2f;
        String titleStr="SECIM GECESI";
        float titlePS=fitTextPS(titleStr,mUsableW*0.80f,gPS*1.2f);
        drawStringC(titleStr, mH*0.10f, titlePS, C_GOLD);

        // Skor
        String scoreStr="HALK DESTEGI: %"+mElectionScore;
        float scorePS=gPS*0.80f;
        drawStringC(scoreStr, mH*0.24f, scorePS, C_GDIM);

        // Parti adı dinamik
        String partyName=mGameState.getPartyDisplayName();

        String resultStr; float[] resultCol; String subStr;
        if (mElectionResult==GameState.ELECTION_WIN) {
            resultStr="KAZANDINIZ"; resultCol=C_GOLD;
            subStr=partyName+" birinci parti. Koalisyon gorusmeleri basliyor.";
        } else if (mElectionResult==GameState.ELECTION_TIE) {
            resultStr="IKINCI TUR"; resultCol=C_WARN;
            subStr="Secim ikinci tura gitti. Uc gun sonra karar.";
        } else {
            resultStr="KAYBETTINIZ"; resultCol=C_DANGER;
            subStr=partyName+" ucuncu parti. Muhalefette devam.";
        }

        float resPS=fitTextPS(resultStr,mUsableW*0.75f,gPS*1.6f);
        drawStringC(resultStr, mH*0.36f, resPS, resultCol);
        float subPS=gPS*0.65f;
        drawStringCWrapped(subStr, mH*0.50f, subPS, C_GDIM);

        // Butonlar
        float bh=Math.max(gPS*8f,mH*0.056f), bw=mUsableW*0.44f, gap=gPS*3f, by=mH*0.76f;
        float bx1=cx-gap/2f-bw, bx2=cx+gap/2f;
        drawButton("YENIDEN", bx1, by, bw, bh, false, false, false);
        drawButton("ANA MENU", bx2, by, bw, bh, false, false, false);

        // Kazanıldıysa "İktidara Geç" butonu
        if (mElectionResult==GameState.ELECTION_WIN) {
            float bigBw=mUsableW*0.70f, bigBh=Math.max(gPS*9f,mH*0.060f);
            drawButton("IKTIDARA GEC", cx-bigBw/2f, mH*0.66f, bigBw, bigBh, false, false, false);
        }

        scanLines();
    }

    private void touchElection(int action, float x, float y) {
        if (action!=MotionEvent.ACTION_UP) return;
        float cx=mW/2f, bh=Math.max(gPS*8f,mH*0.056f), bw=mUsableW*0.44f, gap=gPS*3f, by=mH*0.76f;
        float bx1=cx-gap/2f-bw, bx2=cx+gap/2f;
        if (hit(x,y,bx1,by,bw,bh)) { startGameWithParty(mGameState.partyChoice); return; }
        if (hit(x,y,bx2,by,bw,bh)) { mGameState.sifirla(); mState=ST_MENU; return; }
        // İktidara geç
        if (mElectionResult==GameState.ELECTION_WIN) {
            float bigBw=mUsableW*0.70f, bigBh=Math.max(gPS*9f,mH*0.060f);
            if (hit(x,y,cx-bigBw/2f,mH*0.66f,bigBw,bigBh)) {
                mGameState.gamePhase=GameState.PHASE_RULING;
                String folder="scenario/ruling_era";
                mEngine.loadScenario(folder+"/cards.json");
                mEngine.loadEndings(folder+"/endings.json");
                mEngine.startScenario(mGameState);
                mCardSwipeX=0f; mCardSwiping=false;
                mState=ST_GAME;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YARDIMCI METODlar
    // ══════════════════════════════════════════════════════════════════════

    private String trUpperString(String s) {
        if (s==null) return "";
        StringBuilder sb=new StringBuilder(s.length());
        for (int i=0; i<s.length(); i++) sb.append(trUpper(s.charAt(i)));
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TEXTURE, SHADER, RENDER PRİMİTİFLERİ  (KORUNUYOR)
    // ══════════════════════════════════════════════════════════════════════

    private int loadTexture(String name) {
        try {
            InputStream is=mAssets.open(name);
            Bitmap bmp=BitmapFactory.decodeStream(is); is.close();
            if (bmp==null) return -1;
            int[] ids=new int[1]; GLES20.glGenTextures(1,ids,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0); bmp.recycle();
            return ids[0];
        } catch (IOException e) { return -1; }
    }

    private int buildProgram(String vs, String fs) {
        int v=compile(GLES20.GL_VERTEX_SHADER,vs), f=compile(GLES20.GL_FRAGMENT_SHADER,fs);
        int p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
        int[] s=new int[1]; GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,s,0);
        if (s[0]==0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f); return p;
    }

    private int compile(int type, String src) {
        int s=GLES20.glCreateShader(type);
        GLES20.glShaderSource(s,src); GLES20.glCompileShader(s);
        int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if (ok[0]==0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private void loadIntroFromAssets() {
        final String PATH="src/scenario/Opposition_1/intro.md";
        try {
            InputStream is=mAssets.open(PATH);
            byte[] buf=new byte[is.available()]; is.read(buf); is.close();
            String raw=new String(buf,"UTF-8");
            List<String> texts=new ArrayList<>(), assets=new ArrayList<>();
            String[] blocks=raw.split("(?m)^---\\s*$");
            for (String block:blocks) {
                block=block.trim(); if (!block.contains("[INTRO-")) continue;
                String bgAsset=""; StringBuilder sceneTxt=new StringBuilder();
                for (String line:block.split("\n")) {
                    String t=line.trim();
                    if (t.startsWith("`bg:")) { bgAsset=t.replace("`bg:","").replace("`","").trim(); continue; }
                    if (t.startsWith("**[INTRO-")||t.startsWith("#")||t.isEmpty()) continue;
                    if (!bgAsset.isEmpty()) { if (sceneTxt.length()>0) sceneTxt.append("\n"); sceneTxt.append(t); }
                }
                if (!bgAsset.isEmpty()&&sceneTxt.length()>0) { assets.add(bgAsset); texts.add(sceneTxt.toString()); }
            }
            mCinemaText=texts.toArray(new String[0]); mCinemaAssets=assets.toArray(new String[0]);
        } catch (Exception e) { mCinemaText=new String[0]; mCinemaAssets=new String[0]; }
    }

    private boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px>=rx&&px<=rx+rw&&py>=ry&&py<=ry+rh;
    }

    private void rect(float x, float y, float w, float h, float[] c) {
        GLES20.glUseProgram(mProgCol);
        GLES20.glUniform2f(mLocURes,mW,mH);
        GLES20.glUniform4f(mLocUColor,c[0],c[1],c[2],c[3]);
        mVtxBuf.position(0);
        mVtxBuf.put(x);mVtxBuf.put(y);mVtxBuf.put(x+w);mVtxBuf.put(y);
        mVtxBuf.put(x);mVtxBuf.put(y+h);mVtxBuf.put(x+w);mVtxBuf.put(y+h);
        mVtxBuf.position(0);
        GLES20.glEnableVertexAttribArray(mLocAPos);
        GLES20.glVertexAttribPointer(mLocAPos,2,GLES20.GL_FLOAT,false,0,mVtxBuf);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(mLocAPos);
    }

    private void drawTex(int id, float x, float y, float w, float h, float alpha) {
        if (id<=0) return;
        GLES20.glUseProgram(mProgTex);
        GLES20.glUniform2f(mLocTURes,mW,mH);
        GLES20.glUniform1i(mLocTUTex,0);
        GLES20.glUniform1f(mLocTUAlpha,alpha);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id);
        ByteBuffer bb=ByteBuffer.allocateDirect(16*4); bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb=bb.asFloatBuffer();
        fb.put(x);fb.put(y);fb.put(0f);fb.put(0f);
        fb.put(x+w);fb.put(y);fb.put(1f);fb.put(0f);
        fb.put(x);fb.put(y+h);fb.put(0f);fb.put(1f);
        fb.put(x+w);fb.put(y+h);fb.put(1f);fb.put(1f);
        fb.position(0); int stride=4*4;
        GLES20.glEnableVertexAttribArray(mLocTAPos);
        GLES20.glVertexAttribPointer(mLocTAPos,2,GLES20.GL_FLOAT,false,stride,fb);
        fb.position(2);
        GLES20.glEnableVertexAttribArray(mLocTAUV);
        GLES20.glVertexAttribPointer(mLocTAUV,2,GLES20.GL_FLOAT,false,stride,fb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(mLocTAPos);
        GLES20.glDisableVertexAttribArray(mLocTAUV);
    }

    private void scanLines() {
        float step=Math.max(2f,mH/400f)*3f;
        for (float y=0; y<mH; y+=step) rect(0,y,mW,Math.max(1f,step*0.35f),C_SCAN);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BUTON
    // ══════════════════════════════════════════════════════════════════════

    private void drawButton(String label, float x, float y, float w, float h,
                            boolean hover, boolean pressed, boolean disabled) {
        float[] bg  = disabled?C_DARK:(pressed?C_PRES:C_DARK);
        float[] brd = disabled?C_GREY:(pressed||hover?C_GOLD:C_GDIM);
        float[] txt = disabled?C_GREY:(pressed||hover?C_GOLD:C_GDIM);
        rect(x,y,w,h,bg);
        float b=Math.max(1.5f,gPS*0.4f);
        rect(x,y,w,b,brd); rect(x,y+h-b,w,b,brd);
        rect(x,y,b,h,brd); rect(x+w-b,y,b,h,brd);
        float ps=fitTextPS(label,w*0.78f,gPS*0.80f);
        float tw=charWidth(label,ps);
        drawString(label,x+w/2f-tw/2f,y+h/2f-4.5f*ps,ps,txt);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PİXEL FONT (KORUNUYOR — Rev7'den taşındı, hiç değişmedi)
    // ══════════════════════════════════════════════════════════════════════

    private float charWidth(String s, float ps) { return s.length()*ps*8f; }
    private float fitTextPS(String text, float maxW, float maxPS) {
        float needed=charWidth(text,maxPS); return (needed<=maxW)?maxPS:maxPS*(maxW/needed);
    }

    private List<String> wrapText(String text, float maxLineW, float ps) {
        List<String> result=new ArrayList<>();
        for (String para:text.split("\n",-1)) {
            if (para.isEmpty()) { result.add(""); continue; }
            String[] words=para.split(" "); StringBuilder line=new StringBuilder();
            for (String word:words) {
                String test=(line.length()==0)?word:line+" "+word;
                if (charWidth(test,ps)<=maxLineW) line=new StringBuilder(test);
                else { if (line.length()>0) result.add(line.toString()); line=new StringBuilder(word); }
            }
            if (line.length()>0) result.add(line.toString());
        }
        return result;
    }

    private void drawString(String s, float x, float y, float ps, float[] c) {
        float cx=x;
        for (int i=0; i<s.length(); i++) { drawChar(s.charAt(i),cx,y,ps,c); cx+=ps*8f; }
    }

    private char trUpper(char ch) {
        switch(ch){
            case 'i':      return '\u0130';
            case '\u0131': return 'I';
            case '\u00FC': return '\u00DC';
            case '\u00F6': return '\u00D6';
            case '\u00E7': return '\u00C7';
            case '\u015F': return '\u015E';
            case '\u011F': return '\u011E';
            default:       return Character.toUpperCase(ch);
        }
    }

    private void drawStringC(String s, float y, float ps, float[] c) {
        drawString(s, mW/2f-charWidth(s,ps)/2f, y, ps, c);
    }

    private void drawStringCWrapped(String s, float startY, float ps, float[] c) {
        List<String> lines=wrapText(s,mUsableW*0.88f,ps);
        float lineH=ps*11f;
        for (int i=0; i<lines.size(); i++) drawStringC(lines.get(i),startY+i*lineH,ps,c);
    }

    // Font primitifleri (Rev7'den değişmeden kopyalandı)
    private float T(float ps) { return ps*1.5f; }
    private void pH(float x, float y, float ps, int row, float[] c)
        { rect(x,y+row*ps,ps*5.5f,T(ps),c); }
    private void pHh(float x, float y, float ps, int row, float[] c)
        { rect(x+ps*2.5f,y+row*ps,ps*3f,T(ps),c); }
    private void pV(float x, float y, float ps, int col, int r0, int r1, float[] c)
        { rect(x+col*ps,y+r0*ps,T(ps),(r1-r0+1)*ps,c); }
    private void pD(float x, float y, float ps, int col, int row, float[] c)
        { rect(x+col*ps,y+row*ps,T(ps),T(ps),c); }

    /**
     * drawChar — Rev7'den tamamen korundu.
     * Tüm Türkçe karakterler + Latin alfabe + rakamlar + noktalama dahil.
     */
    private void drawChar(char ch, float x, float y, float ps, float[] c) {
        switch(ch){
            case 'A': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);break;
            case 'B': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,3,c);pV(x,y,ps,4,5,7,c);break;
            case 'C': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);break;
            case '\u00C7': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pD(x,y,ps,2,8,c);break;
            case 'D': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,3,2,6,c);pD(x,y,ps,4,2,c);pD(x,y,ps,4,6,c);break;
            case 'E': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);break;
            case 'F': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);break;
            case 'G': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pHh(x,y,ps,4,c);pV(x,y,ps,4,4,7,c);break;
            case '\u011E': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pHh(x,y,ps,4,c);pV(x,y,ps,4,4,7,c);pD(x,y,ps,2,0,c);pD(x,y,ps,3,0,c);break;
            case 'H': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,4,c);break;
            case 'I': pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,2,1,7,c);break;
            case '\u0130': pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,2,1,7,c);pD(x,y,ps,2,0,c);break;
            case 'J': pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,3,1,6,c);pV(x,y,ps,0,5,7,c);break;
            case 'K': pV(x,y,ps,0,1,7,c);pD(x,y,ps,4,1,c);pD(x,y,ps,3,2,c);pH(x,y,ps,4,c);pD(x,y,ps,3,5,c);pD(x,y,ps,4,6,c);pD(x,y,ps,4,7,c);break;
            case 'L': pV(x,y,ps,0,1,7,c);pH(x,y,ps,7,c);break;
            case 'M': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pD(x,y,ps,1,2,c);pD(x,y,ps,2,3,c);pD(x,y,ps,3,2,c);break;
            case 'N': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pD(x,y,ps,1,2,c);pD(x,y,ps,2,3,c);pD(x,y,ps,3,4,c);pD(x,y,ps,4,5,c);break;
            case 'O': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);break;
            case '\u00D6': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pD(x,y,ps,1,0,c);pD(x,y,ps,3,0,c);break;
            case 'P': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pV(x,y,ps,4,1,4,c);break;
            case 'R': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pV(x,y,ps,4,1,4,c);pD(x,y,ps,3,5,c);pD(x,y,ps,4,6,c);pD(x,y,ps,4,7,c);break;
            case 'S': pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,3,c);pV(x,y,ps,4,5,7,c);break;
            case '\u015E': pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,3,c);pV(x,y,ps,4,5,7,c);pD(x,y,ps,2,8,c);break;
            case 'T': pH(x,y,ps,1,c);pV(x,y,ps,2,1,7,c);break;
            case 'U': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,7,c);break;
            case '\u00DC': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,7,c);pD(x,y,ps,1,0,c);pD(x,y,ps,3,0,c);break;
            case 'V': pV(x,y,ps,0,1,5,c);pV(x,y,ps,4,1,5,c);pD(x,y,ps,1,6,c);pD(x,y,ps,3,6,c);pD(x,y,ps,2,7,c);break;
            case 'Y': pV(x,y,ps,0,1,3,c);pV(x,y,ps,4,1,3,c);pV(x,y,ps,2,3,7,c);pD(x,y,ps,1,3,c);pD(x,y,ps,3,3,c);break;
            case 'Z': pH(x,y,ps,1,c);pH(x,y,ps,7,c);pD(x,y,ps,3,2,c);pD(x,y,ps,2,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,1,5,c);break;
            // Küçük harfler
            case 'a': pV(x,y,ps,4,3,7,c);pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,5,7,c);break;
            case 'b': pV(x,y,ps,0,1,7,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,4,7,c);pD(x,y,ps,3,4,c);break;
            case 'c': pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);break;
            case '\u00E7': pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pD(x,y,ps,2,8,c);break;
            case 'd': pV(x,y,ps,4,1,7,c);pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);break;
            case 'e': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,5,c);break;
            case 'f': pH(x,y,ps,2,c);pH(x,y,ps,4,c);pV(x,y,ps,1,2,7,c);break;
            case 'g': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,8,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,8,c);break;
            case '\u011F': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,8,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,8,c);pD(x,y,ps,2,0,c);break;
            case 'h': pV(x,y,ps,0,1,7,c);pH(x,y,ps,4,c);pV(x,y,ps,4,4,7,c);break;
            case '\u0131': pV(x,y,ps,2,3,7,c);break;
            case 'i': pV(x,y,ps,2,3,7,c);pD(x,y,ps,2,1,c);break;
            case 'j': pV(x,y,ps,3,3,8,c);pD(x,y,ps,3,1,c);pV(x,y,ps,0,7,8,c);break;
            case 'k': pV(x,y,ps,0,1,7,c);pD(x,y,ps,3,4,c);pD(x,y,ps,4,3,c);pD(x,y,ps,4,5,c);pD(x,y,ps,3,6,c);break;
            case 'l': pV(x,y,ps,1,1,7,c);pH(x,y,ps,7,c);break;
            case 'm': pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,3,c);pD(x,y,ps,2,4,c);break;
            case 'n': pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,4,7,c);pH(x,y,ps,3,c);pD(x,y,ps,3,3,c);break;
            case 'o': pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);break;
            case '\u00F6': pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pD(x,y,ps,1,1,c);pD(x,y,ps,3,1,c);break;
            case 'p': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pV(x,y,ps,0,3,8,c);pV(x,y,ps,4,3,5,c);break;
            case 'r': pV(x,y,ps,0,3,7,c);pH(x,y,ps,3,c);pD(x,y,ps,3,4,c);pD(x,y,ps,4,3,c);break;
            case 's': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,4,c);pV(x,y,ps,4,6,7,c);break;
            case '\u015F': pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,4,c);pV(x,y,ps,4,6,7,c);pD(x,y,ps,2,8,c);break;
            case 't': pV(x,y,ps,1,1,7,c);pH(x,y,ps,3,c);pH(x,y,ps,7,c);break;
            case 'u': pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,7,c);break;
            case '\u00FC': pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,7,c);pD(x,y,ps,1,1,c);pD(x,y,ps,3,1,c);break;
            case 'v': pV(x,y,ps,0,3,6,c);pV(x,y,ps,4,3,6,c);pD(x,y,ps,1,6,c);pD(x,y,ps,3,6,c);pD(x,y,ps,2,7,c);break;
            case 'y': pV(x,y,ps,0,3,5,c);pV(x,y,ps,4,3,8,c);pD(x,y,ps,1,6,c);pD(x,y,ps,2,7,c);break;
            case 'z': pH(x,y,ps,3,c);pH(x,y,ps,7,c);pD(x,y,ps,3,4,c);pD(x,y,ps,2,5,c);pD(x,y,ps,1,6,c);break;
            // Rakamlar
            case '0': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);pD(x,y,ps,3,3,c);pD(x,y,ps,2,4,c);break;
            case '1': pD(x,y,ps,1,2,c);pH(x,y,ps,7,c);pV(x,y,ps,2,1,7,c);break;
            case '2': pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,4,c);pV(x,y,ps,0,4,7,c);break;
            case '3': pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,7,c);break;
            case '4': pV(x,y,ps,0,1,4,c);pH(x,y,ps,4,c);pV(x,y,ps,4,1,7,c);break;
            case '5': pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,4,7,c);break;
            case '6': pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,4,7,c);break;
            case '7': pH(x,y,ps,1,c);pV(x,y,ps,4,1,7,c);break;
            case '8': pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);break;
            case '9': pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);break;
            // Noktalama
            case '.': pD(x,y,ps,2,7,c);break;
            case ',': pD(x,y,ps,2,6,c);pD(x,y,ps,1,7,c);break;
            case '!': pV(x,y,ps,2,1,5,c);pD(x,y,ps,2,7,c);break;
            case '?': pH(x,y,ps,1,c);pV(x,y,ps,4,1,4,c);pD(x,y,ps,2,4,c);pD(x,y,ps,2,5,c);pD(x,y,ps,2,7,c);break;
            case ':': pD(x,y,ps,2,3,c);pD(x,y,ps,2,6,c);break;
            case '-': pH(x,y,ps,4,c);break;
            case '+': pV(x,y,ps,2,2,6,c);rect(x+ps,y+ps*3.5f,ps*3f,T(ps),c);break;
            case '=': pH(x,y,ps,3,c);pH(x,y,ps,5,c);break;
            case '/': pD(x,y,ps,3,2,c);pD(x,y,ps,2,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,1,5,c);break;
            case '(': pV(x,y,ps,1,1,7,c);pD(x,y,ps,2,1,c);pD(x,y,ps,2,7,c);break;
            case ')': pV(x,y,ps,3,1,7,c);pD(x,y,ps,2,1,c);pD(x,y,ps,2,7,c);break;
            case '"': pV(x,y,ps,1,1,2,c);pV(x,y,ps,3,1,2,c);break;
            case '\'': pV(x,y,ps,2,1,2,c);break;
            case '%': pD(x,y,ps,0,1,c);pD(x,y,ps,4,7,c);pD(x,y,ps,3,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,1,5,c);break;
            case '>': pD(x,y,ps,0,2,c);pD(x,y,ps,1,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,1,5,c);pD(x,y,ps,0,6,c);break;
            case '<': pD(x,y,ps,4,2,c);pD(x,y,ps,3,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,3,5,c);pD(x,y,ps,4,6,c);break;
            case ' ': break;
            default:  pD(x,y,ps,2,4,c); break;
        }
    }
}

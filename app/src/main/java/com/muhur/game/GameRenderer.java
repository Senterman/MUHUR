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
 * MÜHÜR — GameRenderer (Revizyon 9)
 *
 * Akış:
 *   Splash → Menü → Sinematik → Parti Seçim → [Oyun/Placeholder] →
 *   Seçim Gecesi / Felaket Sonu → Menü
 *
 * Parti durumu:
 *   HSP  → Perde 1 bağlı (OPP1-001..015), Perde 2-5 "Yakında"
 *   MNP  → Perde 1 bağlı (MNP-001..015), Perde 2-5 "Yakında"
 *   LDP  → Tüm içerik "Yakında"
 *   İktidar → "Yakında"
 *
 * Karakter görselleri:
 *   assets/characters/inspector_mehmet.png
 *   assets/characters/secretary_leyla.png
 *   assets/characters/general_rifat.png
 *   assets/characters/imam_nureddin.png
 *   assets/characters/journalist_can.png
 *   assets/characters/spy_x.png
 *   assets/characters/old_voter_ali.png
 *   assets/characters/student_zeynep.png
 *   (Yüklenmezse placeholder kutu gösterilir)
 *
 * Arka planlar:
 *   assets/opposition_bg.png   → muhalefet kart ekranı arka planı
 *   assets/government_bg.png   → iktidar evresi arka planı
 *   assets/party_select_bg.png → parti seçim ekranı arka planı
 *   assets/menu_bg.png         → menü ve genel arka plan
 *
 * Logolar:
 *   assets/logos/logo_hsp.png
 *   assets/logos/logo_mnp.png
 *   assets/logos/logo_ldp.png
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ── MusicController ───────────────────────────────────────────────────

    public interface MusicController {
        void onMusicStart();
        void onVolumeChanged(float volume);
        float getVolume();
    }

    // ── State sabitleri ───────────────────────────────────────────────────

    private static final int ST_SPLASH       = 0;
    private static final int ST_MENU         = 1;
    private static final int ST_CINEMA       = 2;
    private static final int ST_SETTINGS     = 3;
    private static final int ST_GAME         = 4;
    private static final int ST_FONTTEST     = 5;
    private static final int ST_ENDING       = 6;
    private static final int ST_ELECTION     = 7;
    private static final int ST_PARTY_SELECT = 8;
    private static final int ST_COMING_SOON  = 9;   // Yakında ekranı

    // ── Renk paleti ──────────────────────────────────────────────────────

    private static final float[] C_BG     = {0.051f, 0.039f, 0.020f, 1.0f};
    private static final float[] C_GOLD   = {0.831f, 0.659f, 0.325f, 1.0f};
    private static final float[] C_GDIM   = {0.420f, 0.330f, 0.160f, 1.0f};
    private static final float[] C_DARK   = {0.090f, 0.070f, 0.035f, 1.0f};
    private static final float[] C_PRES   = {0.180f, 0.140f, 0.070f, 1.0f};
    private static final float[] C_GREY   = {0.260f, 0.240f, 0.220f, 1.0f};
    private static final float[] C_SCAN   = {0.000f, 0.000f, 0.000f, 0.15f};
    private static final float[] C_WARN   = {0.820f, 0.720f, 0.100f, 1.0f};
    private static final float[] C_DANGER = {0.820f, 0.200f, 0.150f, 1.0f};
    private static final float[] C_RED    = {0.700f, 0.100f, 0.100f, 1.0f};

    // ── Parti verileri ────────────────────────────────────────────────────

    /** İndeks: GameState.PARTY_HSP/MNP/LDP */
    private static final String[] PARTY_IDS     = { "HSP", "MNP", "LDP" };
    private static final String[] PARTY_NAMES   = {
        "HALKCI SECIM PARTISI",
        "MILLI NIZAM PARTISI",
        "LIBERAL DEMOKRAT PARTI"
    };
    private static final String[] PARTY_SLOGANS = {
        "SESINIZI DUYURUYORUZ.",
        "DUZEN VE ISTIKRAR.",
        "OZGURLUK, KALKINMA, REFAH."
    };
    /** Hangi parti Perde 1'e sahip (true = oynanabilir) */
    private static final boolean[] PARTY_ACTIVE = { true, true, false };

    // ── Karakter görseli isimleri ─────────────────────────────────────────
    // Yükleme sırasına göre mTexChar[] dizisine eşleniyor

    private static final String[] CHAR_NAMES = {
        "inspector_mehmet",  // 0
        "secretary_leyla",   // 1
        "general_rifat",     // 2
        "imam_nureddin",     // 3
        "journalist_can",    // 4
        "spy_x",             // 5
        "old_voter_ali",     // 6
        "student_zeynep"     // 7
    };

    // ── Shader kaynak kodları ─────────────────────────────────────────────

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

    // ── GL handles ───────────────────────────────────────────────────────

    private int mProgCol, mLocAPos, mLocURes, mLocUColor;
    private int mProgTex, mLocTAPos, mLocTAUV, mLocTURes, mLocTUTex, mLocTUAlpha;
    private FloatBuffer mVtxBuf;

    // ── Texture ID'leri ───────────────────────────────────────────────────

    private int   mTexLogo      = -1;
    private int   mTexMenuBg    = -1;
    private int   mTexPartyBg   = -1;
    private int   mTexOppBg     = -1;  // opposition_bg.png
    private int   mTexGovBg     = -1;  // government_bg.png
    private int[] mTexCinema    = new int[0];
    private int[] mTexChar      = new int[CHAR_NAMES.length]; // karakter görselleri
    private int[] mTexPartyLogo = new int[3];                 // logo_hsp/mnp/ldp.png

    // ── Ekran & Font ──────────────────────────────────────────────────────

    private float mW, mH, mPad, mUsableW;
    private float gPS;

    // ── Oyun durumu ───────────────────────────────────────────────────────

    private int   mState = ST_SPLASH;
    private int   mFrame = 0;

    // Splash
    private float mSplashAlpha = 0f;
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
    private static final String[] MENU_LABELS = {"YENI OYUN","DEVAM ET","AYARLAR","CIKIS"};

    // Ayarlar
    private int     mSetHover = -1, mSetPress = -1;
    private float   mVolume   = 0.75f;
    private boolean mSliderDragging = false;
    private int     mFps = 60;
    private boolean mMusicStarted = false;

    // Font Test
    private static final String[] FONT_TEST_LINES = {
        "ABCC\u00C7DEFGG\u011EHHII\u0130",
        "JKLMNOO\u00D6PRSS\u015ETUU\u00DCVYZ",
        "abc\u00E7defg\u011Fh\u0131ijklmn",
        "oo\u00F6prs\u015Ftuu\u00FCvyz",
        "0123456789",
        ". , ! ? : ( ) \" ' - + = /"
    };
    private int  mFtChar = 0;
    private long mFtLast = 0;
    private boolean mFtDone = false;
    private static final long FT_CHAR_MS = 40;

    // Kart swipe
    private float   mCardSwipeX      = 0f;
    private boolean mCardSwiping     = false;
    private float   mCardSwipeStartX = 0f;
    private int[]   mCardEffectLeft  = {0,0,0,0};
    private int[]   mCardEffectRight = {0,0,0,0};
    private static final String[] BAR_LABELS = {"HALK","DIN","PARA","ORDU"};

    // Senaryo & GameState
    private final ScenarioEngine mEngine;
    private final GameState      mGameState;

    // Felaket sonu
    private String  mEndingTitle = "";
    private String  mEndingBody  = "";
    private int     mEndingChar  = 0;
    private long    mEndingLast  = 0;
    private boolean mEndingDone  = false;
    private static final long ENDING_CHAR_MS = 35;

    // Seçim
    private int mElectionResult = -1;
    private int mElectionScore  = 0;

    // Touch (class seviyesinde — thread safe)
    private int   mTouchAction = -1;
    private float mTouchX      = 0f;
    private float mTouchY      = 0f;

    // Parti seçim ekranı
    private int mPartyHover = -1, mPartyPress = -1;

    // "Yakında" ekranı
    private String mComingSoonParty = "";

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

        // Varsayılan yükleme
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
        mLocAPos   = GLES20.glGetAttribLocation(mProgCol,"aPos");
        mLocURes   = GLES20.glGetUniformLocation(mProgCol,"uRes");
        mLocUColor = GLES20.glGetUniformLocation(mProgCol,"uColor");

        mProgTex    = buildProgram(VERT_TEX_SRC, FRAG_TEX_SRC);
        mLocTAPos   = GLES20.glGetAttribLocation(mProgTex,"aPos");
        mLocTAUV    = GLES20.glGetAttribLocation(mProgTex,"aUV");
        mLocTURes   = GLES20.glGetUniformLocation(mProgTex,"uRes");
        mLocTUTex   = GLES20.glGetUniformLocation(mProgTex,"uTex");
        mLocTUAlpha = GLES20.glGetUniformLocation(mProgTex,"uAlpha");

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Ana textureler
        mTexLogo    = loadTexture("logo.png");
        mTexMenuBg  = loadTexture("menu_bg.png");
        mTexPartyBg = loadTexture("party_select_bg.png");
        mTexOppBg   = loadTexture("opposition_bg.png");
        mTexGovBg   = loadTexture("government_bg.png");

        // Sinematik
        mTexCinema = new int[mCinemaAssets.length];
        for (int i = 0; i < mCinemaAssets.length; i++)
            mTexCinema[i] = loadTexture(mCinemaAssets[i]);

        // Karakter görselleri (yoksa -1 kalır, placeholder çizilir)
        for (int i = 0; i < CHAR_NAMES.length; i++)
            mTexChar[i] = loadTexture("characters/" + CHAR_NAMES[i] + ".png");

        // Parti logoları
        mTexPartyLogo[0] = loadTexture("logos/logo_hsp.png");
        mTexPartyLogo[1] = loadTexture("logos/logo_mnp.png");
        mTexPartyLogo[2] = loadTexture("logos/logo_ldp.png");

        mCinemaLast = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        mW = w; mH = h;
        GLES20.glViewport(0,0,w,h);
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
        mTouchAction = action; mTouchX = x; mTouchY = y;
        switch (mState) {
            case ST_SPLASH:       touchSplash(action);              break;
            case ST_CINEMA:       touchCinema(action,x,y,time);     break;
            case ST_MENU:         touchMenu(action,x,y);            break;
            case ST_SETTINGS:     touchSettings(action,x,y);        break;
            case ST_FONTTEST:     touchFontTest(action,x,y);        break;
            case ST_GAME:         touchGame(action,x,y);            break;
            case ST_ENDING:       touchEnding(action);              break;
            case ST_ELECTION:     touchElection(action,x,y);        break;
            case ST_PARTY_SELECT: touchPartySelect(action,x,y);     break;
            case ST_COMING_SOON:  touchComingSoon(action);          break;
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
        else if (mFrame > SPLASH_FRAMES-30)   mSplashAlpha = (SPLASH_FRAMES-mFrame)/30f;
        else                                  mSplashAlpha = 1f;
        if (mFrame >= SPLASH_FRAMES) {
            mFrame=0; mState=ST_MENU;
            mMenuHover=mMenuPress=-1;
            triggerMusicStart();
        }
    }

    private void updateCinema() {
        if (mCinemaDone || mCinemaText.length==0) return;
        long now = System.currentTimeMillis();
        if (now-mCinemaLast >= TYPEWRITER_MS) {
            mCinemaLast=now;
            String text = mCinemaText[mCinemaScene];
            if (mCinemaChar < text.length()) mCinemaChar++;
            else mCinemaDone=true;
        }
    }

    private void triggerMusicStart() {
        if (!mMusicStarted && mMusic!=null) { mMusicStarted=true; mMusic.onMusicStart(); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER ANA METODU
    // ══════════════════════════════════════════════════════════════════════

    private void render() {
        GLES20.glClearColor(C_BG[0],C_BG[1],C_BG[2],1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        switch (mState) {
            case ST_SPLASH:       renderSplash();        break;
            case ST_MENU:         renderMenu();           break;
            case ST_CINEMA:       renderCinema();         break;
            case ST_SETTINGS:     renderSettings();       break;
            case ST_FONTTEST:     renderFontTest();       break;
            case ST_GAME:         renderGame();           break;
            case ST_ENDING:       renderEnding();         break;
            case ST_ELECTION:     renderElection();       break;
            case ST_PARTY_SELECT: renderPartySelect();    break;
            case ST_COMING_SOON:  renderComingSoon();     break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SPLASH
    // ══════════════════════════════════════════════════════════════════════

    private void renderSplash() {
        float cx=mW/2f, cy=mH/2f, a=mSplashAlpha;
        if (mTexLogo>0) {
            float lw=mUsableW*0.55f;
            drawTex(mTexLogo,cx-lw/2f,cy-lw/2f-mH*0.06f,lw,lw,a);
        }
        float ps=fitTextPS("BONECASTOFFICIAL",mUsableW*0.88f,gPS);
        float[] col={C_GOLD[0],C_GOLD[1],C_GOLD[2],a};
        drawStringC("BONECASTOFFICIAL",cy+mH*0.09f,ps,col);
        float prog=Math.min(1f,mFrame/(float)SPLASH_FRAMES);
        float bw=mUsableW*0.50f, bh=gPS*1.5f, bx=cx-bw/2f, by=mH*0.86f;
        float[] dc={C_DARK[0],C_DARK[1],C_DARK[2],a};
        float[] fc={C_GOLD[0],C_GOLD[1],C_GOLD[2],a};
        rect(bx,by,bw,bh,dc); if(prog>0) rect(bx,by,bw*prog,bh,fc);
        scanLines();
    }

    private void touchSplash(int action) {
        if (action==MotionEvent.ACTION_DOWN) mFrame=SPLASH_FRAMES;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANA MENÜ
    // ══════════════════════════════════════════════════════════════════════

    private void renderMenu() {
        if (mTexMenuBg>0) {
            drawTex(mTexMenuBg,0,0,mW,mH,0.72f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.50f}; rect(0,0,mW,mH,ov);
        }
        float cx=mW/2f;
        float titlePS=fitTextPS("MUHUR",mUsableW*0.80f,gPS*2.0f);
        drawStringC("MUHUR",mH*0.16f,titlePS,C_GOLD);
        drawStringCWrapped("KADERIN, MUHURUN UCUNDA.",mH*0.29f,gPS*0.68f,C_GDIM);
        float devPS=fitTextPS("BONECASTOFFICIAL",mUsableW*0.68f,gPS*0.58f);
        drawStringC("BONECASTOFFICIAL",mH*0.36f,devPS,C_GREY);
        float bw=mUsableW*0.72f, bh=Math.max(gPS*8.5f,mH*0.065f);
        float bx=cx-bw/2f, gap=bh+gPS*2.5f, sy=mH*0.46f;
        for (int i=0;i<MENU_LABELS.length;i++)
            drawButton(MENU_LABELS[i],bx,sy+i*gap,bw,bh,mMenuHover==i,mMenuPress==i,i==1);
        float[] ga={C_GREY[0],C_GREY[1],C_GREY[2],0.45f};
        drawString("v0.2.0",mPad,mH-gPS*5f,gPS*0.62f,ga);
        scanLines();
    }

    private void touchMenu(int action, float x, float y) {
        float cx=mW/2f, bw=mUsableW*0.72f, bh=Math.max(gPS*8.5f,mH*0.065f);
        float bx=cx-bw/2f, gap=bh+gPS*2.5f, sy=mH*0.46f;
        if (action==MotionEvent.ACTION_DOWN) {
            mMenuPress=-1;
            for (int i=0;i<MENU_LABELS.length;i++)
                if (hit(x,y,bx,sy+i*gap,bw,bh)) { mMenuPress=i; mMenuHover=i; return; }
        } else if (action==MotionEvent.ACTION_MOVE) {
            mMenuHover=-1;
            for (int i=0;i<MENU_LABELS.length;i++)
                if (hit(x,y,bx,sy+i*gap,bw,bh)) { mMenuHover=i; break; }
        } else if (action==MotionEvent.ACTION_UP) {
            int p=mMenuPress; mMenuPress=-1; mMenuHover=-1;
            if (p<0||!hit(x,y,bx,sy+p*gap,bw,bh)) return;
            switch(p) {
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
        if (mCinemaText.length==0) { startPartySelect(); return; }
        mState=ST_CINEMA;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SİNEMATİK
    // ══════════════════════════════════════════════════════════════════════

    private void renderCinema() {
        int bg=(mCinemaScene<mTexCinema.length)?mTexCinema[mCinemaScene]:-1;
        if (bg>0) {
            drawTex(bg,0,0,mW,mH,0.82f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.52f}; rect(0,0,mW,mH,ov);
        } else rect(0,0,mW,mH,C_BG);
        float snPS=gPS*0.72f; float[] ga={C_GREY[0],C_GREY[1],C_GREY[2],0.75f};
        String scn=(mCinemaScene+1)+"/"+mCinemaText.length;
        drawString(scn,mW-mPad-charWidth(scn,snPS),mPad*1.5f,snPS,ga);
        String full=mCinemaText[mCinemaScene];
        String vis=full.substring(0,Math.min(mCinemaChar,full.length()));
        float textPS=gPS*0.90f, maxW=mUsableW*0.86f;
        List<String> lines=wrapText(vis,maxW,textPS);
        float lineH=textPS*11f, textY=mH*0.52f-lines.size()*lineH/2f;
        for (int i=0;i<lines.size();i++) drawStringC(lines.get(i),textY+i*lineH,textPS,C_GOLD);
        if (!mCinemaDone&&(mFrame/15)%2==0) {
            String last=lines.isEmpty()?"":lines.get(lines.size()-1);
            rect(mW/2f+charWidth(last,textPS)/2f+textPS,textY+(lines.size()-1)*lineH,
                 textPS,textPS*8f,C_GOLD);
        }
        if (mCinemaDone) {
            float bw=mUsableW*0.42f,bh=Math.max(gPS*9f,mH*0.06f);
            drawButton("DEVAM",mW/2f-bw/2f,mH*0.82f,bw,bh,mSetHover==99,false,false);
        }
        float sw=mUsableW*0.30f,sh=Math.max(gPS*7f,mH*0.05f);
        float sx=mPad,sy=mH-sh-mPad*2f;
        drawButton("ATLA",sx,sy,sw,sh,false,mSkipHeld,false);
        if (mSkipHeld) {
            long held=System.currentTimeMillis()-mSkipStart;
            float prog=Math.min(1f,held/(float)SKIP_HOLD_MS);
            rect(sx,sy+sh+gPS*0.5f,sw*prog,gPS*1.2f,C_GOLD);
            if (held>=SKIP_HOLD_MS) startPartySelect();
        }
        scanLines();
    }

    private void touchCinema(int action, float x, float y, long time) {
        float sw=mUsableW*0.30f,sh=Math.max(gPS*7f,mH*0.05f);
        float sx=mPad,sy=mH-sh-mPad*2f;
        float bw=mUsableW*0.42f,bh=Math.max(gPS*9f,mH*0.06f);
        float bx=mW/2f-bw/2f,by=mH*0.82f;
        if (action==MotionEvent.ACTION_DOWN) {
            if (hit(x,y,sx,sy,sw,sh)) { mSkipHeld=true; mSkipStart=System.currentTimeMillis(); return; }
            if (!mCinemaDone) { mCinemaChar=mCinemaText[mCinemaScene].length(); mCinemaDone=true; return; }
            if (hit(x,y,bx,by,bw,bh)) nextCinema();
        } else if (action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) {
            mSkipHeld=false; mSetHover=-1;
        }
    }

    private void nextCinema() {
        mCinemaScene++;
        if (mCinemaScene>=mCinemaText.length) startPartySelect();
        else { mCinemaChar=0; mCinemaDone=false; mCinemaLast=System.currentTimeMillis(); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AYARLAR
    // ══════════════════════════════════════════════════════════════════════

    private float sliderX(){return mW/2f-sliderW()/2f;}
    private float sliderW(){return mUsableW*0.85f;}
    private float sliderY(){return mH*0.42f;}
    private float sliderH(){return Math.max(gPS*3f,6f);}
    private float thumbR(){return Math.max(gPS*4.5f,10f);}
    private float volToThumbX(){return sliderX()+mVolume*sliderW();}

    private void renderSettings() {
        if (mTexMenuBg>0) {
            drawTex(mTexMenuBg,0,0,mW,mH,0.5f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.65f}; rect(0,0,mW,mH,ov);
        }
        float cx=mW/2f;
        float tPS=fitTextPS("AYARLAR",mUsableW*0.60f,gPS*1.6f);
        drawStringC("AYARLAR",mH*0.14f,tPS,C_GOLD);
        float labPS=gPS*0.72f,labY=mH*0.34f;
        drawString("SES SEVIYESI:",mPad,labY,labPS,C_GOLD);
        int pct=Math.round(mVolume*100f); String ps="%"+pct;
        float pPS=fitTextPS(ps,mUsableW*0.18f,labPS);
        drawString(ps,mW-mPad-charWidth(ps,pPS),labY,pPS,C_GOLD);
        float sx=sliderX(),sw=sliderW(),sy=sliderY(),sh=sliderH(),tr=thumbR();
        float tx=volToThumbX(),brd=Math.max(1.5f,gPS*0.3f);
        rect(sx,sy,sw,sh,C_DARK); if(mVolume>0) rect(sx,sy,mVolume*sw,sh,C_GOLD);
        rect(sx,sy,sw,brd,C_GDIM); rect(sx,sy+sh-brd,sw,brd,C_GDIM);
        rect(sx,sy,brd,sh,C_GDIM); rect(sx+sw-brd,sy,brd,sh,C_GDIM);
        rect(tx-tr,sy+sh/2f-tr,tr*2f,tr*2f,mSliderDragging?C_GOLD:C_GDIM);
        float togW=mUsableW*0.28f,togH=Math.max(gPS*9f,mH*0.060f);
        float togX0=cx-togW-mPad*0.5f,togX1=cx+mPad*0.5f,r2Y=mH*0.56f;
        drawString("FPS:",mPad,r2Y,labPS,C_GOLD);
        drawButton("30",togX0,r2Y,togW,togH,mSetHover==2,mSetPress==2,mFps!=30);
        drawButton("60",togX1,r2Y,togW,togH,mSetHover==3,mSetPress==3,mFps!=60);
        float backW=mUsableW*0.45f,backH=Math.max(gPS*9f,mH*0.060f);
        drawButton("GERI",cx-backW/2f,mH*0.76f,backW,backH,mSetHover==10,mSetPress==10,false);
        scanLines();
    }

    private void touchSettings(int action, float x, float y) {
        float cx=mW/2f,togW=mUsableW*0.28f,togH=Math.max(gPS*9f,mH*0.060f);
        float togX0=cx-togW-mPad*0.5f,togX1=cx+mPad*0.5f,r2Y=mH*0.56f;
        float backW=mUsableW*0.45f,backH=Math.max(gPS*9f,mH*0.060f);
        float backX=cx-backW/2f,backY=mH*0.76f;
        float sx=sliderX(),sw=sliderW(),sy=sliderY(),sh=sliderH(),tr=thumbR();
        float tx=volToThumbX(),hp=tr*1.4f;
        if (action==MotionEvent.ACTION_DOWN) {
            if (x>=tx-hp&&x<=tx+hp&&y>=sy+sh/2f-hp&&y<=sy+sh/2f+hp) {
                mSliderDragging=true; updateSlider(x,sx,sw); return;
            }
            mSetPress=-1;
            if      (hit(x,y,togX0,r2Y,togW,togH)) mSetPress=2;
            else if (hit(x,y,togX1,r2Y,togW,togH)) mSetPress=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetPress=10;
            mSetHover=mSetPress;
        } else if (action==MotionEvent.ACTION_MOVE) {
            if (mSliderDragging) { updateSlider(x,sx,sw); return; }
            mSetHover=-1;
            if      (hit(x,y,togX0,r2Y,togW,togH)) mSetHover=2;
            else if (hit(x,y,togX1,r2Y,togW,togH)) mSetHover=3;
            else if (hit(x,y,backX,backY,backW,backH)) mSetHover=10;
        } else if (action==MotionEvent.ACTION_UP) {
            if (mSliderDragging) { mSliderDragging=false; updateSlider(x,sx,sw); return; }
            int p=mSetPress; mSetPress=-1; mSetHover=-1;
            switch(p) { case 2:mFps=30;break; case 3:mFps=60;break;
                case 10:if(hit(x,y,backX,backY,backW,backH))mState=ST_MENU;break; }
        } else if (action==MotionEvent.ACTION_CANCEL) {
            mSliderDragging=false; mSetPress=-1; mSetHover=-1;
        }
    }

    private void updateSlider(float tx,float trackX,float trackW) {
        mVolume=Math.max(0f,Math.min(1f,(tx-trackX)/trackW));
        if (mMusic!=null) mMusic.onVolumeChanged(mVolume);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT TEST
    // ══════════════════════════════════════════════════════════════════════

    private int ftTotal(){int n=0;for(String s:FONT_TEST_LINES)n+=s.length();return n;}

    private void updateFontTest() {
        if (mFtDone) return;
        long now=System.currentTimeMillis();
        if (now-mFtLast>=FT_CHAR_MS){mFtLast=now;if(mFtChar<ftTotal())mFtChar++;else mFtDone=true;}
    }

    private void renderFontTest() {
        rect(0,0,mW,mH,C_BG);
        float sy=mH*0.06f,maxLH=(mH*0.72f)/FONT_TEST_LINES.length;
        int left=mFtChar;
        for (int li=0;li<FONT_TEST_LINES.length;li++) {
            String line=FONT_TEST_LINES[li]; float ry=sy+li*maxLH;
            float ps=fitTextPS(line,mUsableW*0.92f,gPS*1.05f);
            int show=Math.min(left,line.length()); left-=show;
            if (show<=0) break;
            String vis=line.substring(0,show);
            float[] bc={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.25f};
            rect(mPad,ry+ps*8f,mUsableW,Math.max(1f,gPS*0.3f),bc);
            drawString(vis,mPad,ry,ps,C_GOLD);
            if (show<line.length()&&(mFrame/12)%2==0)
                rect(mPad+charWidth(vis,ps),ry,ps*0.8f,ps*9f,C_GOLD);
        }
        float infoPS=gPS*0.58f; float[] ic={C_GREY[0],C_GREY[1],C_GREY[2],0.70f};
        if (!mFtDone) drawString("YAZILIYOR...",mPad,mH*0.90f,infoPS,ic);
        else {
            float bw=mUsableW*0.50f,bh=Math.max(gPS*9f,mH*0.058f);
            drawButton("DEVAM",mW/2f-bw/2f,mH*0.88f,bw,bh,false,false,false);
        }
        float[] sc={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.12f};
        rect(0,0,mPad*4f,mPad*4f,sc);
        scanLines();
    }

    private void touchFontTest(int action, float x, float y) {
        if (action!=MotionEvent.ACTION_UP) return;
        if (hit(x,y,0,0,mPad*4f,mPad*4f)) { startPartySelect(); return; }
        if (!mFtDone) { mFtChar=ftTotal(); mFtDone=true; }
        else startPartySelect();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARTİ SEÇİM EKRANI — Rev 9: Logolar + kilitli parti gösterimi
    // ══════════════════════════════════════════════════════════════════════

    private void startPartySelect() {
        mPartyHover=-1; mPartyPress=-1; mState=ST_PARTY_SELECT;
    }

    private void renderPartySelect() {
        int bgT=(mTexPartyBg>0)?mTexPartyBg:mTexMenuBg;
        if (bgT>0) {
            drawTex(bgT,0,0,mW,mH,0.55f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.62f}; rect(0,0,mW,mH,ov);
        } else rect(0,0,mW,mH,C_BG);

        float cx=mW/2f;
        float tPS=fitTextPS("PARTINIZI SECIN",mUsableW*0.82f,gPS*1.2f);
        drawStringC("PARTINIZI SECIN",mH*0.07f,tPS,C_GOLD);
        float subPS=gPS*0.58f; float[] subC={C_GREY[0],C_GREY[1],C_GREY[2],0.70f};
        drawStringC("1999 - TURKIYE GENEL SECIMLER",mH*0.15f,subPS,subC);

        float cardW=mUsableW*0.90f, cardH=mH*0.175f;
        float cardX=cx-cardW/2f, gap=gPS*4f, startY=mH*0.23f;

        for (int i=0;i<PARTY_IDS.length;i++) {
            float cardY=startY+i*(cardH+gap);
            boolean hover=(mPartyHover==i&&PARTY_ACTIVE[i]);
            boolean pressed=(mPartyPress==i&&PARTY_ACTIVE[i]);
            boolean locked=!PARTY_ACTIVE[i];

            // Kart zemin
            float[] bgC=pressed?C_PRES:(hover?C_DARK:C_BG);
            if (locked) { bgC=new float[]{C_BG[0]*0.6f,C_BG[1]*0.6f,C_BG[2]*0.6f,1f}; }
            rect(cardX,cardY,cardW,cardH,bgC);

            // Sol renkli şerit
            float stripW=gPS*2.2f, sa=locked?0.25f:(hover?1.0f:0.55f);
            float[] sC={C_GOLD[0]*(1f-i*0.18f),C_GOLD[1]*(1f-i*0.08f),C_GOLD[2]*(0.4f+i*0.15f),sa};
            rect(cardX,cardY,stripW,cardH,sC);

            // Logo (varsa)
            if (mTexPartyLogo[i]>0) {
                float logoS=cardH*0.65f;
                float logoX=cardX+stripW+gPS*1.5f;
                float logoY=cardY+cardH/2f-logoS/2f;
                drawTex(mTexPartyLogo[i],logoX,logoY,logoS,logoS,locked?0.35f:0.90f);
            }

            // Çerçeve
            float b=Math.max(1.5f,gPS*0.35f);
            float[] brdC=locked?C_GREY:(hover||pressed)?C_GOLD:C_GDIM;
            rect(cardX,cardY,cardW,b,brdC);
            rect(cardX,cardY+cardH-b,cardW,b,brdC);
            rect(cardX,cardY,b,cardH,brdC);
            rect(cardX+cardW-b,cardY,b,cardH,brdC);

            // Kısa ad
            float logoSz=(mTexPartyLogo[i]>0)?cardH*0.65f+gPS*3f:0f;
            float idX=cardX+stripW+logoSz+gPS*2f;
            float idPS=gPS*0.80f;
            float[] idC=locked?C_GREY:(hover?C_GOLD:C_GDIM);
            drawString(PARTY_IDS[i],idX,cardY+gPS*1.5f,idPS,idC);

            // Tam ad
            float namePS=fitTextPS(PARTY_NAMES[i],(cardX+cardW-b)-idX-gPS*2f,gPS*0.62f);
            float[] nameC=locked?C_GREY:C_GOLD;
            float nameX=idX+charWidth(PARTY_IDS[i],idPS)+gPS*2.5f;
            drawString(PARTY_NAMES[i],nameX,cardY+gPS*1.5f,namePS,nameC);

            // Slogan veya "Yakında"
            float slogPS=gPS*0.50f;
            float[] slogC={C_GDIM[0],C_GDIM[1],C_GDIM[2],locked?0.40f:(hover?0.90f:0.65f)};
            String slogTxt=locked?"YAKIN ZAMANDA...":PARTY_SLOGANS[i];
            drawString(slogTxt,nameX,cardY+gPS*1.5f+namePS*11f+gPS,slogPS,slogC);

            // Kilit ikonu (kilitliyse)
            if (locked) {
                String lockStr="[KILITLI]";
                float lkPS=gPS*0.52f;
                float lkW=charWidth(lockStr,lkPS);
                float[] lkC={C_RED[0],C_RED[1],C_RED[2],0.70f};
                drawString(lockStr,cardX+cardW-b-lkW-gPS*2f,cardY+cardH/2f-lkPS*4.5f,lkPS,lkC);
            }

            // "SEÇ >" hover'da
            if (hover&&!locked) {
                float aPS=gPS*0.60f; String aStr="SEC >";
                float aW=charWidth(aStr,aPS);
                drawString(aStr,cardX+cardW-b-aW-gPS*2f,cardY+cardH/2f-aPS*4.5f,aPS,C_GOLD);
            }
        }

        float iPS=gPS*0.54f; float[] iC={C_GREY[0],C_GREY[1],C_GREY[2],0.50f};
        drawStringC("BIR PARTIYE DOKUN VE SEC",mH*0.88f,iPS,iC);

        float backW=mUsableW*0.40f,backH=Math.max(gPS*8f,mH*0.054f);
        float backX=cx-backW/2f,backY=mH*0.92f;
        drawButton("GERI",backX,backY,backW,backH,false,false,false);

        scanLines();
    }

    private void touchPartySelect(int action, float x, float y) {
        float cx=mW/2f, cardW=mUsableW*0.90f, cardH=mH*0.175f;
        float cardX=cx-cardW/2f, gap=gPS*4f, startY=mH*0.23f;
        float backW=mUsableW*0.40f, backH=Math.max(gPS*8f,mH*0.054f);
        float backX=cx-backW/2f, backY=mH*0.92f;

        if (action==MotionEvent.ACTION_DOWN) {
            mPartyPress=-1;
            for (int i=0;i<PARTY_IDS.length;i++) {
                if (!PARTY_ACTIVE[i]) continue;
                if (hit(x,y,cardX,startY+i*(cardH+gap),cardW,cardH))
                    { mPartyPress=i; mPartyHover=i; return; }
            }
        } else if (action==MotionEvent.ACTION_MOVE) {
            mPartyHover=-1;
            for (int i=0;i<PARTY_IDS.length;i++) {
                if (!PARTY_ACTIVE[i]) continue;
                if (hit(x,y,cardX,startY+i*(cardH+gap),cardW,cardH)) { mPartyHover=i; break; }
            }
        } else if (action==MotionEvent.ACTION_UP) {
            int p=mPartyPress; mPartyPress=-1; mPartyHover=-1;
            if (hit(x,y,backX,backY,backW,backH)) { mState=ST_MENU; return; }
            if (p>=0 && PARTY_ACTIVE[p] && hit(x,y,cardX,startY+p*(cardH+gap),cardW,cardH))
                startGameWithParty(p);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  YAKINDA EKRANI (ST_COMING_SOON)
    // ══════════════════════════════════════════════════════════════════════

    private void renderComingSoon() {
        rect(0,0,mW,mH,C_BG);
        float cx=mW/2f;
        // Üst ayırıcı efekt
        float[] lineC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.40f};
        rect(mPad*2f,mH*0.18f,mUsableW-mPad*2f,Math.max(1f,gPS*0.3f),lineC);
        rect(mPad*2f,mH*0.82f,mUsableW-mPad*2f,Math.max(1f,gPS*0.3f),lineC);

        // Başlık
        float tPS=fitTextPS("YAKIN ZAMANDA",mUsableW*0.80f,gPS*1.3f);
        drawStringC("YAKIN ZAMANDA",mH*0.10f,tPS,C_GOLD);

        // Parti logosu (varsa)
        int logoIdx=-1;
        for (int i=0;i<PARTY_IDS.length;i++)
            if (PARTY_IDS[i].equals(mComingSoonParty)) { logoIdx=i; break; }
        if (logoIdx>=0 && mTexPartyLogo[logoIdx]>0) {
            float ls=mW*0.22f;
            drawTex(mTexPartyLogo[logoIdx],cx-ls/2f,mH*0.22f,ls,ls,0.35f);
        }

        // Parti adı
        float pPS=gPS*0.90f;
        drawStringC(mComingSoonParty,mH*0.36f,pPS,C_GDIM);

        // Açıklama metni (daktilo efektsiz, sabit)
        String[] lines={
            "Bu parti icin senaryo",
            "henuz tamamlanmadi.",
            "",
            "Ilerleyen surumlerde",
            "oynanabilir hale gelecek."
        };
        float bodyPS=gPS*0.72f, bodyY=mH*0.44f;
        for (int i=0;i<lines.length;i++)
            drawStringC(lines[i],bodyY+i*bodyPS*11.5f,bodyPS,C_GOLD);

        // Geri butonu
        float bw=mUsableW*0.55f,bh=Math.max(gPS*9f,mH*0.058f);
        drawButton("GERI DON",cx-bw/2f,mH*0.86f,bw,bh,false,false,false);

        // Versiyon notu
        float[] vC={C_GREY[0],C_GREY[1],C_GREY[2],0.40f};
        drawStringC("v0.2.0",mH*0.95f,gPS*0.52f,vC);

        scanLines();
    }

    private void touchComingSoon(int action) {
        if (action==MotionEvent.ACTION_UP) startPartySelect();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN BAŞLATMA
    // ══════════════════════════════════════════════════════════════════════

    private void startGameWithParty(int party) {
        // Perde 1 var mı?
        if (!PARTY_ACTIVE[party]) {
            mComingSoonParty = PARTY_NAMES[party];
            mState = ST_COMING_SOON;
            return;
        }
        mGameState.sifirla();
        mGameState.partyChoice = party;
        String folder = "scenario/" + mGameState.getScenarioFolder();
        boolean scenOk = mEngine.loadScenario(folder+"/cards.json");
        boolean endOk  = mEngine.loadEndings(folder+"/endings.json");
        mEngine.startScenario(mGameState);
        mCardSwipeX=0f; mCardSwiping=false; mTouchAction=-1;
        mState=ST_GAME;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OYUN — KART EKRANI (ST_GAME)
    // ══════════════════════════════════════════════════════════════════════

    private void renderGame() {
        // Arka plan
        int bgTex=(mGameState.gamePhase==GameState.PHASE_RULING)?mTexGovBg:mTexOppBg;
        if (bgTex>0) {
            drawTex(bgTex,0,0,mW,mH,0.65f);
            float[] ov={C_BG[0],C_BG[1],C_BG[2],0.55f}; rect(0,0,mW,mH,ov);
        } else rect(0,0,mW,mH,C_BG);

        // Üst bar şeridi
        float barAreaY=mPad*1.2f, barAreaH=mH*0.10f;
        drawStatBars(barAreaY,barAreaH);

        ScenarioEngine.Card card=mEngine.getCurrentCard();
        if (card==null) {
            if (!mGameState.oyunBitti) triggerElectionScreen();
            scanLines(); return;
        }
        syncCardEffects(card);

        // Kart geometrisi
        float cardW=mUsableW*0.88f, cardH=mH*0.54f;
        float cardCX=mW/2f-cardW/2f, cardCY=mH*0.20f;
        float absSwipe=Math.abs(mCardSwipeX), tiltOff=mCardSwipeX*0.07f;
        float cardX=cardCX+mCardSwipeX;

        // Gölge
        float[] shC={0f,0f,0f,0.28f};
        rect(cardX+gPS,cardCY+tiltOff+gPS,cardW,cardH,shC);

        // Kart arka plan + karakter görseli
        rect(cardX,cardCY+tiltOff,cardW,cardH,C_DARK);
        drawCardCharacter(card.character,cardX,cardCY+tiltOff,cardW,cardH);

        // Kart çerçevesi
        float[] frameCol=(absSwipe>mW*0.05f)?((mCardSwipeX>0)?C_GOLD:C_GDIM):C_GDIM;
        float b=Math.max(1.5f,gPS*0.4f);
        rect(cardX,        cardCY+tiltOff,        cardW,b,frameCol);
        rect(cardX,        cardCY+tiltOff+cardH-b,cardW,b,frameCol);
        rect(cardX,        cardCY+tiltOff,        b,cardH,frameCol);
        rect(cardX+cardW-b,cardCY+tiltOff,        b,cardH,frameCol);

        // İç içerik
        float innerX=cardX+gPS*3f, innerW=cardW-gPS*6f;
        float innerY=cardCY+tiltOff+gPS*2.5f;

        // Karakter etiketi
        float charPS=gPS*0.56f;
        float[] dimC={C_GREY[0],C_GREY[1],C_GREY[2],0.80f};
        drawString(card.character.replace("_"," ").toUpperCase(),innerX,innerY,charPS,dimC);

        // Ayırıcı
        float divY=innerY+charPS*10f+gPS*0.5f;
        float[] lineC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.45f};
        rect(innerX,divY,innerW,Math.max(1f,gPS*0.25f),lineC);

        // Flavor
        float flavPS=gPS*0.60f;
        float[] flavC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.75f};
        List<String> fLines=wrapText(card.flavor,innerW,flavPS);
        float flavY=divY+gPS*1.5f;
        for (int i=0;i<Math.min(fLines.size(),2);i++)
            drawString(fLines.get(i),innerX,flavY+i*flavPS*11f,flavPS,flavC);

        // Ana metin
        float textPS=gPS*0.72f;
        float textY=flavY+Math.min(fLines.size(),2)*flavPS*11f+gPS*2f;
        List<String> tLines=wrapText(card.text,innerW,textPS);
        for (int i=0;i<Math.min(tLines.size(),6);i++)
            drawString(tLines.get(i),innerX,textY+i*textPS*11f,textPS,C_GOLD);

        // EVET/HAYIR etiketi
        if (absSwipe>mW*0.04f) {
            boolean goRight=(mCardSwipeX>0);
            String lbl=goRight?"EVET":"HAYIR";
            float la=Math.min(1f,absSwipe/(mW*0.14f));
            float[] lc=goRight?new float[]{C_GOLD[0],C_GOLD[1],C_GOLD[2],la}
                              :new float[]{C_GDIM[0],C_GDIM[1],C_GDIM[2],la};
            float lPS=fitTextPS(lbl,cardW*0.55f,gPS*2.0f);
            float lW=charWidth(lbl,lPS);
            float lX=mW/2f-lW/2f+mCardSwipeX*0.3f;
            float lY=cardCY+cardH*0.40f+tiltOff;
            float pad=gPS*2.5f;
            float[] boxC={C_BG[0],C_BG[1],C_BG[2],la*0.80f};
            rect(lX-pad,lY-pad,lW+pad*2f,lPS*10f+pad*2f,boxC);
            float bl2=Math.max(1f,gPS*0.35f);
            rect(lX-pad,lY-pad,lW+pad*2f,bl2,lc);
            rect(lX-pad,lY+lPS*10f+pad-bl2,lW+pad*2f,bl2,lc);
            rect(lX-pad,lY-pad,bl2,lPS*10f+pad*2f,lc);
            rect(lX+lW+pad-bl2,lY-pad,bl2,lPS*10f+pad*2f,lc);
            drawString(lbl,lX,lY,lPS,lc);
            drawBarHintDots(goRight,barAreaY,barAreaH);
        }

        // Yön okları
        if (!mCardSwiping) {
            float[] arC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.40f};
            float arPS=gPS*0.65f;
            drawString("<",mPad,mH*0.46f,arPS,arC);
            drawString(">",mW-mPad-charWidth(">",arPS),mH*0.46f,arPS,arC);
        }

        // Seçenek etiketleri (kart altı)
        float optY=cardCY+cardH+gPS*3f, optPS=gPS*0.58f;
        float[] lOptC={(mCardSwipeX<0)?C_GOLD[0]:C_GDIM[0],
                       (mCardSwipeX<0)?C_GOLD[1]:C_GDIM[1],
                       (mCardSwipeX<0)?C_GOLD[2]:C_GDIM[2],1f};
        float[] rOptC={(mCardSwipeX>0)?C_GOLD[0]:C_GDIM[0],
                       (mCardSwipeX>0)?C_GOLD[1]:C_GDIM[1],
                       (mCardSwipeX>0)?C_GOLD[2]:C_GDIM[2],1f};
        drawString("< "+card.choiceLeft.label,mPad,optY,optPS,lOptC);
        String rStr=card.choiceRight.label+" >";
        drawString(rStr,mW-mPad-charWidth(rStr,optPS),optY,optPS,rOptC);

        // Menü butonu (küçük, sağ üst)
        float mBw=mUsableW*0.25f,mBh=Math.max(gPS*7f,mH*0.045f);
        float mBx=mW-mPad-mBw,mBy=mPad*0.5f;
        drawButton("MENU",mBx,mBy,mBw,mBh,false,false,false);

        // Alt durum çubuğu
        renderStatusBar();
        scanLines();
    }

    /**
     * Karakter görselini kart üzerinde, sağ tarafta yarı saydam gösterir.
     * Görsel yüklenmemişse — hiçbir şey çizmez (placeholder yok).
     */
    private void drawCardCharacter(String charId, float cardX, float cardY,
                                    float cardW, float cardH) {
        int texIdx=-1;
        for (int i=0;i<CHAR_NAMES.length;i++) {
            if (CHAR_NAMES[i].equals(charId)) { texIdx=i; break; }
        }
        if (texIdx<0 || mTexChar[texIdx]<=0) return;

        // Sağ taraf, kart yüksekliğinin %80'i, hafif saydam
        float charH=cardH*0.82f;
        float charW=charH*0.65f; // karakter görselleri dikey olduğu varsayımı
        float charX=cardX+cardW-charW-gPS;
        float charY=cardY+cardH*0.09f;
        drawTex(mTexChar[texIdx],charX,charY,charW,charH,0.18f);
    }

    private void syncCardEffects(ScenarioEngine.Card card) {
        mCardEffectLeft  = new int[]{card.choiceLeft.halk,card.choiceLeft.din,
                                     card.choiceLeft.para,card.choiceLeft.ordu};
        mCardEffectRight = new int[]{card.choiceRight.halk,card.choiceRight.din,
                                     card.choiceRight.para,card.choiceRight.ordu};
    }

    private void triggerElectionScreen() {
        mElectionResult=mGameState.getElectionResult();
        mElectionScore=mGameState.getElectionScore();
        mState=ST_ELECTION;
    }

    private void touchGame(int action, float x, float y) {
        float cardW=mUsableW*0.88f,cardH=mH*0.54f;
        float cardX0=mW/2f-cardW/2f,cardY0=mH*0.20f;
        float threshold=mW*0.27f;
        // Menü butonu
        float mBw=mUsableW*0.25f,mBh=Math.max(gPS*7f,mH*0.045f);
        float mBx=mW-mPad-mBw,mBy=mPad*0.5f;
        if (action==MotionEvent.ACTION_UP&&hit(x,y,mBx,mBy,mBw,mBh)) {
            mCardSwipeX=0f; mCardSwiping=false; mState=ST_MENU; return;
        }
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (hit(x,y,cardX0,cardY0,cardW,cardH))
                    { mCardSwiping=true; mCardSwipeStartX=x; mCardSwipeX=0f; }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCardSwiping) mCardSwipeX=x-mCardSwipeStartX;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCardSwiping) {
                    if      (mCardSwipeX> threshold) onCardChosen(true);
                    else if (mCardSwipeX<-threshold) onCardChosen(false);
                    mCardSwipeX=0f; mCardSwiping=false;
                }
                break;
        }
    }

    private void onCardChosen(boolean right) {
        mEngine.choose(mGameState,right);
        if (mGameState.oyunBitti) {
            ScenarioEngine.Ending e=mEngine.getEnding(mGameState);
            mEndingTitle=(e!=null)?e.title:"OYUN BITTI";
            mEndingBody=(e!=null)?e.body:"";
            mEndingChar=0; mEndingLast=System.currentTimeMillis(); mEndingDone=false;
            mState=ST_ENDING;
        } else if (mEngine.isScenarioComplete()) {
            // Perde 1 bitti ama Perde 2+ yok → "Yakında"
            mComingSoonParty=PARTY_NAMES[mGameState.partyChoice]+" - PERDE 2+";
            mState=ST_COMING_SOON;
        }
    }

    // ── Stat barları ──────────────────────────────────────────────────────

    private void drawStatBars(float areaY, float areaH) {
        int bc=BAR_LABELS.length;
        float slotW=mUsableW/(float)bc, barH=Math.max(4f,gPS*1.4f);
        float labPS=gPS*0.52f, barY=areaY+areaH*0.50f, labY=areaY+areaH*0.02f;
        int[] vals={mGameState.halk,mGameState.din,mGameState.para,mGameState.ordu};
        for (int i=0;i<bc;i++) {
            float bx=mPad+i*slotW, bw=slotW*0.84f;
            int v=vals[i], d=mGameState.getDangerLevel(v);
            float[] fC=(d==2)?C_DANGER:(d==1)?C_WARN:C_GOLD;
            rect(bx,barY,bw,barH,C_DARK);
            float fill=(v/100f)*bw; if(fill>0) rect(bx,barY,fill,barH,fC);
            float bl=Math.max(1f,gPS*0.2f);
            float[] frC={fC[0],fC[1],fC[2],0.55f};
            rect(bx,barY,bw,bl,frC); rect(bx,barY+barH-bl,bw,bl,frC);
            rect(bx,barY,bl,barH,frC); rect(bx+bw-bl,barY,bl,barH,frC);
            float[] lC=(d>0)?fC:C_GDIM;
            float lw=charWidth(BAR_LABELS[i],labPS);
            drawString(BAR_LABELS[i],bx+bw/2f-lw/2f,labY,labPS,lC);
            if (d>0) {
                String ns=String.valueOf(v); float nPS=labPS*0.85f;
                float[] nC={fC[0],fC[1],fC[2],0.90f};
                drawString(ns,bx+bw/2f-charWidth(ns,nPS)/2f,barY+barH+gPS*0.5f,nPS,nC);
            }
        }
    }

    private void drawBarHintDots(boolean goRight, float areaY, float areaH) {
        int[] eff=goRight?mCardEffectRight:mCardEffectLeft;
        int bc=BAR_LABELS.length;
        float slotW=mUsableW/(float)bc, barY=areaY+areaH*0.50f;
        float dotR=Math.max(gPS*1.5f,4f);
        for (int i=0;i<bc&&i<eff.length;i++) {
            if (eff[i]==0) continue;
            float bx=mPad+i*slotW, bw=slotW*0.84f;
            float dcx=bx+bw/2f, dcy=barY-dotR*2.4f;
            float[] db=(eff[i]>0)?C_GOLD:C_GDIM;
            float al=0.60f+0.40f*(float)Math.sin(mFrame*0.17f);
            float[] bC={db[0],db[1],db[2],al};
            rect(dcx-dotR,dcy-dotR,dotR*2f,dotR*2f,bC);
            String sign=(eff[i]>0)?"+":"-"; float sPS=gPS*0.44f;
            drawString(sign,dcx-charWidth(sign,sPS)/2f,dcy-sPS*5f,sPS,bC);
        }
    }

    private void renderStatusBar() {
        float bY=mH*0.91f, bH=mH-bY;
        float[] sC={C_GDIM[0],C_GDIM[1],C_GDIM[2],0.28f};
        rect(0,bY-Math.max(1f,gPS*0.25f),mW,Math.max(1f,gPS*0.25f),sC);
        rect(0,bY,mW,bH,C_DARK);
        float ps=gPS*0.54f;
        String yr="YIL "+mGameState.year;
        String ac="PERDE "+mGameState.currentAct;
        String ti=mGameState.getTitle();
        drawString(yr,mPad,bY+gPS*0.8f,ps,C_GOLD);
        float aw=charWidth(ac,ps);
        drawString(ac,mW/2f-aw/2f,bY+gPS*0.8f,ps,C_GDIM);
        float tw=charWidth(ti,ps);
        drawString(ti,mW-mPad-tw,bY+gPS*0.8f,ps,C_GREY);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FELAKET SONU (ST_ENDING)
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
        String vis=full.substring(0,Math.min(mEndingChar,full.length()));
        String[] parts=vis.split("\n",2);
        String tVis=parts[0];
        String bVis=(parts.length>1)?parts[1].replaceFirst("^\n",""):"";
        float tPS=fitTextPS(tVis.isEmpty()?"X":tVis,mUsableW*0.80f,gPS*1.3f);
        drawStringC(tVis,mH*0.10f,tPS,C_GOLD);
        rect(mPad*2f,mH*0.10f+tPS*10f+gPS,mUsableW-mPad*2f,Math.max(1f,gPS*0.3f),C_GDIM);
        float bPS=gPS*0.70f, bY=mH*0.10f+tPS*10f+gPS*4f;
        List<String> bLines=wrapText(bVis,mUsableW*0.86f,bPS);
        for (int i=0;i<bLines.size();i++)
            drawStringC(bLines.get(i),bY+i*bPS*11.5f,bPS,C_GOLD);
        if (!mEndingDone&&(mFrame/15)%2==0)
            rect(cx,bY+bLines.size()*bPS*11.5f,bPS,bPS*8f,C_GOLD);
        if (mEndingDone) {
            float bh=Math.max(gPS*8f,mH*0.056f);
            float bw1=mUsableW*0.44f,bw2=mUsableW*0.44f,gap=gPS*3f,by=mH*0.84f;
            float bx1=cx-gap/2f-bw1, bx2=cx+gap/2f;
            drawButton("YENIDEN",bx1,by,bw1,bh,false,false,false);
            drawButton("ANA MENU",bx2,by,bw2,bh,false,false,false);
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
    //  SEÇİM SONUCU (ST_ELECTION)
    // ══════════════════════════════════════════════════════════════════════

    private void renderElection() {
        rect(0,0,mW,mH,C_BG);
        float cx=mW/2f;
        float tPS=fitTextPS("SECIM GECESI",mUsableW*0.80f,gPS*1.2f);
        drawStringC("SECIM GECESI",mH*0.10f,tPS,C_GOLD);
        String scStr="HALK DESTEGI: %"+mElectionScore;
        drawStringC(scStr,mH*0.24f,gPS*0.80f,C_GDIM);

        String pName=mGameState.getPartyDisplayName();
        String rStr; float[] rC; String sub;
        if (mElectionResult==GameState.ELECTION_WIN) {
            rStr="KAZANDINIZ"; rC=C_GOLD;
            sub=pName+" birinci parti. Koalisyon gorusmeleri basliyor.";
        } else if (mElectionResult==GameState.ELECTION_TIE) {
            rStr="IKINCI TUR"; rC=C_WARN;
            sub="Secim ikinci tura gitti. Uc gun sonra karar.";
        } else {
            rStr="KAYBETTINIZ"; rC=C_DANGER;
            sub=pName+" ucuncu parti. Muhalefette devam.";
        }
        float rPS=fitTextPS(rStr,mUsableW*0.75f,gPS*1.6f);
        drawStringC(rStr,mH*0.36f,rPS,rC);
        drawStringCWrapped(sub,mH*0.50f,gPS*0.65f,C_GDIM);

        // İktidara Geç (kazanılırsa)
        if (mElectionResult==GameState.ELECTION_WIN) {
            float bigBw=mUsableW*0.72f,bigBh=Math.max(gPS*9f,mH*0.060f);
            drawButton("IKTIDARA GEC",cx-bigBw/2f,mH*0.64f,bigBw,bigBh,false,false,false);
        }

        float bh=Math.max(gPS*8f,mH*0.056f),bw=mUsableW*0.44f,gap=gPS*3f,by=mH*0.76f;
        float bx1=cx-gap/2f-bw, bx2=cx+gap/2f;
        drawButton("YENIDEN",bx1,by,bw,bh,false,false,false);
        drawButton("ANA MENU",bx2,by,bw,bh,false,false,false);
        scanLines();
    }

    private void touchElection(int action, float x, float y) {
        if (action!=MotionEvent.ACTION_UP) return;
        float cx=mW/2f,bh=Math.max(gPS*8f,mH*0.056f),bw=mUsableW*0.44f,gap=gPS*3f,by=mH*0.76f;
        float bx1=cx-gap/2f-bw,bx2=cx+gap/2f;
        // İktidara Geç
        if (mElectionResult==GameState.ELECTION_WIN) {
            float bigBw=mUsableW*0.72f,bigBh=Math.max(gPS*9f,mH*0.060f);
            if (hit(x,y,cx-bigBw/2f,mH*0.64f,bigBw,bigBh)) {
                // İktidar evresi "Yakında"
                mComingSoonParty="IKTIDAR EVRESI";
                mState=ST_COMING_SOON; return;
            }
        }
        if (hit(x,y,bx1,by,bw,bh)) { startGameWithParty(mGameState.partyChoice); return; }
        if (hit(x,y,bx2,by,bw,bh)) { mGameState.sifirla(); mState=ST_MENU; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER PRİMİTİFLERİ & TEXTURE
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
        int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);
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
        try {
            InputStream is=mAssets.open("src/scenario/Opposition_1/intro.md");
            byte[] buf=new byte[is.available()]; is.read(buf); is.close();
            String raw=new String(buf,"UTF-8");
            List<String> texts=new ArrayList<>(),assets=new ArrayList<>();
            for (String block:raw.split("(?m)^---\\s*$")) {
                block=block.trim(); if (!block.contains("[INTRO-")) continue;
                String bg=""; StringBuilder sc=new StringBuilder();
                for (String line:block.split("\n")) {
                    String t=line.trim();
                    if (t.startsWith("`bg:")) { bg=t.replace("`bg:","").replace("`","").trim(); continue; }
                    if (t.startsWith("**[INTRO-")||t.startsWith("#")||t.isEmpty()) continue;
                    if (!bg.isEmpty()) { if(sc.length()>0)sc.append("\n"); sc.append(t); }
                }
                if (!bg.isEmpty()&&sc.length()>0) { assets.add(bg); texts.add(sc.toString()); }
            }
            mCinemaText=texts.toArray(new String[0]);
            mCinemaAssets=assets.toArray(new String[0]);
        } catch (Exception e) { mCinemaText=new String[0]; mCinemaAssets=new String[0]; }
    }

    private boolean hit(float px,float py,float rx,float ry,float rw,float rh) {
        return px>=rx&&px<=rx+rw&&py>=ry&&py<=ry+rh;
    }

    private void rect(float x,float y,float w,float h,float[] c) {
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

    private void drawTex(int id,float x,float y,float w,float h,float alpha) {
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
        for (float y=0;y<mH;y+=step) rect(0,y,mW,Math.max(1f,step*0.35f),C_SCAN);
    }

    private void drawButton(String label,float x,float y,float w,float h,
                             boolean hover,boolean pressed,boolean disabled) {
        float[] bg=disabled?C_DARK:(pressed?C_PRES:C_DARK);
        float[] brd=disabled?C_GREY:(pressed||hover?C_GOLD:C_GDIM);
        float[] txt=disabled?C_GREY:(pressed||hover?C_GOLD:C_GDIM);
        rect(x,y,w,h,bg);
        float b=Math.max(1.5f,gPS*0.4f);
        rect(x,y,w,b,brd);rect(x,y+h-b,w,b,brd);
        rect(x,y,b,h,brd);rect(x+w-b,y,b,h,brd);
        float ps=fitTextPS(label,w*0.78f,gPS*0.80f);
        float tw=charWidth(label,ps);
        drawString(label,x+w/2f-tw/2f,y+h/2f-4.5f*ps,ps,txt);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PİXEL FONT (Rev7 orijinalinden korunuyor — hiç değişmedi)
    // ══════════════════════════════════════════════════════════════════════

    private float charWidth(String s,float ps){return s.length()*ps*8f;}
    private float fitTextPS(String text,float maxW,float maxPS){
        float n=charWidth(text,maxPS); return(n<=maxW)?maxPS:maxPS*(maxW/n);
    }
    private List<String> wrapText(String text,float maxW,float ps){
        List<String> r=new ArrayList<>();
        for (String para:text.split("\n",-1)){
            if(para.isEmpty()){r.add("");continue;}
            String[] words=para.split(" "); StringBuilder line=new StringBuilder();
            for(String w:words){
                String test=(line.length()==0)?w:line+" "+w;
                if(charWidth(test,ps)<=maxW) line=new StringBuilder(test);
                else{if(line.length()>0)r.add(line.toString());line=new StringBuilder(w);}
            }
            if(line.length()>0)r.add(line.toString());
        }
        return r;
    }
    private void drawString(String s,float x,float y,float ps,float[] c){
        float cx=x; for(int i=0;i<s.length();i++){drawChar(s.charAt(i),cx,y,ps,c);cx+=ps*8f;}
    }
    private char trUpper(char ch){
        switch(ch){case 'i':return'\u0130';case'\u0131':return'I';case'\u00FC':return'\u00DC';
        case'\u00F6':return'\u00D6';case'\u00E7':return'\u00C7';case'\u015F':return'\u015E';
        case'\u011F':return'\u011E';default:return Character.toUpperCase(ch);}
    }
    private void drawStringC(String s,float y,float ps,float[] c){
        drawString(s,mW/2f-charWidth(s,ps)/2f,y,ps,c);
    }
    private void drawStringCWrapped(String s,float sy,float ps,float[] c){
        List<String> ls=wrapText(s,mUsableW*0.88f,ps);
        float lh=ps*11f; for(int i=0;i<ls.size();i++) drawStringC(ls.get(i),sy+i*lh,ps,c);
    }
    private float T(float ps){return ps*1.5f;}
    private void pH(float x,float y,float ps,int row,float[] c){rect(x,y+row*ps,ps*5.5f,T(ps),c);}
    private void pHh(float x,float y,float ps,int row,float[] c){rect(x+ps*2.5f,y+row*ps,ps*3f,T(ps),c);}
    private void pV(float x,float y,float ps,int col,int r0,int r1,float[] c){
        rect(x+col*ps,y+r0*ps,T(ps),(r1-r0+1)*ps,c);
    }
    private void pD(float x,float y,float ps,int col,int row,float[] c){
        rect(x+col*ps,y+row*ps,T(ps),T(ps),c);
    }

    private void drawChar(char ch,float x,float y,float ps,float[] c){
        switch(ch){
            // ── BÜYÜK HARFLER (orijinal Rev7'den) ──────────────────────────
            case 'A':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);break;
            case 'B':pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,3,c);pV(x,y,ps,4,5,7,c);break;
            case 'C':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);break;
            case 'D':pV(x,y,ps,0,1,7,c);rect(x,y+ps*1,ps*3.5f,T(ps),c);rect(x,y+ps*7,ps*3.5f,T(ps),c);pV(x,y,ps,4,2,6,c);pV(x,y,ps,3,1,2,c);pV(x,y,ps,3,6,7,c);break;
            case 'E':pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,7,c);rect(x,y+4*ps,ps*4f,T(ps),c);break;
            case 'F':pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);rect(x,y+4*ps,ps*4f,T(ps),c);break;
            case 'G':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,5,7,c);rect(x+ps*2.5f,y+ps*4,ps*2.5f+T(ps),T(ps),c);break;
            case 'H':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,4,c);break;
            case 'I':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,2,1,7,c);break;
            case 'J':pH(x,y,ps,1,c);pV(x,y,ps,4,1,7,c);pV(x,y,ps,0,6,7,c);pH(x,y,ps,7,c);break;
            case 'K':pV(x,y,ps,0,1,7,c);pV(x,y,ps,2,1,2,c);pV(x,y,ps,3,1,1,c);pV(x,y,ps,1,3,5,c);pV(x,y,ps,2,5,6,c);pV(x,y,ps,3,6,7,c);break;
            case 'L':pV(x,y,ps,0,1,7,c);pH(x,y,ps,7,c);break;
            case 'M':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pV(x,y,ps,1,1,3,c);pV(x,y,ps,3,1,3,c);pV(x,y,ps,2,3,4,c);break;
            case 'N':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pV(x,y,ps,1,1,3,c);pV(x,y,ps,2,3,5,c);pV(x,y,ps,3,5,7,c);break;
            case 'O':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);break;
            case 'P':pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pV(x,y,ps,4,1,4,c);break;
            case 'Q':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,6,c);pV(x,y,ps,3,6,7,c);break;
            case 'R':pV(x,y,ps,0,1,7,c);pH(x,y,ps,1,c);pH(x,y,ps,4,c);pV(x,y,ps,4,1,4,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,4,6,7,c);break;
            case 'S':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,4,7,c);break;
            case 'T':pH(x,y,ps,1,c);pV(x,y,ps,2,1,7,c);break;
            case 'U':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,7,c);break;
            case 'V':pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,1,4,c);pV(x,y,ps,1,5,6,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,2,7,7,c);break;
            case 'W':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pV(x,y,ps,1,5,7,c);pV(x,y,ps,3,5,7,c);pV(x,y,ps,2,4,5,c);break;
            case 'X':pV(x,y,ps,0,1,2,c);pV(x,y,ps,4,1,2,c);pV(x,y,ps,1,3,3,c);pV(x,y,ps,3,3,3,c);pV(x,y,ps,2,4,4,c);pV(x,y,ps,1,5,5,c);pV(x,y,ps,3,5,5,c);pV(x,y,ps,0,6,7,c);pV(x,y,ps,4,6,7,c);break;
            case 'Y':pV(x,y,ps,0,1,3,c);pV(x,y,ps,4,1,3,c);pV(x,y,ps,1,4,4,c);pV(x,y,ps,3,4,4,c);pV(x,y,ps,2,5,7,c);break;
            case 'Z':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,3,2,3,c);pV(x,y,ps,2,4,5,c);pV(x,y,ps,1,6,7,c);pV(x,y,ps,4,1,2,c);pV(x,y,ps,0,6,7,c);break;
            // ── RAKAMLAR ────────────────────────────────────────────────────
            case '0':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pV(x,y,ps,1,2,3,c);pV(x,y,ps,2,4,4,c);pV(x,y,ps,3,5,6,c);break;
            case '1':pV(x,y,ps,2,1,7,c);pH(x,y,ps,7,c);pV(x,y,ps,1,2,3,c);break;
            case '2':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,4,c);pV(x,y,ps,0,4,7,c);break;
            case '3':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,1,7,c);break;
            case '4':pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,4,c);break;
            case '5':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,4,7,c);break;
            case '6':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,4,7,c);break;
            case '7':pH(x,y,ps,1,c);pV(x,y,ps,4,1,4,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,2,7,7,c);break;
            case '8':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);break;
            case '9':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,1,7,c);break;
            // ── NOKTALAMA ────────────────────────────────────────────────────
            case '.':pD(x,y,ps,2,7,c);break;
            case ',':pD(x,y,ps,2,7,c);pD(x,y,ps,1,8,c);break;
            case ':':pD(x,y,ps,2,3,c);pD(x,y,ps,2,5,c);break;
            case '!':pV(x,y,ps,2,1,5,c);pD(x,y,ps,2,7,c);break;
            case '?':pH(x,y,ps,1,c);pV(x,y,ps,4,1,3,c);pV(x,y,ps,2,3,4,c);pV(x,y,ps,3,3,4,c);pD(x,y,ps,2,6,c);break;
            case '/':pV(x,y,ps,4,1,2,c);pV(x,y,ps,3,3,4,c);pV(x,y,ps,2,5,6,c);pV(x,y,ps,1,7,7,c);break;
            case '-':rect(x+ps*0.5f,y+ps*4,ps*4f,T(ps),c);break;
            case '+':rect(x+ps*0.5f,y+ps*4,ps*4f,T(ps),c);pV(x,y,ps,2,2,6,c);break;
            case '=':rect(x+ps*0.5f,y+ps*3,ps*4f,T(ps),c);rect(x+ps*0.5f,y+ps*5,ps*4f,T(ps),c);break;
            case '(':pV(x,y,ps,1,1,7,c);rect(x+ps*1f,y+ps*1f,ps*1.5f,T(ps),c);rect(x+ps*1f,y+ps*7f,ps*1.5f,T(ps),c);pV(x,y,ps,0,2,6,c);break;
            case ')':pV(x,y,ps,3,1,7,c);rect(x+ps*2f,y+ps*1f,ps*1.5f,T(ps),c);rect(x+ps*2f,y+ps*7f,ps*1.5f,T(ps),c);pV(x,y,ps,4,2,6,c);break;
            case '%':pD(x,y,ps,0,1,c);pD(x,y,ps,1,1,c);pD(x,y,ps,0,2,c);pD(x,y,ps,1,2,c);pV(x,y,ps,3,1,2,c);pV(x,y,ps,2,3,4,c);pV(x,y,ps,1,5,6,c);pD(x,y,ps,3,5,c);pD(x,y,ps,4,5,c);pD(x,y,ps,3,6,c);pD(x,y,ps,4,6,c);break;
            case '"':pV(x,y,ps,1,1,2,c);pV(x,y,ps,3,1,2,c);break;
            case '\'':pV(x,y,ps,2,1,2,c);break;
            case '>':pD(x,y,ps,0,2,c);pD(x,y,ps,1,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,1,5,c);pD(x,y,ps,0,6,c);break;
            case '<':pD(x,y,ps,4,2,c);pD(x,y,ps,3,3,c);pD(x,y,ps,2,4,c);pD(x,y,ps,3,5,c);pD(x,y,ps,4,6,c);break;
            // ── TÜRKÇE BÜYÜK ────────────────────────────────────────────────
            case'\u00C7':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pD(x,y,ps,2,8,c);rect(x+ps*1f,y+ps*8f+T(ps)*0.5f,ps*1.5f,T(ps)*0.6f,c);break;
            case'\u011E':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,5,7,c);rect(x+ps*2.5f,y+ps*4,ps*2.5f+T(ps),T(ps),c);pD(x,y,ps,1,0,c);pD(x,y,ps,3,0,c);rect(x+ps*2f,y+T(ps)*0.4f,ps*1.5f,T(ps)*0.7f,c);break;
            case'\u0130':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,2,1,7,c);pD(x,y,ps,2,0,c);break;
            case'\u00D6':pH(x,y,ps,1,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pD(x,y,ps,1,0,c);pD(x,y,ps,3,0,c);break;
            case'\u015E':pH(x,y,ps,1,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,0,1,4,c);pV(x,y,ps,4,4,7,c);pD(x,y,ps,2,8,c);rect(x+ps*1f,y+ps*8f+T(ps)*0.5f,ps*1.5f,T(ps)*0.6f,c);break;
            case'\u00DC':pV(x,y,ps,0,1,7,c);pV(x,y,ps,4,1,7,c);pH(x,y,ps,7,c);pD(x,y,ps,1,0,c);pD(x,y,ps,3,0,c);break;
            // ── KÜÇÜK HARFLER (orijinal Rev7'den) ──────────────────────────
            case 'a':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);break;
            case 'b':pV(x,y,ps,0,2,7,c);pH(x,y,ps,4,c);pH(x,y,ps,7,c);pV(x,y,ps,4,4,7,c);break;
            case 'c':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);break;
            case'\u00E7':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pD(x,y,ps,2,8,c);rect(x+ps*1f,y+ps*8f+T(ps)*0.5f,ps*1.5f,T(ps)*0.6f,c);break;
            case 'd':pV(x,y,ps,4,2,7,c);pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);break;
            case 'e':pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,5,c);break;
            case 'f':pV(x,y,ps,1,2,7,c);pH(x,y,ps,2,c);rect(x+ps*1f,y+ps*4,ps*3f,T(ps),c);break;
            case 'g':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,8,c);pH(x,y,ps,8,c);break;
            case'\u011F':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,8,c);pH(x,y,ps,8,c);pD(x,y,ps,1,2,c);pD(x,y,ps,3,2,c);rect(x+ps*2f,y+ps*2f+T(ps)*0.4f,ps*1.5f,T(ps)*0.7f,c);break;
            case 'h':pV(x,y,ps,0,2,7,c);pH(x,y,ps,4,c);pV(x,y,ps,4,4,7,c);break;
            case'\u0131':pV(x,y,ps,2,3,7,c);break;
            case 'i':pV(x,y,ps,2,3,7,c);pD(x,y,ps,2,2,c);break;
            case 'j':pV(x,y,ps,3,3,7,c);pD(x,y,ps,3,2,c);pV(x,y,ps,0,7,8,c);rect(x+ps*1f,y+ps*8f,ps*2f,T(ps),c);break;
            case 'k':pV(x,y,ps,0,2,7,c);pV(x,y,ps,2,4,5,c);pV(x,y,ps,3,3,4,c);pV(x,y,ps,4,3,3,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,4,7,7,c);break;
            case 'l':pV(x,y,ps,2,2,7,c);break;
            case 'm':pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pV(x,y,ps,1,3,5,c);pV(x,y,ps,3,3,5,c);pV(x,y,ps,2,5,7,c);break;
            case 'n':pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,3,c);break;
            case 'o':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);break;
            case'\u00F6':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pD(x,y,ps,1,2,c);pD(x,y,ps,3,2,c);break;
            case 'p':pV(x,y,ps,0,3,8,c);pH(x,y,ps,3,c);pH(x,y,ps,6,c);pV(x,y,ps,4,3,6,c);break;
            case 'q':pV(x,y,ps,4,3,8,c);pH(x,y,ps,3,c);pH(x,y,ps,6,c);pV(x,y,ps,0,3,6,c);break;
            case 'r':pV(x,y,ps,0,3,7,c);pH(x,y,ps,3,c);pV(x,y,ps,3,3,4,c);break;
            case 's':pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,5,c);pV(x,y,ps,4,5,7,c);break;
            case'\u015F':pH(x,y,ps,3,c);pH(x,y,ps,5,c);pH(x,y,ps,7,c);pV(x,y,ps,0,3,5,c);pV(x,y,ps,4,5,7,c);pD(x,y,ps,2,8,c);rect(x+ps*1f,y+ps*8f+T(ps)*0.5f,ps*1.5f,T(ps)*0.6f,c);break;
            case 't':pV(x,y,ps,2,2,7,c);rect(x+ps*1f,y+ps*3,ps*3f,T(ps),c);break;
            case 'u':pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,7,c);break;
            case'\u00FC':pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pH(x,y,ps,7,c);pD(x,y,ps,1,2,c);pD(x,y,ps,3,2,c);break;
            case 'v':pV(x,y,ps,0,3,5,c);pV(x,y,ps,4,3,5,c);pV(x,y,ps,1,6,6,c);pV(x,y,ps,3,6,6,c);pV(x,y,ps,2,7,7,c);break;
            case 'w':pV(x,y,ps,0,3,7,c);pV(x,y,ps,4,3,7,c);pV(x,y,ps,1,5,7,c);pV(x,y,ps,3,5,7,c);pV(x,y,ps,2,4,5,c);break;
            case 'x':pV(x,y,ps,0,3,4,c);pV(x,y,ps,4,3,4,c);pV(x,y,ps,1,4,5,c);pV(x,y,ps,3,4,5,c);pV(x,y,ps,2,5,5,c);pV(x,y,ps,1,5,6,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,0,6,7,c);pV(x,y,ps,4,6,7,c);break;
            case 'y':pV(x,y,ps,0,3,5,c);pV(x,y,ps,4,3,5,c);pV(x,y,ps,1,5,6,c);pV(x,y,ps,3,5,6,c);pV(x,y,ps,2,6,8,c);break;
            case 'z':pH(x,y,ps,3,c);pH(x,y,ps,7,c);pV(x,y,ps,3,4,5,c);pV(x,y,ps,2,5,6,c);pV(x,y,ps,1,6,7,c);pV(x,y,ps,4,3,4,c);pV(x,y,ps,0,6,7,c);break;
            case ' ':break;
            default:pD(x,y,ps,2,4,c);break;
        }
    }
}

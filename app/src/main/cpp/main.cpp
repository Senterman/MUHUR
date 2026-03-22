/**
 * MÜHÜR — main.cpp
 * İlk frame: Tam altın ekran (GL çalışıyor mu test)
 * Sonraki frameler: Papers Please menü
 */

#include <android_native_app_glue.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <time.h>

#define T "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  T, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, T, __VA_ARGS__)

/* ── Renkler ── */
static const float BG[4]   = {0.051f, 0.039f, 0.020f, 1.f};
static const float GOLD[4] = {0.831f, 0.659f, 0.325f, 1.f};
static const float DARK[4] = {0.090f, 0.070f, 0.035f, 1.f};
static const float PRES[4] = {0.180f, 0.140f, 0.070f, 1.f};
static const float GREY[4] = {0.260f, 0.240f, 0.220f, 1.f};
static const float GDIM[4] = {0.420f, 0.330f, 0.160f, 1.f};

/* ── Shader ── */
static const char* VS =
    "attribute vec2 p;"
    "uniform vec2 r;"
    "void main(){"
    "vec2 n=(p/r)*2.0-1.0;"
    "gl_Position=vec4(n.x,-n.y,0.0,1.0);}";

static const char* FS =
    "precision mediump float;"
    "uniform vec4 c;"
    "void main(){gl_FragColor=c;}";

/* ── Global durum (static — stack overflow yok) ── */
static EGLDisplay g_dpy  = EGL_NO_DISPLAY;
static EGLSurface g_suf  = EGL_NO_SURFACE;
static EGLContext g_ctx  = EGL_NO_CONTEXT;
static GLuint     g_prog = 0;
static int32_t    g_W    = 0;
static int32_t    g_H    = 0;
static bool       g_ready      = false;
static bool       g_inSettings = false;
static bool       g_inBoot     = false;
static int        g_fps        = 60;
static long       g_bootStart  = 0;
static long       g_lastFrame  = 0;
static int        g_frameCount = 0;

/* Buton hit alanları */
static float g_bx[4], g_by[4], g_bw[4], g_bh[4];
static bool  g_bp[4];
static float g_sx[3], g_sy[3], g_sw2[3], g_sh2[3];

/* ── Zaman ── */
static long nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/* ── Shader ── */
static GLuint mkShader(GLenum t, const char* s) {
    GLuint h = glCreateShader(t);
    glShaderSource(h, 1, &s, nullptr);
    glCompileShader(h);
    GLint ok = 0;
    glGetShaderiv(h, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char b[256] = {};
        glGetShaderInfoLog(h, 256, nullptr, b);
        LE("shader: %s", b);
    }
    return h;
}

static GLuint mkProg() {
    GLuint v = mkShader(GL_VERTEX_SHADER,   VS);
    GLuint f = mkShader(GL_FRAGMENT_SHADER, FS);
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v);
    glDeleteShader(f);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    LI("prog=%u ok=%d", p, ok);
    return p;
}

/* ── Dikdörtgen çiz ── */
static void R(float x, float y, float w, float h, const float* col) {
    float v[] = {x,y, x+w,y, x,y+h, x+w,y+h};
    glUseProgram(g_prog);
    glUniform2f(glGetUniformLocation(g_prog, "r"), (float)g_W, (float)g_H);
    glUniform4fv(glGetUniformLocation(g_prog, "c"), 1, col);
    GLuint ap = (GLuint)glGetAttribLocation(g_prog, "p");
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap, 2, GL_FLOAT, GL_FALSE, 0, v);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(ap);
}

/* ── Pixel font: 5×9 segment stili ── */
static float gPS = 0;

/* Yatay çubuk */
static void SH(float ox, float oy, float x, float y,
               float w, const float* c) {
    R(ox+x*gPS, oy+y*gPS, w*gPS, gPS, c);
}
/* Dikey çubuk */
static void SV(float ox, float oy, float x, float y,
               float h, const float* c) {
    R(ox+x*gPS, oy+y*gPS, gPS, h*gPS, c);
}

static void drawChar(char ch, float ox, float oy, const float* c) {
    switch (ch) {
    case 'A': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,4,c);SV(ox,oy,4,1,4,c);
              SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,4,c);SV(ox,oy,4,5,4,c);break;
    case 'B': SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);
              SH(ox,oy,0,4,4,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);
              SH(ox,oy,0,8,4,c);break;
    case 'C': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'D': SH(ox,oy,0,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
              SH(ox,oy,0,8,3,c);break;
    case 'E': SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);
              SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case 'F': SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);
              SV(ox,oy,0,5,4,c);break;
    case 'G': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,2,4,3,c);
              SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case 'H': SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SH(ox,oy,1,4,3,c);break;
    case 'I': SH(ox,oy,1,0,3,c);SV(ox,oy,2,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'J': SH(ox,oy,1,0,3,c);SV(ox,oy,3,1,6,c);SV(ox,oy,0,6,2,c);
              SH(ox,oy,1,8,2,c);break;
    case 'K': SV(ox,oy,0,0,9,c);SH(ox,oy,1,4,3,c);SV(ox,oy,3,0,4,c);
              SV(ox,oy,3,5,4,c);break;
    case 'L': SV(ox,oy,0,0,8,c);SH(ox,oy,1,8,4,c);break;
    case 'M': SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SV(ox,oy,2,1,3,c);
              R(ox+gPS,oy+gPS,gPS,gPS,c);R(ox+3*gPS,oy+gPS,gPS,gPS,c);break;
    case 'N': SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);
              R(ox+gPS,oy+gPS,gPS,gPS,c);R(ox+2*gPS,oy+3*gPS,gPS,gPS,c);
              R(ox+3*gPS,oy+5*gPS,gPS,gPS,c);break;
    case 'O': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
              SH(ox,oy,1,8,3,c);break;
    case 'P': SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);
              SH(ox,oy,0,4,4,c);break;
    case 'R': SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);
              SH(ox,oy,0,4,4,c);SV(ox,oy,3,5,4,c);break;
    case 'S': SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,3,c);SH(ox,oy,1,4,3,c);
              SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,4,c);break;
    case 'T': SH(ox,oy,0,0,5,c);SV(ox,oy,2,1,8,c);break;
    case 'U': SV(ox,oy,0,0,8,c);SV(ox,oy,4,0,8,c);SH(ox,oy,1,8,3,c);break;
    case 'V': SV(ox,oy,0,0,6,c);SV(ox,oy,4,0,6,c);
              R(ox+gPS,oy+6*gPS,gPS,gPS,c);R(ox+3*gPS,oy+6*gPS,gPS,gPS,c);
              R(ox+2*gPS,oy+7*gPS,gPS,gPS,c);break;
    case 'W': SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SV(ox,oy,2,5,4,c);break;
    case 'Y': SV(ox,oy,0,0,4,c);SV(ox,oy,4,0,4,c);SH(ox,oy,1,4,3,c);
              SV(ox,oy,2,5,4,c);break;
    case 'Z': SH(ox,oy,0,0,5,c);R(ox+3*gPS,oy+2*gPS,gPS,gPS,c);
              R(ox+2*gPS,oy+4*gPS,gPS,gPS,c);R(ox+gPS,oy+6*gPS,gPS,gPS,c);
              SH(ox,oy,0,8,5,c);break;
    case '0': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
              SH(ox,oy,1,8,3,c);break;
    case '1': SV(ox,oy,2,0,9,c);break;
    case '2': SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);
              SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '3': SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);
              SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '4': SV(ox,oy,0,0,5,c);SV(ox,oy,4,0,9,c);SH(ox,oy,0,4,5,c);break;
    case '5': SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,5,c);
              SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '6': SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,4,4,c);
              SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case '7': SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,4,c);SV(ox,oy,2,5,4,c);break;
    case '8': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);
              SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);
              SH(ox,oy,1,8,3,c);break;
    case '9': SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,7,c);
              SH(ox,oy,1,4,3,c);SH(ox,oy,1,8,3,c);break;
    case ':': R(ox+2*gPS,oy+2*gPS,gPS,gPS,c);
              R(ox+2*gPS,oy+6*gPS,gPS,gPS,c);break;
    case '-': SH(ox,oy,1,4,3,c);break;
    case '.': R(ox+2*gPS,oy+8*gPS,gPS,gPS,c);break;
    case '!': SV(ox,oy,2,0,6,c);R(ox+2*gPS,oy+8*gPS,gPS,gPS,c);break;
    default:  break;
    }
}

static int charCount(const char* s) {
    int n = 0;
    const unsigned char* p = (const unsigned char*)s;
    while (*p) {
        if (*p >= 0x80) { p++; if (*p && (*p & 0xC0) == 0x80) p++; }
        else p++;
        n++;
    }
    return n;
}

static void drawStr(const char* s, float x, float y,
                    float ps, const float* col) {
    gPS = ps;
    float cx = x;
    const unsigned char* p = (const unsigned char*)s;
    while (*p) {
        if (*p >= 0x80) {
            p++;
            if (*p && (*p & 0xC0) == 0x80) p++;
            cx += 7 * ps;
            continue;
        }
        char ch = (char)*p;
        if (ch >= 'a' && ch <= 'z') ch = ch - 'a' + 'A';
        drawChar(ch, cx, y, col);
        cx += 7 * ps;
        p++;
    }
}

static void drawStrC(const char* s, float cy, float ps, const float* col) {
    float w = (float)charCount(s) * 7 * ps;
    drawStr(s, (float)g_W * 0.5f - w * 0.5f, cy, ps, col);
}

/* ── Buton çiz ── */
static void drawBtn(float x, float y, float w, float h,
                    const char* lbl, bool en, bool pr, float ps) {
    float bw = (float)g_W * 0.005f;
    const float* gc = en ? GOLD : GREY;
    float bc[4] = {gc[0], gc[1], gc[2], en ? 1.f : 0.35f};
    R(x-bw, y-bw, w+2*bw, h+2*bw, bc);
    R(x, y, w, h, pr ? PRES : DARK);
    float cs = (float)g_W * 0.010f;
    R(x,       y,       cs, cs, bc);
    R(x+w-cs,  y,       cs, cs, bc);
    R(x,       y+h-cs,  cs, cs, bc);
    R(x+w-cs,  y+h-cs,  cs, cs, bc);
    float lc[4] = {gc[0], gc[1], gc[2], en ? 1.f : 0.4f};
    float tw = (float)charCount(lbl) * 7 * ps;
    drawStr(lbl, x + (w-tw)*0.5f, y + (h - 9*ps)*0.5f, ps, lc);
}

static bool hitTest(int i, float tx, float ty) {
    return tx >= g_bx[i] && tx <= g_bx[i]+g_bw[i] &&
           ty >= g_by[i] && ty <= g_by[i]+g_bh[i];
}
static bool hitS(int i, float tx, float ty) {
    return tx >= g_sx[i] && tx <= g_sx[i]+g_sw2[i] &&
           ty >= g_sy[i] && ty <= g_sy[i]+g_sh2[i];
}

/* ════ RENDER ════ */

static void renderMenu() {
    float sw = (float)g_W, sh = (float)g_H;
    glViewport(0, 0, g_W, g_H);
    glClearColor(BG[0], BG[1], BG[2], 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    /* Tarama çizgileri */
    float sc[4] = {0, 0, 0, 0.10f};
    for (float y = 0; y < sh; y += 5.f) R(0, y, sw, 1.5f, sc);

    /* Şeritler */
    R(0, sh*0.010f, sw, sh*0.004f, GOLD);
    R(0, sh*0.974f, sw, sh*0.004f, GOLD);

    /* Başlık */
    float ps = sw * 0.011f;
    drawStrC("MUHUR", sh*0.055f, ps, GOLD);

    /* Ayraç */
    R(sw*0.08f, sh*0.155f, sw*0.84f, sh*0.002f, GDIM);

    /* Motto */
    float mps = sw * 0.007f;
    drawStrC("KADERIN MUHRUNUN UCUNDA", sh*0.165f, mps, GDIM);

    /* İkinci ayraç */
    R(sw*0.08f, sh*0.220f, sw*0.84f, sh*0.002f, GOLD);

    /* Butonlar */
    float bw  = sw * 0.72f;
    float bh  = sh * 0.080f;
    float bx  = (sw - bw) * 0.5f;
    float gap = sh * 0.022f;
    float sy  = sh * 0.250f;
    float bp  = sw * 0.013f;

    const char* lb[4] = {"YENİ OYUN", "DEVAM ET", "AYARLAR", "CIKIS"};
    bool  en[4]       = {true, false, true, true};

    for (int i = 0; i < 4; i++) {
        float fy = sy + (float)i * (bh + gap);
        g_bx[i] = bx; g_by[i] = fy;
        g_bw[i] = bw; g_bh[i] = bh;
        drawBtn(bx, fy, bw, bh, lb[i], en[i], g_bp[i], bp);
    }

    /* Alt bilgi */
    float dps = sw * 0.007f;
    drawStrC("BONECASTOFFICIAL", sh * 0.935f, dps, GDIM);
}

static void renderBoot() {
    float sw = (float)g_W, sh = (float)g_H;
    glViewport(0, 0, g_W, g_H);
    glClearColor(0, 0, 0, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    long el = nowMs() - g_bootStart;
    float prog = (float)el / 2000.f;
    if (prog > 1.f) prog = 1.f;

    float bx = sw*0.15f, by = sh*0.52f;
    float bw = sw*0.70f, bh = sh*0.018f;
    R(bx, by, bw, bh, DARK);
    R(bx, by, bw*prog, bh, GOLD);

    float ps = sw * 0.009f;
    drawStrC("SISTEM BASLATILIYOR", sh*0.42f, ps, GOLD);

    float sc[4] = {0, 0, 0, 0.10f};
    for (float y = 0; y < sh; y += 5.f) R(0, y, sw, 1.5f, sc);

    if (el > 2200) g_inBoot = false;
}

static void renderSettings() {
    float sw = (float)g_W, sh = (float)g_H;
    glViewport(0, 0, g_W, g_H);
    glClearColor(BG[0], BG[1], BG[2], 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    float sc[4] = {0, 0, 0, 0.10f};
    for (float y = 0; y < sh; y += 5.f) R(0, y, sw, 1.5f, sc);
    R(0, sh*0.010f, sw, sh*0.004f, GOLD);

    float ps = sw * 0.011f;
    drawStrC("AYARLAR", sh*0.055f, ps, GOLD);
    R(sw*0.10f, sh*0.155f, sw*0.80f, sh*0.003f, GDIM);

    float bw = sw*0.70f, bh = sh*0.080f;
    float bx = (sw-bw)*0.5f, bp = sw*0.013f;

    /* FPS 60 */
    if (g_fps == 60) {
        float hl[4] = {GOLD[0], GOLD[1], GOLD[2], 0.18f};
        R(bx, sh*0.22f, bw, bh, hl);
    }
    g_sx[0]=bx; g_sy[0]=sh*0.22f; g_sw2[0]=bw; g_sh2[0]=bh;
    drawBtn(bx, sh*0.22f, bw, bh, "FPS 60", true, false, bp);

    /* FPS 30 */
    if (g_fps == 30) {
        float hl[4] = {GOLD[0], GOLD[1], GOLD[2], 0.18f};
        R(bx, sh*0.34f, bw, bh, hl);
    }
    g_sx[1]=bx; g_sy[1]=sh*0.34f; g_sw2[1]=bw; g_sh2[1]=bh;
    drawBtn(bx, sh*0.34f, bw, bh, "FPS 30", true, false, bp);

    /* Geri */
    g_sx[2]=bx; g_sy[2]=sh*0.56f; g_sw2[2]=bw; g_sh2[2]=bh;
    drawBtn(bx, sh*0.56f, bw, bh, "GERI", true, false, bp);

    char ft[24];
    snprintf(ft, sizeof(ft), "HEDEF %d FPS", g_fps);
    drawStrC(ft, sh*0.74f, sw*0.007f, GDIM);
}

/* ════ CALLBACKS ════ */

static void onCmd(android_app* aapp, int32_t cmd) {
    switch (cmd) {

    case APP_CMD_INIT_WINDOW: {
        LI("=== INIT_WINDOW ===");
        if (!aapp->window) { LE("window null"); break; }

        g_dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (g_dpy == EGL_NO_DISPLAY) { LE("getDisplay fail"); break; }

        EGLint ma=0, mi=0;
        if (!eglInitialize(g_dpy, &ma, &mi)) { LE("eglInit fail"); break; }
        LI("EGL v%d.%d", ma, mi);

        const EGLint att[] = {
            EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,   8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE,  8,
            EGL_NONE
        };
        EGLConfig cfg; EGLint nc = 0;
        if (!eglChooseConfig(g_dpy, att, &cfg, 1, &nc) || nc == 0) {
            LE("chooseConfig fail"); break;
        }

        g_suf = eglCreateWindowSurface(g_dpy, cfg, aapp->window, nullptr);
        if (g_suf == EGL_NO_SURFACE) {
            LE("createSurface fail 0x%x", eglGetError()); break;
        }

        const EGLint ca[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        g_ctx = eglCreateContext(g_dpy, cfg, EGL_NO_CONTEXT, ca);
        if (g_ctx == EGL_NO_CONTEXT) {
            LE("createContext fail 0x%x", eglGetError()); break;
        }

        if (eglMakeCurrent(g_dpy, g_suf, g_suf, g_ctx) == EGL_FALSE) {
            LE("makeCurrent fail 0x%x", eglGetError()); break;
        }

        eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
        eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
        LI("surface %dx%d", g_W, g_H);

        /* AŞAMA 1: Düz altın renk — shader olmadan */
        glViewport(0, 0, g_W, g_H);
        glClearColor(GOLD[0]*0.4f, GOLD[1]*0.4f, GOLD[2]*0.4f, 1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(g_dpy, g_suf);
        LI("ASAMA1 clear OK");

        /* AŞAMA 2: Shader */
        g_prog = mkProg();
        if (!g_prog) { LE("prog fail"); break; }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        memset(g_bp, 0, sizeof(g_bp));
        g_frameCount = 0;
        g_lastFrame  = nowMs();
        g_ready      = true;
        LI("=== HAZIR ===");
        break;
    }

    case APP_CMD_TERM_WINDOW:
        LI("TERM_WINDOW");
        g_ready = false;
        if (g_prog) { glDeleteProgram(g_prog); g_prog = 0; }
        if (g_dpy != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_dpy, EGL_NO_SURFACE,
                           EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (g_ctx != EGL_NO_CONTEXT) eglDestroyContext(g_dpy, g_ctx);
            if (g_suf != EGL_NO_SURFACE) eglDestroySurface(g_dpy, g_suf);
            eglTerminate(g_dpy);
        }
        g_dpy = EGL_NO_DISPLAY;
        g_ctx = EGL_NO_CONTEXT;
        g_suf = EGL_NO_SURFACE;
        break;

    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if (g_dpy != EGL_NO_DISPLAY) {
            eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
            eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
            LI("resize %dx%d", g_W, g_H);
        }
        break;

    default: break;
    }
}

static int32_t onInp(android_app* aapp, AInputEvent* evt) {
    if (!g_ready) return 0;
    if (AInputEvent_getType(evt) != AINPUT_EVENT_TYPE_MOTION) return 0;

    int32_t act = AMotionEvent_getAction(evt) & AMOTION_EVENT_ACTION_MASK;
    float   tx  = AMotionEvent_getX(evt, 0);
    float   ty  = AMotionEvent_getY(evt, 0);

    if (act == AMOTION_EVENT_ACTION_DOWN) {
        if (!g_inSettings && !g_inBoot)
            for (int i = 0; i < 4; i++)
                g_bp[i] = hitTest(i, tx, ty);
        return 1;
    }

    if (act != AMOTION_EVENT_ACTION_UP) return 1;
    memset(g_bp, 0, sizeof(g_bp));

    if (g_inBoot)     { g_inBoot = false; return 1; }

    if (g_inSettings) {
        if      (hitS(0, tx, ty)) g_fps = 60;
        else if (hitS(1, tx, ty)) g_fps = 30;
        else if (hitS(2, tx, ty)) g_inSettings = false;
        return 1;
    }

    if      (hitTest(0, tx, ty)) { g_inBoot = true; g_bootStart = nowMs(); }
    else if (hitTest(2, tx, ty)) { g_inSettings = true; }
    else if (hitTest(3, tx, ty)) { ANativeActivity_finish(aapp->activity); }
    return 1;
}

/* ════ GİRİŞ NOKTASI ════ */

void android_main(android_app* aapp) {
    LI("android_main START");

    aapp->userData     = nullptr;
    aapp->onAppCmd     = onCmd;
    aapp->onInputEvent = onInp;

    while (!aapp->destroyRequested) {
        int                  ev  = 0;
        android_poll_source* src = nullptr;
        int timeout = g_ready ? 0 : -1;

        while (ALooper_pollOnce(timeout, nullptr, &ev, (void**)&src) >= 0) {
            if (src) src->process(aapp, src);
            if (aapp->destroyRequested) break;
        }

        if (!g_ready) continue;

        long now = nowMs();
        long fm  = (g_fps == 30) ? 33L : 16L;
        if (now - g_lastFrame < fm) { usleep(2000); continue; }
        g_lastFrame = now;

        if      (g_inBoot)     renderBoot();
        else if (g_inSettings) renderSettings();
        else                   renderMenu();

        eglSwapBuffers(g_dpy, g_suf);
        g_frameCount++;

        if (g_frameCount == 1) LI("İLK FRAME RENDER EDILDI");
    }

    LI("android_main END");
}

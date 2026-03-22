/**
 * MÜHÜR — main.cpp
 * Shader: tam isimler (aPos, uRes, uColor)
 * VBO tabanlı çizim
 * Splash (frame sayacı) → Ana Menü
 */

#include <android_native_app_glue.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <cstdio>
#include <cmath>

#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── Shader ── */
static const char* VERT_SRC =
"attribute vec2 aPos;\n"
"uniform   vec2 uRes;\n"
"void main() {\n"
"    vec2 ndc = (aPos / uRes) * 2.0 - vec2(1.0);\n"
"    gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);\n"
"}\n";

static const char* FRAG_SRC =
"precision mediump float;\n"
"uniform vec4 uColor;\n"
"void main() {\n"
"    gl_FragColor = uColor;\n"
"}\n";

/* ── EGL ── */
static EGLDisplay g_dpy = EGL_NO_DISPLAY;
static EGLSurface g_suf = EGL_NO_SURFACE;
static EGLContext g_ctx = EGL_NO_CONTEXT;
static int32_t    g_W = 1, g_H = 1;
static bool       g_ok = false;

/* ── GL ── */
static GLuint g_prog = 0;
static GLint  g_aPos = -1;
static GLint  g_uRes = -1;
static GLint  g_uColor = -1;
static GLuint g_vbo = 0;

/* ── State ── */
enum State { ST_SPLASH, ST_MENU, ST_SETTINGS };
static State g_state = ST_SPLASH;
static int   g_frame = 0;
static int   g_fps   = 60;
static bool  g_sndOn = true;

/* Buton alanları */
static float g_bx[4], g_by[4], g_bw[4], g_bh[4];
static bool  g_bp[4] = {};
static float g_sx[4], g_sy[4], g_sw2[4], g_sh2[4];

/* ── Renkler ── */
static const float C_BG[4]   = {0.051f, 0.039f, 0.020f, 1.0f};
static const float C_GOLD[4] = {0.831f, 0.659f, 0.325f, 1.0f};
static const float C_GDIM[4] = {0.420f, 0.330f, 0.160f, 1.0f};
static const float C_DARK[4] = {0.090f, 0.070f, 0.035f, 1.0f};
static const float C_PRES[4] = {0.180f, 0.140f, 0.070f, 1.0f};
static const float C_GREY[4] = {0.260f, 0.240f, 0.220f, 1.0f};

/* ── Shader derleme ── */
static GLuint compileShader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[512] = {};
        glGetShaderInfoLog(sh, 512, nullptr, buf);
        LE("Shader compile error: %s", buf);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

static GLuint createProgram() {
    GLuint vs = compileShader(GL_VERTEX_SHADER,   VERT_SRC);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAG_SRC);
    if (!vs || !fs) return 0;

    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char buf[256] = {};
        glGetProgramInfoLog(prog, 256, nullptr, buf);
        LE("Program link error: %s", buf);
        glDeleteProgram(prog);
        return 0;
    }
    LI("Shader program OK id=%u", prog);
    return prog;
}

/* ── Dikdörtgen çiz ── */
static void drawRect(float x, float y, float w, float h, const float* col) {
    float verts[8] = {
        x,     y,
        x + w, y,
        x,     y + h,
        x + w, y + h
    };

    glBindBuffer(GL_ARRAY_BUFFER, g_vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_DYNAMIC_DRAW);

    glEnableVertexAttribArray((GLuint)g_aPos);
    glVertexAttribPointer((GLuint)g_aPos, 2, GL_FLOAT, GL_FALSE, 0, nullptr);

    glUniform2f(g_uRes, (float)g_W, (float)g_H);
    glUniform4f(g_uColor, col[0], col[1], col[2], col[3]);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray((GLuint)g_aPos);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

/* ── Pixel font ── */
static float gPS = 0.0f;

static void segH(float ox, float oy, float x, float y, float w, const float* c) {
    drawRect(ox + x * gPS, oy + y * gPS, w * gPS, gPS, c);
}
static void segV(float ox, float oy, float x, float y, float h, const float* c) {
    drawRect(ox + x * gPS, oy + y * gPS, gPS, h * gPS, c);
}
static void segD(float ox, float oy, float x, float y, const float* c) {
    drawRect(ox + x * gPS, oy + y * gPS, gPS, gPS, c);
}

static void drawChar(char ch, float ox, float oy, const float* c) {
    switch (ch) {
    case 'A': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,4,c); segV(ox,oy,4,1,4,c);
              segH(ox,oy,1,4,3,c); segV(ox,oy,0,5,4,c); segV(ox,oy,4,5,4,c); break;
    case 'B': segH(ox,oy,0,0,4,c); segV(ox,oy,0,1,3,c); segV(ox,oy,4,1,3,c);
              segH(ox,oy,0,4,4,c); segV(ox,oy,0,5,3,c); segV(ox,oy,4,5,3,c);
              segH(ox,oy,0,8,4,c); break;
    case 'C': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,7,c); segH(ox,oy,1,8,3,c); break;
    case 'D': segH(ox,oy,0,0,3,c); segV(ox,oy,0,1,7,c); segV(ox,oy,4,1,7,c);
              segH(ox,oy,0,8,3,c); break;
    case 'E': segH(ox,oy,0,0,5,c); segV(ox,oy,0,1,3,c); segH(ox,oy,0,4,4,c);
              segV(ox,oy,0,5,3,c); segH(ox,oy,0,8,5,c); break;
    case 'F': segH(ox,oy,0,0,5,c); segV(ox,oy,0,1,3,c); segH(ox,oy,0,4,4,c);
              segV(ox,oy,0,5,4,c); break;
    case 'G': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,7,c); segH(ox,oy,2,4,3,c);
              segV(ox,oy,4,5,3,c); segH(ox,oy,1,8,3,c); break;
    case 'H': segV(ox,oy,0,0,9,c); segV(ox,oy,4,0,9,c); segH(ox,oy,1,4,3,c); break;
    case 'I': segH(ox,oy,1,0,3,c); segV(ox,oy,2,1,7,c); segH(ox,oy,1,8,3,c); break;
    case 'J': segH(ox,oy,1,0,3,c); segV(ox,oy,3,1,6,c); segV(ox,oy,0,6,2,c);
              segH(ox,oy,1,8,2,c); break;
    case 'K': segV(ox,oy,0,0,9,c); segH(ox,oy,1,4,3,c);
              segV(ox,oy,3,0,4,c); segV(ox,oy,3,5,4,c); break;
    case 'L': segV(ox,oy,0,0,8,c); segH(ox,oy,1,8,4,c); break;
    case 'M': segV(ox,oy,0,0,9,c); segV(ox,oy,4,0,9,c);
              segD(ox,oy,1,1,c); segD(ox,oy,2,2,c); segD(ox,oy,3,1,c); break;
    case 'N': segV(ox,oy,0,0,9,c); segV(ox,oy,4,0,9,c);
              segD(ox,oy,1,1,c); segD(ox,oy,2,3,c); segD(ox,oy,3,5,c); break;
    case 'O': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,7,c); segV(ox,oy,4,1,7,c);
              segH(ox,oy,1,8,3,c); break;
    case 'P': segH(ox,oy,0,0,4,c); segV(ox,oy,0,1,8,c); segV(ox,oy,4,1,3,c);
              segH(ox,oy,0,4,4,c); break;
    case 'R': segH(ox,oy,0,0,4,c); segV(ox,oy,0,1,8,c); segV(ox,oy,4,1,3,c);
              segH(ox,oy,0,4,4,c); segV(ox,oy,3,5,4,c); break;
    case 'S': segH(ox,oy,1,0,4,c); segV(ox,oy,0,1,3,c); segH(ox,oy,1,4,3,c);
              segV(ox,oy,4,5,3,c); segH(ox,oy,0,8,4,c); break;
    case 'T': segH(ox,oy,0,0,5,c); segV(ox,oy,2,1,8,c); break;
    case 'U': segV(ox,oy,0,0,8,c); segV(ox,oy,4,0,8,c); segH(ox,oy,1,8,3,c); break;
    case 'V': segV(ox,oy,0,0,6,c); segV(ox,oy,4,0,6,c);
              segD(ox,oy,1,6,c); segD(ox,oy,3,6,c); segD(ox,oy,2,7,c); break;
    case 'W': segV(ox,oy,0,0,9,c); segV(ox,oy,4,0,9,c); segV(ox,oy,2,5,4,c); break;
    case 'Y': segV(ox,oy,0,0,4,c); segV(ox,oy,4,0,4,c); segH(ox,oy,1,4,3,c);
              segV(ox,oy,2,5,4,c); break;
    case 'Z': segH(ox,oy,0,0,5,c); segD(ox,oy,3,2,c); segD(ox,oy,2,4,c);
              segD(ox,oy,1,6,c); segH(ox,oy,0,8,5,c); break;
    case '0': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,7,c); segV(ox,oy,4,1,7,c);
              segH(ox,oy,1,8,3,c); break;
    case '1': segV(ox,oy,2,0,9,c); break;
    case '2': segH(ox,oy,0,0,5,c); segV(ox,oy,4,1,3,c); segH(ox,oy,0,4,5,c);
              segV(ox,oy,0,5,3,c); segH(ox,oy,0,8,5,c); break;
    case '3': segH(ox,oy,0,0,5,c); segV(ox,oy,4,1,3,c); segH(ox,oy,0,4,5,c);
              segV(ox,oy,4,5,3,c); segH(ox,oy,0,8,5,c); break;
    case '4': segV(ox,oy,0,0,5,c); segV(ox,oy,4,0,9,c); segH(ox,oy,0,4,5,c); break;
    case '5': segH(ox,oy,0,0,5,c); segV(ox,oy,0,1,3,c); segH(ox,oy,0,4,5,c);
              segV(ox,oy,4,5,3,c); segH(ox,oy,0,8,5,c); break;
    case '6': segH(ox,oy,1,0,4,c); segV(ox,oy,0,1,7,c); segH(ox,oy,1,4,4,c);
              segV(ox,oy,4,5,3,c); segH(ox,oy,1,8,3,c); break;
    case '7': segH(ox,oy,0,0,5,c); segV(ox,oy,4,1,4,c); segV(ox,oy,2,5,4,c); break;
    case '8': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,3,c); segV(ox,oy,4,1,3,c);
              segH(ox,oy,1,4,3,c); segV(ox,oy,0,5,3,c); segV(ox,oy,4,5,3,c);
              segH(ox,oy,1,8,3,c); break;
    case '9': segH(ox,oy,1,0,3,c); segV(ox,oy,0,1,3,c); segV(ox,oy,4,1,7,c);
              segH(ox,oy,1,4,3,c); segH(ox,oy,1,8,3,c); break;
    case ':': segD(ox,oy,2,2,c); segD(ox,oy,2,6,c); break;
    case '.': segD(ox,oy,2,8,c); break;
    case '-': segH(ox,oy,1,4,3,c); break;
    case '/': segD(ox,oy,3,1,c); segD(ox,oy,2,3,c); segD(ox,oy,1,5,c); break;
    default: break;
    }
}

static int strLen(const char* s) {
    int n = 0;
    for (const char* p = s; *p && *p != '\n'; ++p) n++;
    return n;
}

static void drawString(const char* s, float x, float y, float ps, const float* col) {
    gPS = ps;
    float cx = x, cy = y;
    for (const unsigned char* p = (const unsigned char*)s; *p; ++p) {
        if (*p == '\n') { cy += 12.0f * ps; cx = x; continue; }
        if (*p >= 0x80) continue;
        char ch = (char)*p;
        if (ch >= 'a' && ch <= 'z') ch -= 32;
        drawChar(ch, cx, cy, col);
        cx += 7.0f * ps;
    }
}

static void drawStringCentered(const char* s, float cy, float ps, const float* col) {
    float w = (float)strLen(s) * 7.0f * ps;
    drawString(s, (float)g_W * 0.5f - w * 0.5f, cy, ps, col);
}

/* ── Buton ── */
static void drawButton(float x, float y, float w, float h,
                       const char* label, bool enabled, bool pressed, float ps) {
    float bw = (float)g_W * 0.005f;
    const float* gc = enabled ? C_GOLD : C_GREY;
    float bc[4] = {gc[0], gc[1], gc[2], enabled ? 1.0f : 0.4f};

    /* Kenarlık */
    drawRect(x - bw, y - bw, w + 2*bw, h + 2*bw, bc);
    /* Gövde */
    drawRect(x, y, w, h, pressed ? C_PRES : C_DARK);
    /* Köşe noktaları */
    float cs = (float)g_W * 0.009f;
    drawRect(x,       y,       cs, cs, bc);
    drawRect(x+w-cs,  y,       cs, cs, bc);
    drawRect(x,       y+h-cs,  cs, cs, bc);
    drawRect(x+w-cs,  y+h-cs,  cs, cs, bc);
    /* Etiket */
    float lc[4] = {gc[0], gc[1], gc[2], enabled ? 1.0f : 0.4f};
    float tw = (float)strLen(label) * 7.0f * ps;
    drawString(label, x + (w - tw) * 0.5f, y + (h - 9.0f * ps) * 0.5f, ps, lc);
}

static bool hitTest(float bx, float by, float bw, float bh, float tx, float ty) {
    return tx >= bx && tx <= bx + bw && ty >= by && ty <= by + bh;
}

/* ── Tarama çizgileri ── */
static void scanLines() {
    float sc[4] = {0.0f, 0.0f, 0.0f, 0.10f};
    for (float y = 0.0f; y < (float)g_H; y += 4.0f)
        drawRect(0.0f, y, (float)g_W, 1.5f, sc);
}

/* ════ RENDER SPLASH ════ */
static void renderSplash() {
    float sw = (float)g_W, sh = (float)g_H;

    glViewport(0, 0, g_W, g_H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(g_prog);
    scanLines();

    /* Üst/alt şerit */
    drawRect(0, sh*0.010f, sw, sh*0.004f, C_GOLD);
    drawRect(0, sh*0.974f, sw, sh*0.004f, C_GOLD);

    /* "BONECASTOFFICIAL" büyük */
    float ps = sw * 0.010f;
    drawStringCentered("BONECASTOFFICIAL", sh * 0.38f, ps, C_GOLD);

    /* "SUNAR" */
    float dps = sw * 0.007f;
    drawStringCentered("SUNAR", sh * 0.38f + 14.0f * ps, dps, C_GDIM);

    /* İlerleme çubuğu */
    float prog = (float)g_frame / 120.0f;
    if (prog > 1.0f) prog = 1.0f;
    float bx = sw * 0.20f, by = sh * 0.65f;
    float bw = sw * 0.60f, bh = sh * 0.007f;
    drawRect(bx, by, bw, bh, C_DARK);
    drawRect(bx, by, bw * prog, bh, C_GOLD);

    /* Alt bilgi */
    drawStringCentered("ANDROID NDK   OPENGL ES 2.0", sh * 0.74f, dps, C_GDIM);
}

/* ════ RENDER MENU ════ */
static void renderMenu() {
    float sw = (float)g_W, sh = (float)g_H;

    glViewport(0, 0, g_W, g_H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(g_prog);
    scanLines();

    drawRect(0, sh*0.010f, sw, sh*0.004f, C_GOLD);
    drawRect(0, sh*0.974f, sw, sh*0.004f, C_GOLD);

    /* Başlık */
    float ps = sw * 0.014f;
    drawStringCentered("MUHUR", sh * 0.055f, ps, C_GOLD);

    /* Ayraç + motto */
    drawRect(sw*0.08f, sh*0.165f, sw*0.84f, sh*0.002f, C_GDIM);
    drawStringCentered("KADERIN MUHRUNUN UCUNDA", sh*0.178f, sw*0.008f, C_GDIM);
    drawRect(sw*0.08f, sh*0.230f, sw*0.84f, sh*0.002f, C_GOLD);

    /* Butonlar */
    float bw = sw * 0.72f, bh = sh * 0.080f;
    float bx = (sw - bw) * 0.5f;
    float gap = sh * 0.022f, sy = sh * 0.260f;
    float bp = sw * 0.013f;
    const char* lb[4] = {"YENİ OYUN", "DEVAM ET", "AYARLAR", "CIKIS"};
    bool  en[4] = {true, false, true, true};

    for (int i = 0; i < 4; i++) {
        float fy = sy + (float)i * (bh + gap);
        g_bx[i] = bx; g_by[i] = fy; g_bw[i] = bw; g_bh[i] = bh;
        drawButton(bx, fy, bw, bh, lb[i], en[i], g_bp[i], bp);
    }

    drawStringCentered("BONECASTOFFICIAL", sh*0.930f, sw*0.007f, C_GDIM);
    drawString("V0.1", sw*0.04f, sh*0.950f, sw*0.007f, C_GDIM);
}

/* ════ RENDER SETTINGS ════ */
static void renderSettings() {
    float sw = (float)g_W, sh = (float)g_H;

    glViewport(0, 0, g_W, g_H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(g_prog);
    scanLines();
    drawRect(0, sh*0.010f, sw, sh*0.004f, C_GOLD);
    drawStringCentered("AYARLAR", sh*0.060f, sw*0.012f, C_GOLD);
    drawRect(sw*0.10f, sh*0.155f, sw*0.80f, sh*0.003f, C_GDIM);

    float bw = sw*0.70f, bh = sh*0.080f, bx = (sw-bw)*0.5f, bp = sw*0.012f;

    char sesL[24]; snprintf(sesL, 24, "SES %s", g_sndOn ? "ACIK" : "KAPALI");
    g_sx[0]=bx; g_sy[0]=sh*0.22f; g_sw2[0]=bw; g_sh2[0]=bh;
    drawButton(bx, sh*0.22f, bw, bh, sesL, true, false, bp);

    if (g_fps==60) { float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.20f}; drawRect(bx,sh*0.33f,bw,bh,hl); }
    g_sx[1]=bx; g_sy[1]=sh*0.33f; g_sw2[1]=bw; g_sh2[1]=bh;
    drawButton(bx, sh*0.33f, bw, bh, "FPS 60", true, false, bp);

    if (g_fps==30) { float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.20f}; drawRect(bx,sh*0.44f,bw,bh,hl); }
    g_sx[2]=bx; g_sy[2]=sh*0.44f; g_sw2[2]=bw; g_sh2[2]=bh;
    drawButton(bx, sh*0.44f, bw, bh, "FPS 30", true, false, bp);

    g_sx[3]=bx; g_sy[3]=sh*0.62f; g_sw2[3]=bw; g_sh2[3]=bh;
    drawButton(bx, sh*0.62f, bw, bh, "GERI", true, false, bp);

    char ft[24]; snprintf(ft, 24, "HEDEF %d FPS", g_fps);
    drawStringCentered(ft, sh*0.74f, sw*0.008f, C_GDIM);
}

/* ════ DOKUNMA ════ */
static void onTouchUp(float tx, float ty, android_app* aapp) {
    if (g_state == ST_SPLASH) {
        g_state = ST_MENU;
        LI("Splash -> Menu (tap)");
        return;
    }
    if (g_state == ST_MENU) {
        memset(g_bp, 0, sizeof(g_bp));
        if      (hitTest(g_bx[0],g_by[0],g_bw[0],g_bh[0],tx,ty)) LI("YENİ OYUN tiklandi");
        else if (hitTest(g_bx[2],g_by[2],g_bw[2],g_bh[2],tx,ty)) { g_state=ST_SETTINGS; LI("AYARLAR"); }
        else if (hitTest(g_bx[3],g_by[3],g_bw[3],g_bh[3],tx,ty)) ANativeActivity_finish(aapp->activity);
        return;
    }
    if (g_state == ST_SETTINGS) {
        if      (hitTest(g_sx[0],g_sy[0],g_sw2[0],g_sh2[0],tx,ty)) g_sndOn = !g_sndOn;
        else if (hitTest(g_sx[1],g_sy[1],g_sw2[1],g_sh2[1],tx,ty)) g_fps = 60;
        else if (hitTest(g_sx[2],g_sy[2],g_sw2[2],g_sh2[2],tx,ty)) g_fps = 30;
        else if (hitTest(g_sx[3],g_sy[3],g_sw2[3],g_sh2[3],tx,ty)) g_state = ST_MENU;
        return;
    }
}

/* ════ ANDROID CALLBACKS ════ */
static void onAppCmd(android_app* aapp, int32_t cmd) {
    switch (cmd) {
    case APP_CMD_INIT_WINDOW: {
        LI("INIT_WINDOW");
        if (!aapp->window) { LE("null window"); break; }

        /* EGL */
        g_dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (g_dpy == EGL_NO_DISPLAY) { LE("noDisplay"); break; }
        EGLint ma=0, mi=0;
        if (!eglInitialize(g_dpy, &ma, &mi)) { LE("eglInit"); break; }
        LI("EGL %d.%d", ma, mi);

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
            const EGLint att2[] = {
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
            };
            eglChooseConfig(g_dpy, att2, &cfg, 1, &nc);
        }
        LI("Config nc=%d", nc);

        g_suf = eglCreateWindowSurface(g_dpy, cfg, aapp->window, nullptr);
        if (g_suf == EGL_NO_SURFACE) { LE("surface fail 0x%x", eglGetError()); break; }

        const EGLint ca[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
        g_ctx = eglCreateContext(g_dpy, cfg, EGL_NO_CONTEXT, ca);
        if (g_ctx == EGL_NO_CONTEXT) { LE("ctx fail 0x%x", eglGetError()); break; }

        if (eglMakeCurrent(g_dpy, g_suf, g_suf, g_ctx) == EGL_FALSE) {
            LE("makeCurrent fail 0x%x", eglGetError()); break;
        }

        eglSwapInterval(g_dpy, 0);

        eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
        eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
        LI("Surface %dx%d", g_W, g_H);

        /* Shader */
        g_prog = createProgram();
        if (!g_prog) { LE("prog fail"); break; }

        g_aPos   = glGetAttribLocation (g_prog, "aPos");
        g_uRes   = glGetUniformLocation(g_prog, "uRes");
        g_uColor = glGetUniformLocation(g_prog, "uColor");
        LI("aPos=%d uRes=%d uColor=%d", g_aPos, g_uRes, g_uColor);

        if (g_aPos < 0 || g_uRes < 0 || g_uColor < 0) {
            LE("Shader location fail!"); break;
        }

        /* VBO */
        glGenBuffers(1, &g_vbo);
        LI("VBO=%u", g_vbo);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        /* Test frame — amber */
        glViewport(0, 0, g_W, g_H);
        glClearColor(0.4f, 0.3f, 0.05f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        /* Shader ile tek dikdörtgen çiz — eğer görünürse shader çalışıyor */
        glUseProgram(g_prog);
        float testVerts[8] = {
            (float)g_W*0.3f, (float)g_H*0.45f,
            (float)g_W*0.7f, (float)g_H*0.45f,
            (float)g_W*0.3f, (float)g_H*0.55f,
            (float)g_W*0.7f, (float)g_H*0.55f
        };
        glBindBuffer(GL_ARRAY_BUFFER, g_vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(testVerts), testVerts, GL_DYNAMIC_DRAW);
        glEnableVertexAttribArray((GLuint)g_aPos);
        glVertexAttribPointer((GLuint)g_aPos, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
        glUniform2f(g_uRes, (float)g_W, (float)g_H);
        glUniform4f(g_uColor, C_GOLD[0], C_GOLD[1], C_GOLD[2], C_GOLD[3]);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray((GLuint)g_aPos);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        eglSwapBuffers(g_dpy, g_suf);
        LI("Test frame gonderildi - amber + altin bar gorulmeli");

        /* State */
        memset(g_bp, 0, sizeof(g_bp));
        g_state = ST_SPLASH;
        g_frame = 0;
        g_ok = true;
        LI("HAZIR");
        break;
    }
    case APP_CMD_TERM_WINDOW:
        LI("TERM_WINDOW");
        g_ok = false;
        if (g_vbo) { glDeleteBuffers(1, &g_vbo); g_vbo = 0; }
        if (g_prog) { glDeleteProgram(g_prog); g_prog = 0; }
        if (g_dpy != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (g_ctx != EGL_NO_CONTEXT) eglDestroyContext(g_dpy, g_ctx);
            if (g_suf != EGL_NO_SURFACE) eglDestroySurface(g_dpy, g_suf);
            eglTerminate(g_dpy);
        }
        g_dpy=EGL_NO_DISPLAY; g_ctx=EGL_NO_CONTEXT; g_suf=EGL_NO_SURFACE;
        break;
    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if (g_dpy != EGL_NO_DISPLAY) {
            eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
            eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
            LI("Resize %dx%d", g_W, g_H);
        }
        break;
    default: break;
    }
}

static int32_t onInputEvent(android_app* aapp, AInputEvent* evt) {
    if (!g_ok) return 0;
    if (AInputEvent_getType(evt) != AINPUT_EVENT_TYPE_MOTION) return 0;
    int32_t act = AMotionEvent_getAction(evt) & AMOTION_EVENT_ACTION_MASK;
    float tx = AMotionEvent_getX(evt, 0);
    float ty = AMotionEvent_getY(evt, 0);
    if (act == AMOTION_EVENT_ACTION_DOWN) {
        if (g_state == ST_MENU)
            for (int i = 0; i < 4; i++)
                g_bp[i] = hitTest(g_bx[i], g_by[i], g_bw[i], g_bh[i], tx, ty);
    } else if (act == AMOTION_EVENT_ACTION_UP) {
        memset(g_bp, 0, sizeof(g_bp));
        onTouchUp(tx, ty, aapp);
    } else if (act == AMOTION_EVENT_ACTION_CANCEL) {
        memset(g_bp, 0, sizeof(g_bp));
    }
    return 1;
}

/* ════ GİRİŞ NOKTASI ════ */
void android_main(android_app* aapp) {
    LI("android_main START");
    aapp->userData     = nullptr;
    aapp->onAppCmd     = onAppCmd;
    aapp->onInputEvent = onInputEvent;

    while (!aapp->destroyRequested) {
        int                  ev  = 0;
        android_poll_source* src = nullptr;
        int timeout = g_ok ? 0 : -1;

        while (ALooper_pollOnce(timeout, nullptr, &ev, (void**)&src) >= 0) {
            if (src) src->process(aapp, src);
            if (aapp->destroyRequested) break;
        }

        if (!g_ok) continue;

        /* Render */
        switch (g_state) {
        case ST_SPLASH:
            renderSplash();
            g_frame++;
            if (g_frame >= 120) {
                g_state = ST_MENU;
                LI("Splash bitti -> Menu");
            }
            break;
        case ST_MENU:
            renderMenu();
            break;
        case ST_SETTINGS:
            renderSettings();
            break;
        }

        eglSwapBuffers(g_dpy, g_suf);
    }

    LI("android_main END");
}

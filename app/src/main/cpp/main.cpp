/**
 * MÜHÜR — main.cpp
 * Papers Please pikselli menü · OpenGL ES 2.0
 * Android Native Activity · Crash-safe asset yükleme
 */

/* ── Sistem başlıkları ───────────────────────────────── */
#include <android_native_app_glue.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

/* ── Standart C/C++ ──────────────────────────────────── */
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <unistd.h>   /* usleep */
#include <time.h>

/* ── Log ─────────────────────────────────────────────── */
#define TAG  "MUHUR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ════════════════════════════════════════════════════════
 * RENK PALETİ
 * ════════════════════════════════════════════════════════ */
static const float C_BG[4]      = { 0.051f, 0.039f, 0.020f, 1.00f };
static const float C_GOLD[4]    = { 0.831f, 0.659f, 0.325f, 1.00f };
static const float C_GOLD_DIM[4]= { 0.420f, 0.330f, 0.160f, 1.00f };
static const float C_DARK[4]    = { 0.080f, 0.062f, 0.031f, 1.00f };
static const float C_PRESS[4]   = { 0.160f, 0.124f, 0.060f, 1.00f };
static const float C_GREY[4]    = { 0.220f, 0.200f, 0.180f, 1.00f };

/* ════════════════════════════════════════════════════════
 * PIXEL FONT 5×7  (ASCII 32–90)
 * ════════════════════════════════════════════════════════ */
static const uint8_t FONT[59][7] = {
/*' '*/{ 0x00,0x00,0x00,0x00,0x00,0x00,0x00 },
/*'!'*/{ 0x04,0x04,0x04,0x04,0x00,0x04,0x00 },
/*'"'*/{ 0x0A,0x0A,0x00,0x00,0x00,0x00,0x00 },
/*'#'*/{ 0x0A,0x1F,0x0A,0x0A,0x1F,0x0A,0x00 },
/*'$'*/{ 0x04,0x0F,0x14,0x0E,0x05,0x1E,0x04 },
/*'%'*/{ 0x18,0x19,0x02,0x04,0x13,0x03,0x00 },
/*'&'*/{ 0x0C,0x12,0x14,0x08,0x15,0x12,0x0D },
/*'''*/{ 0x04,0x04,0x00,0x00,0x00,0x00,0x00 },
/*'('*/{ 0x02,0x04,0x08,0x08,0x08,0x04,0x02 },
/*')'*/{ 0x08,0x04,0x02,0x02,0x02,0x04,0x08 },
/*'*'*/{ 0x00,0x04,0x15,0x0E,0x15,0x04,0x00 },
/*'+'*/{ 0x00,0x04,0x04,0x1F,0x04,0x04,0x00 },
/*','*/{ 0x00,0x00,0x00,0x00,0x06,0x04,0x08 },
/*'-'*/{ 0x00,0x00,0x00,0x1F,0x00,0x00,0x00 },
/*'.'*/{ 0x00,0x00,0x00,0x00,0x00,0x06,0x06 },
/*'/'*/{ 0x01,0x01,0x02,0x04,0x08,0x10,0x10 },
/*'0'*/{ 0x0E,0x11,0x13,0x15,0x19,0x11,0x0E },
/*'1'*/{ 0x04,0x0C,0x04,0x04,0x04,0x04,0x0E },
/*'2'*/{ 0x0E,0x11,0x01,0x02,0x04,0x08,0x1F },
/*'3'*/{ 0x1F,0x02,0x04,0x02,0x01,0x11,0x0E },
/*'4'*/{ 0x02,0x06,0x0A,0x12,0x1F,0x02,0x02 },
/*'5'*/{ 0x1F,0x10,0x1E,0x01,0x01,0x11,0x0E },
/*'6'*/{ 0x06,0x08,0x10,0x1E,0x11,0x11,0x0E },
/*'7'*/{ 0x1F,0x01,0x02,0x04,0x08,0x08,0x08 },
/*'8'*/{ 0x0E,0x11,0x11,0x0E,0x11,0x11,0x0E },
/*'9'*/{ 0x0E,0x11,0x11,0x0F,0x01,0x02,0x0C },
/*':'*/{ 0x00,0x06,0x06,0x00,0x06,0x06,0x00 },
/*';'*/{ 0x00,0x06,0x06,0x00,0x06,0x04,0x08 },
/*'<'*/{ 0x02,0x04,0x08,0x10,0x08,0x04,0x02 },
/*'='*/{ 0x00,0x00,0x1F,0x00,0x1F,0x00,0x00 },
/*'>'*/{ 0x08,0x04,0x02,0x01,0x02,0x04,0x08 },
/*'?'*/{ 0x0E,0x11,0x01,0x02,0x04,0x00,0x04 },
/*'@'*/{ 0x0E,0x11,0x01,0x0D,0x15,0x15,0x0E },
/*'A'*/{ 0x0E,0x11,0x11,0x1F,0x11,0x11,0x11 },
/*'B'*/{ 0x1E,0x11,0x11,0x1E,0x11,0x11,0x1E },
/*'C'*/{ 0x0E,0x11,0x10,0x10,0x10,0x11,0x0E },
/*'D'*/{ 0x1C,0x12,0x11,0x11,0x11,0x12,0x1C },
/*'E'*/{ 0x1F,0x10,0x10,0x1E,0x10,0x10,0x1F },
/*'F'*/{ 0x1F,0x10,0x10,0x1E,0x10,0x10,0x10 },
/*'G'*/{ 0x0E,0x11,0x10,0x17,0x11,0x11,0x0F },
/*'H'*/{ 0x11,0x11,0x11,0x1F,0x11,0x11,0x11 },
/*'I'*/{ 0x0E,0x04,0x04,0x04,0x04,0x04,0x0E },
/*'J'*/{ 0x07,0x02,0x02,0x02,0x02,0x12,0x0C },
/*'K'*/{ 0x11,0x12,0x14,0x18,0x14,0x12,0x11 },
/*'L'*/{ 0x10,0x10,0x10,0x10,0x10,0x10,0x1F },
/*'M'*/{ 0x11,0x1B,0x15,0x15,0x11,0x11,0x11 },
/*'N'*/{ 0x11,0x19,0x15,0x13,0x11,0x11,0x11 },
/*'O'*/{ 0x0E,0x11,0x11,0x11,0x11,0x11,0x0E },
/*'P'*/{ 0x1E,0x11,0x11,0x1E,0x10,0x10,0x10 },
/*'Q'*/{ 0x0E,0x11,0x11,0x11,0x15,0x12,0x0D },
/*'R'*/{ 0x1E,0x11,0x11,0x1E,0x14,0x12,0x11 },
/*'S'*/{ 0x0F,0x10,0x10,0x0E,0x01,0x01,0x1E },
/*'T'*/{ 0x1F,0x04,0x04,0x04,0x04,0x04,0x04 },
/*'U'*/{ 0x11,0x11,0x11,0x11,0x11,0x11,0x0E },
/*'V'*/{ 0x11,0x11,0x11,0x11,0x11,0x0A,0x04 },
/*'W'*/{ 0x11,0x11,0x11,0x15,0x15,0x15,0x0A },
/*'X'*/{ 0x11,0x11,0x0A,0x04,0x0A,0x11,0x11 },
/*'Y'*/{ 0x11,0x11,0x0A,0x04,0x04,0x04,0x04 },
/*'Z'*/{ 0x1F,0x01,0x02,0x04,0x08,0x10,0x1F },
};

/* ════════════════════════════════════════════════════════
 * SHADER KAYNAKLARI
 * ════════════════════════════════════════════════════════ */
static const char* VS_C = R"GLSL(
attribute vec2 aPos;
uniform   vec2 uRes;
void main(){
    vec2 n = (aPos / uRes) * 2.0 - 1.0;
    gl_Position = vec4(n.x, -n.y, 0.0, 1.0);
}
)GLSL";

static const char* FS_C = R"GLSL(
precision mediump float;
uniform vec4 uColor;
void main(){ gl_FragColor = uColor; }
)GLSL";

static const char* VS_T = R"GLSL(
attribute vec2 aPos;
attribute vec2 aUV;
uniform   vec2 uRes;
varying   vec2 vUV;
void main(){
    vec2 n = (aPos / uRes) * 2.0 - 1.0;
    gl_Position = vec4(n.x, -n.y, 0.0, 1.0);
    vUV = aUV;
}
)GLSL";

static const char* FS_T = R"GLSL(
precision mediump float;
uniform sampler2D uTex;
uniform float     uAlpha;
varying vec2 vUV;
void main(){
    vec4 c = texture2D(uTex, vUV);
    gl_FragColor = vec4(c.rgb, c.a * uAlpha);
}
)GLSL";

/* ════════════════════════════════════════════════════════
 * SHADER YARDIMCILARI
 * ════════════════════════════════════════════════════════ */
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[512];
        glGetShaderInfoLog(s, 512, nullptr, buf);
        LOGE("Shader derlenemedi: %s", buf);
    }
    return s;
}

static GLuint createProgram(const char* vs, const char* fs) {
    GLuint p  = glCreateProgram();
    GLuint v  = compileShader(GL_VERTEX_SHADER,   vs);
    GLuint f  = compileShader(GL_FRAGMENT_SHADER, fs);
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v);
    glDeleteShader(f);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { LOGE("Program link hatasi"); }
    return p;
}

/* ════════════════════════════════════════════════════════
 * PNG DECODE  (stored-block only, crash-safe)
 * ════════════════════════════════════════════════════════ */
static inline uint32_t readU32BE(const uint8_t* p) {
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|
           ((uint32_t)p[2]<< 8)| (uint32_t)p[3];
}
static uint8_t paethPredictor(uint8_t a, uint8_t b, uint8_t c) {
    int pa = abs((int)b - c);
    int pb = abs((int)a - c);
    int pc = abs((int)a + (int)b - 2*(int)c);
    return (pa <= pb && pa <= pc) ? a : (pb <= pc) ? b : c;
}
static bool zlibInflate(const uint8_t* src, int sLen,
                        uint8_t* dst, int dLen) {
    if (sLen < 2) return false;
    int pos = 2, out = 0; /* CMF+FLG skip */
    while (pos < sLen - 4 && out < dLen) {
        bool bfinal = (src[pos] & 1) != 0;
        int  btype  = (src[pos] >> 1) & 3;
        pos++;
        if (btype == 0) {                       /* stored block */
            if (pos & 1) pos++;
            if (pos + 4 > sLen) return false;
            uint16_t len = (uint16_t)src[pos] |
                           ((uint16_t)src[pos+1] << 8);
            pos += 4;
            if (pos + len > sLen || out + len > dLen) return false;
            memcpy(dst + out, src + pos, len);
            out += len; pos += len;
        } else {
            LOGE("PNG: deflate blok - stb_image gerekli");
            return false;
        }
        if (bfinal) break;
    }
    return out == dLen;
}
static bool decodePNG(const uint8_t* data, int size,
                      uint8_t** outPixels, int* outW, int* outH) {
    static const uint8_t SIG[8] = {137,80,78,71,13,10,26,10};
    if (size < 8 || memcmp(data, SIG, 8) != 0) return false;

    int pos = 8, w = 0, h = 0, ch = 0;
    uint8_t* idat  = nullptr;
    int      idatL = 0, idatC = 0;
    bool     gotEnd = false;

    while (pos + 12 <= size && !gotEnd) {
        int  cLen  = (int)readU32BE(data + pos); pos += 4;
        char cType[5]; memcpy(cType, data+pos, 4); cType[4]=0; pos += 4;

        if (!strcmp(cType, "IHDR")) {
            w  = (int)readU32BE(data + pos);
            h  = (int)readU32BE(data + pos + 4);
            int bd = data[pos+8], ct = data[pos+9];
            ch = (ct==6) ? 4 : (ct==2) ? 3 : 0;
            if (bd != 8 || ch == 0) {
                LOGE("PNG: sadece RGB/RGBA 8bit");
                free(idat); return false;
            }
        } else if (!strcmp(cType, "IDAT")) {
            if (idatL + cLen > idatC) {
                idatC = idatL + cLen + 65536;
                idat  = (uint8_t*)realloc(idat, (size_t)idatC);
            }
            memcpy(idat + idatL, data + pos, (size_t)cLen);
            idatL += cLen;
        } else if (!strcmp(cType, "IEND")) {
            gotEnd = true;
        }
        pos += cLen + 4; /* data + CRC */
    }

    if (!gotEnd || !idat || w == 0 || h == 0) { free(idat); return false; }

    int stride = w * ch;
    int rawSz  = h * (stride + 1);
    uint8_t* raw = (uint8_t*)malloc((size_t)rawSz);
    if (!raw) { free(idat); return false; }

    if (!zlibInflate(idat, idatL, raw, rawSz)) {
        free(idat); free(raw); return false;
    }
    free(idat);

    uint8_t* px   = (uint8_t*)malloc((size_t)(w * h * 4));
    uint8_t* prev = (uint8_t*)calloc((size_t)stride, 1);
    if (!px || !prev) { free(raw); free(px); free(prev); return false; }

    for (int y = 0; y < h; y++) {
        uint8_t* row = raw  + y * (stride + 1);
        uint8_t  ft  = row[0];
        uint8_t* cur = row  + 1;
        for (int x = 0; x < stride; x++) {
            uint8_t a = (x >= ch) ? cur[x-ch] : 0;
            uint8_t b = prev[x];
            uint8_t c = (x >= ch) ? prev[x-ch] : 0;
            switch (ft) {
                case 1: cur[x] += a; break;
                case 2: cur[x] += b; break;
                case 3: cur[x] += (uint8_t)((a+b)/2); break;
                case 4: cur[x] += paethPredictor(a,b,c); break;
                default: break;
            }
        }
        memcpy(prev, cur, (size_t)stride);
        uint8_t* out = px + y * w * 4;
        for (int x = 0; x < w; x++) {
            out[x*4+0] = cur[x*ch+0];
            out[x*4+1] = cur[x*ch+1];
            out[x*4+2] = cur[x*ch+2];
            out[x*4+3] = (ch == 4) ? cur[x*ch+3] : 255;
        }
    }
    free(raw); free(prev);
    *outPixels = px; *outW = w; *outH = h;
    return true;
}

static GLuint loadTexture(AAssetManager* am, const char* path,
                          int* tw, int* th) {
    if (!am) return 0;
    AAsset* a = AAssetManager_open(am, path, AASSET_MODE_BUFFER);
    if (!a) { LOGE("Asset bulunamadi: %s", path); return 0; }
    const uint8_t* buf = (const uint8_t*)AAsset_getBuffer(a);
    int sz = (int)AAsset_getLength(a);
    uint8_t* px = nullptr; int w = 0, h = 0;
    bool ok = decodePNG(buf, sz, &px, &w, &h);
    AAsset_close(a);
    if (!ok) { LOGE("PNG decode hatasi: %s", path); return 0; }
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, px);
    free(px);
    if (tw) *tw = w;
    if (th) *th = h;
    LOGI("Texture: %s (%dx%d)", path, w, h);
    return tex;
}

/* ════════════════════════════════════════════════════════
 * ÇİZİM
 * ════════════════════════════════════════════════════════ */
struct Gfx {
    float  sw, sh;
    GLuint cp, tp;  /* colour program, texture program */
};

static void drawRect(const Gfx& g, float x, float y, float w, float h,
                     const float* col) {
    float v[] = { x,y, x+w,y, x,y+h, x+w,y+h };
    glUseProgram(g.cp);
    glUniform2f(glGetUniformLocation(g.cp, "uRes"), g.sw, g.sh);
    glUniform4fv(glGetUniformLocation(g.cp, "uColor"), 1, col);
    GLint ap = glGetAttribLocation(g.cp, "aPos");
    glEnableVertexAttribArray((GLuint)ap);
    glVertexAttribPointer((GLuint)ap, 2, GL_FLOAT, GL_FALSE, 0, v);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray((GLuint)ap);
}

static void drawTex(const Gfx& g, GLuint tex,
                    float x, float y, float w, float h, float alpha) {
    if (!tex) return;
    float v[] = { x,y,0,0, x+w,y,1,0, x,y+h,0,1, x+w,y+h,1,1 };
    glUseProgram(g.tp);
    glUniform2f(glGetUniformLocation(g.tp, "uRes"),   g.sw, g.sh);
    glUniform1i(glGetUniformLocation(g.tp, "uTex"),   0);
    glUniform1f(glGetUniformLocation(g.tp, "uAlpha"), alpha);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    GLint ap = glGetAttribLocation(g.tp, "aPos");
    GLint au = glGetAttribLocation(g.tp, "aUV");
    glEnableVertexAttribArray((GLuint)ap);
    glEnableVertexAttribArray((GLuint)au);
    glVertexAttribPointer((GLuint)ap,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)0);
    glVertexAttribPointer((GLuint)au,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray((GLuint)ap);
    glDisableVertexAttribArray((GLuint)au);
}

/* pixel karakter */
static void drawChar(const Gfx& g, char c, float x, float y,
                     float ps, const float* col) {
    int idx = (int)(unsigned char)c - 32;
    if (idx < 0 || idx >= 59) idx = 0;
    for (int row = 0; row < 7; row++) {
        uint8_t bits = FONT[idx][row];
        for (int col2 = 0; col2 < 5; col2++) {
            if (bits & (0x10 >> col2))
                drawRect(g, x + col2*ps, y + row*ps, ps, ps, col);
        }
    }
}

static void drawText(const Gfx& g, const char* txt,
                     float x, float y, float ps, const float* col) {
    float cx = x;
    const unsigned char* p = (const unsigned char*)txt;
    while (*p) {
        if (*p >= 0x80) {           /* UTF-8 multi-byte: atla */
            p++; if (*p && (*p & 0xC0) == 0x80) p++;
            cx += 6.0f * ps; continue;
        }
        drawChar(g, (char)*p, cx, y, ps, col);
        cx += 6.0f * ps; p++;
    }
}

static float textW(const char* txt, float ps) {
    int n = 0;
    const unsigned char* p = (const unsigned char*)txt;
    while (*p) {
        if (*p >= 0x80) { p++; if (*p && (*p & 0xC0)==0x80) p++; }
        else p++;
        n++;
    }
    return (float)n * 6.0f * ps;
}

static void drawTextCentered(const Gfx& g, const char* txt,
                              float cx, float y, float ps, const float* col) {
    drawText(g, txt, cx - textW(txt, ps) * 0.5f, y, ps, col);
}

/* ════════════════════════════════════════════════════════
 * BUTON
 * ════════════════════════════════════════════════════════ */
struct Btn {
    float       x, y, w, h;
    bool        enabled;
    bool        pressed;
    const char* label;
};

static bool hitTest(const Btn& b, float tx, float ty) {
    return b.enabled &&
           tx >= b.x && tx <= b.x + b.w &&
           ty >= b.y && ty <= b.y + b.h;
}

static void drawBtn(const Gfx& g, const Btn& b, float ps) {
    float bw = g.sw * 0.005f;

    /* kenarlık */
    float bc[4] = { C_GOLD[0], C_GOLD[1], C_GOLD[2],
                    b.enabled ? 1.0f : 0.30f };
    if (!b.enabled) { bc[0]=C_GREY[0]; bc[1]=C_GREY[1]; bc[2]=C_GREY[2]; }
    drawRect(g, b.x-bw, b.y-bw, b.w+2*bw, b.h+2*bw, bc);

    /* gövde */
    const float* fill = b.pressed ? C_PRESS : C_DARK;
    float fc[4] = { fill[0], fill[1], fill[2], 0.94f };
    drawRect(g, b.x, b.y, b.w, b.h, fc);

    /* köşe vurguları (Papers Please stili) */
    float cs = g.sw * 0.010f;
    drawRect(g, b.x,         b.y,         cs, cs, bc);
    drawRect(g, b.x+b.w-cs,  b.y,         cs, cs, bc);
    drawRect(g, b.x,         b.y+b.h-cs,  cs, cs, bc);
    drawRect(g, b.x+b.w-cs,  b.y+b.h-cs,  cs, cs, bc);

    /* etiket */
    float lc[4] = { C_GOLD[0], C_GOLD[1], C_GOLD[2],
                    b.enabled ? 1.0f : 0.40f };
    if (!b.enabled) { lc[0]=C_GREY[0]; lc[1]=C_GREY[1]; lc[2]=C_GREY[2]; }
    float tx2 = b.x + (b.w - textW(b.label, ps)) * 0.5f;
    float ty2 = b.y + (b.h - 7.0f * ps) * 0.5f;
    drawText(g, b.label, tx2, ty2, ps, lc);
}

/* ════════════════════════════════════════════════════════
 * UYGULAMA DURUMU
 * ════════════════════════════════════════════════════════ */
enum Screen { SCR_MENU, SCR_SETTINGS, SCR_BOOT, SCR_CREDITS };

struct App {
    /* EGL */
    EGLDisplay  eglDpy = EGL_NO_DISPLAY;
    EGLSurface  eglSuf = EGL_NO_SURFACE;
    EGLContext  eglCtx = EGL_NO_CONTEXT;
    int32_t     W = 0, H = 0;
    bool        ready = false;

    /* GL programs */
    GLuint      colProg = 0, texProg = 0;

    /* Textures */
    GLuint      texBG = 0, texLogo = 0;
    int         bgW = 0, bgH = 0, lgW = 0, lgH = 0;

    /* Assets */
    AAssetManager* am = nullptr;

    /* Oyun */
    Screen      scr     = SCR_MENU;
    bool        hasSave = false;
    bool        soundOn = true;
    int         fps     = 60;

    /* Boot anim */
    long        bootStart = 0;

    /* FPS limiter */
    long        lastFrameMs = 0;

    /* Butonlar */
    Btn         mBtn[4];   /* menü */
    Btn         sBtn[4];   /* ayarlar */
    Btn         cBtn[1];   /* credits geri */
};

/* ════════════════════════════════════════════════════════
 * ZAMAN
 * ════════════════════════════════════════════════════════ */
static long nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/* ════════════════════════════════════════════════════════
 * RENDER — TARAMA ÇİZGİSİ (CRT efekti)
 * ════════════════════════════════════════════════════════ */
static void drawScanlines(const Gfx& g) {
    float sc[4] = {0.0f, 0.0f, 0.0f, 0.13f};
    for (float y = 0.0f; y < g.sh; y += 4.0f)
        drawRect(g, 0.0f, y, g.sw, 1.5f, sc);
}

/* ════════════════════════════════════════════════════════
 * RENDER — ANA MENÜ
 * ════════════════════════════════════════════════════════ */
static void renderMenu(App& app) {
    Gfx g = { (float)app.W, (float)app.H, app.colProg, app.texProg };
    float sw = g.sw, sh = g.sh;

    glViewport(0, 0, app.W, app.H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    /* arka plan */
    if (app.texBG) drawTex(g, app.texBG, 0, 0, sw, sh, 0.80f);
    float ov[4] = {C_BG[0],C_BG[1],C_BG[2],0.50f};
    drawRect(g, 0, 0, sw, sh, ov);

    drawScanlines(g);

    /* üst şerit */
    drawRect(g, 0, sh*0.010f, sw, sh*0.004f, C_GOLD);

    /* logo */
    if (app.texLogo && app.lgW > 0) {
        float ar = (float)app.lgW / (float)app.lgH;
        float lw = sw * 0.50f, lh = lw / ar;
        if (lh > sh * 0.18f) { lh = sh*0.18f; lw = lh*ar; }
        drawTex(g, app.texLogo, (sw-lw)*0.5f, sh*0.04f, lw, lh, 1.0f);
    } else {
        float ps = sw * 0.022f;
        drawTextCentered(g, "MUHUR", sw*0.5f, sh*0.07f, ps, C_GOLD);
    }

    /* motto */
    float mps = sw * 0.014f;
    drawTextCentered(g, "KADERIN, MUHRUNUN UCUNDA.",
                     sw*0.5f, sh*0.26f, mps, C_GOLD_DIM);

    /* ayraç */
    drawRect(g, sw*0.08f, sh*0.31f, sw*0.84f, sh*0.003f, C_GOLD_DIM);

    /* butonlar */
    float bw  = sw * 0.72f;
    float bh  = sh * 0.074f;
    float bx  = (sw - bw) * 0.5f;
    float gap = sh * 0.020f;
    float sy  = sh * 0.355f;
    float ps  = sw * 0.024f;

    const char* lbl[4]  = { "YENİ OYUN", "DEVAM ET", "AYARLAR", "CIKIS" };
    bool        en[4]   = { true, app.hasSave, true, true };

    for (int i = 0; i < 4; i++) {
        app.mBtn[i].x       = bx;
        app.mBtn[i].y       = sy + (float)i * (bh + gap);
        app.mBtn[i].w       = bw;
        app.mBtn[i].h       = bh;
        app.mBtn[i].enabled = en[i];
        app.mBtn[i].label   = lbl[i];
        drawBtn(g, app.mBtn[i], ps);
    }

    /* alt bilgi */
    float dps = sw * 0.012f;
    drawTextCentered(g, "BONECASTOFFICIAL", sw*0.5f, sh*0.94f, dps, C_GOLD_DIM);
    drawText(g, "V0.1", sw*0.04f, sh*0.95f, dps, C_GOLD_DIM);

    /* alt şerit */
    drawRect(g, 0, sh*0.975f, sw, sh*0.004f, C_GOLD);

    eglSwapBuffers(app.eglDpy, app.eglSuf);
}

/* ════════════════════════════════════════════════════════
 * RENDER — SİSTEM BAŞLATILIYOR (Boot)
 * ════════════════════════════════════════════════════════ */
static void renderBoot(App& app) {
    Gfx g = { (float)app.W, (float)app.H, app.colProg, app.texProg };
    float sw = g.sw, sh = g.sh;

    glViewport(0, 0, app.W, app.H);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    long elapsed = nowMs() - app.bootStart;
    int  dots    = (int)(elapsed / 400) % 4;

    char line[48] = "SİSTEM BAŞLATILIYOR";
    for (int i = 0; i < dots; i++) strcat(line, ".");

    float ps = sw * 0.020f;
    drawTextCentered(g, line, sw*0.5f, sh*0.42f, ps, C_GOLD);

    /* progress bar */
    float prog = (float)elapsed / 2000.0f;
    if (prog > 1.0f) prog = 1.0f;
    float bx = sw*0.15f, by = sh*0.53f, bw = sw*0.70f, bh = sh*0.018f;
    drawRect(g, bx, by, bw, bh, C_DARK);
    drawRect(g, bx, by, bw*prog, bh, C_GOLD);

    float dps = sw * 0.012f;
    drawTextCentered(g, "BONECASTOFFICIAL", sw*0.5f, sh*0.64f, dps, C_GOLD_DIM);

    drawScanlines(g);
    eglSwapBuffers(app.eglDpy, app.eglSuf);

    if (elapsed > 2200) app.scr = SCR_MENU;
}

/* ════════════════════════════════════════════════════════
 * RENDER — AYARLAR
 * ════════════════════════════════════════════════════════ */
static void renderSettings(App& app) {
    Gfx g = { (float)app.W, (float)app.H, app.colProg, app.texProg };
    float sw = g.sw, sh = g.sh;

    glViewport(0, 0, app.W, app.H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (app.texBG) drawTex(g, app.texBG, 0, 0, sw, sh, 0.35f);
    float ov[4]={C_BG[0],C_BG[1],C_BG[2],0.65f};
    drawRect(g,0,0,sw,sh,ov);
    drawScanlines(g);

    float ps  = sw * 0.026f;
    float dps = sw * 0.013f;
    drawTextCentered(g, "AYARLAR", sw*0.5f, sh*0.06f, ps, C_GOLD);
    drawRect(g, sw*0.10f, sh*0.145f, sw*0.80f, sh*0.003f, C_GOLD_DIM);

    float bw  = sw * 0.70f, bh = sh * 0.075f;
    float bx  = (sw - bw) * 0.5f;
    float bp  = sw * 0.022f;

    /* Ses */
    char sesLbl[32];
    snprintf(sesLbl, sizeof(sesLbl), "SES: %s", app.soundOn ? "ACIK" : "KAPALI");
    app.sBtn[0] = { bx, sh*0.22f, bw, bh, true, app.sBtn[0].pressed, sesLbl };
    drawBtn(g, app.sBtn[0], bp);

    /* FPS 30 */
    if (app.fps == 30) {
        float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.15f};
        drawRect(g, bx, sh*0.33f, bw, bh, hl);
    }
    app.sBtn[1] = { bx, sh*0.33f, bw, bh, true, app.sBtn[1].pressed, "FPS: 30" };
    drawBtn(g, app.sBtn[1], bp);

    /* FPS 60 */
    if (app.fps == 60) {
        float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.15f};
        drawRect(g, bx, sh*0.44f, bw, bh, hl);
    }
    app.sBtn[2] = { bx, sh*0.44f, bw, bh, true, app.sBtn[2].pressed, "FPS: 60" };
    drawBtn(g, app.sBtn[2], bp);

    /* Geri */
    app.sBtn[3] = { bx, sh*0.62f, bw, bh, true, app.sBtn[3].pressed, "GERİ" };
    drawBtn(g, app.sBtn[3], bp);

    char fpsTxt[32];
    snprintf(fpsTxt, sizeof(fpsTxt), "HEDEF: %d FPS", app.fps);
    drawTextCentered(g, fpsTxt, sw*0.5f, sh*0.73f, dps, C_GOLD_DIM);

    eglSwapBuffers(app.eglDpy, app.eglSuf);
}

/* ════════════════════════════════════════════════════════
 * RENDER — GELİŞTİRİCİLER
 * ════════════════════════════════════════════════════════ */
static void renderCredits(App& app) {
    Gfx g = { (float)app.W, (float)app.H, app.colProg, app.texProg };
    float sw = g.sw, sh = g.sh;

    glViewport(0, 0, app.W, app.H);
    glClearColor(C_BG[0], C_BG[1], C_BG[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (app.texBG) drawTex(g, app.texBG, 0, 0, sw, sh, 0.25f);
    float ov[4]={C_BG[0],C_BG[1],C_BG[2],0.72f};
    drawRect(g,0,0,sw,sh,ov);
    drawScanlines(g);

    float ps  = sw * 0.026f;
    float dp  = sw * 0.020f;
    float dps = sw * 0.013f;
    drawTextCentered(g, "GELİSTİRİCİLER", sw*0.5f, sh*0.06f, ps,  C_GOLD);
    drawRect(g, sw*0.10f, sh*0.145f, sw*0.80f, sh*0.003f, C_GOLD_DIM);
    drawTextCentered(g, "BONECASTOFFICIAL", sw*0.5f, sh*0.35f, dp,  C_GOLD);
    drawTextCentered(g, "OYUN TASARIMI & GELISTIRME",
                     sw*0.5f, sh*0.46f, dps, C_GOLD_DIM);
    drawTextCentered(g, "MUHUR 2025", sw*0.5f, sh*0.52f, dps, C_GOLD_DIM);

    float bw = sw*0.55f, bh = sh*0.075f;
    float bx = (sw-bw)*0.5f;
    app.cBtn[0] = { bx, sh*0.70f, bw, bh, true, app.cBtn[0].pressed, "GERİ" };
    drawBtn(g, app.cBtn[0], sw*0.022f);

    eglSwapBuffers(app.eglDpy, app.eglSuf);
}

/* ════════════════════════════════════════════════════════
 * DOKUNMA YÖNETİMİ
 * ════════════════════════════════════════════════════════ */
static void handleTap(App& app, float tx, float ty,
                      android_app* aapp) {
    switch (app.scr) {
        case SCR_MENU:
            if      (hitTest(app.mBtn[0],tx,ty)) {
                app.scr = SCR_BOOT;
                app.bootStart = nowMs();
            }
            else if (hitTest(app.mBtn[1],tx,ty) && app.hasSave) {
                LOGI("DEVAM ET");
            }
            else if (hitTest(app.mBtn[2],tx,ty)) {
                app.scr = SCR_SETTINGS;
            }
            else if (hitTest(app.mBtn[3],tx,ty)) {
                ANativeActivity_finish(aapp->activity);
            }
            break;
        case SCR_SETTINGS:
            if      (hitTest(app.sBtn[0],tx,ty)) app.soundOn = !app.soundOn;
            else if (hitTest(app.sBtn[1],tx,ty)) app.fps = 30;
            else if (hitTest(app.sBtn[2],tx,ty)) app.fps = 60;
            else if (hitTest(app.sBtn[3],tx,ty)) app.scr = SCR_MENU;
            break;
        case SCR_CREDITS:
            if (hitTest(app.cBtn[0],tx,ty)) app.scr = SCR_MENU;
            break;
        case SCR_BOOT:
            app.scr = SCR_MENU;   /* dokunarak atla */
            break;
    }
}

/* ════════════════════════════════════════════════════════
 * ANDROID CALLBACKS
 * ════════════════════════════════════════════════════════ */
static void onAppCmd(android_app* aapp, int32_t cmd) {
    App& app = *(App*)aapp->userData;
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            if (!aapp->window) break;
            /* EGL */
            {
                app.eglDpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
                eglInitialize(app.eglDpy, nullptr, nullptr);
                const EGLint att[] = {
                    EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_RED_SIZE,   8, EGL_GREEN_SIZE, 8,
                    EGL_BLUE_SIZE,  8, EGL_ALPHA_SIZE, 8,
                    EGL_NONE
                };
                EGLConfig cfg; EGLint n = 0;
                eglChooseConfig(app.eglDpy, att, &cfg, 1, &n);
                app.eglSuf = eglCreateWindowSurface(
                                 app.eglDpy, cfg, aapp->window, nullptr);
                const EGLint ca[] = {
                    EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE
                };
                app.eglCtx = eglCreateContext(
                                 app.eglDpy, cfg, EGL_NO_CONTEXT, ca);
                if (eglMakeCurrent(app.eglDpy, app.eglSuf,
                                   app.eglSuf, app.eglCtx) == EGL_FALSE) {
                    LOGE("eglMakeCurrent basarisiz");
                    break;
                }
                eglQuerySurface(app.eglDpy,app.eglSuf,EGL_WIDTH, &app.W);
                eglQuerySurface(app.eglDpy,app.eglSuf,EGL_HEIGHT,&app.H);
            }
            /* GL */
            app.colProg = createProgram(VS_C, FS_C);
            app.texProg = createProgram(VS_T, FS_T);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            /* Assets */
            app.am = aapp->activity->assetManager;
            app.texBG   = loadTexture(app.am,"menu_bg.png",&app.bgW,&app.bgH);
            app.texLogo = loadTexture(app.am,"logo.png",   &app.lgW,&app.lgH);
            /* Buton press state sıfırla */
            memset(app.mBtn, 0, sizeof(app.mBtn));
            memset(app.sBtn, 0, sizeof(app.sBtn));
            memset(app.cBtn, 0, sizeof(app.cBtn));
            app.lastFrameMs = nowMs();
            app.ready = true;
            LOGI("GL hazir — %dx%d", app.W, app.H);
            break;

        case APP_CMD_TERM_WINDOW:
            app.ready = false;
            if (app.texBG)   { glDeleteTextures(1,&app.texBG);   app.texBG=0; }
            if (app.texLogo) { glDeleteTextures(1,&app.texLogo); app.texLogo=0; }
            if (app.colProg) { glDeleteProgram(app.colProg); app.colProg=0; }
            if (app.texProg) { glDeleteProgram(app.texProg); app.texProg=0; }
            if (app.eglDpy != EGL_NO_DISPLAY) {
                eglMakeCurrent(app.eglDpy,
                               EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
                if (app.eglCtx!=EGL_NO_CONTEXT)
                    eglDestroyContext(app.eglDpy, app.eglCtx);
                if (app.eglSuf!=EGL_NO_SURFACE)
                    eglDestroySurface(app.eglDpy, app.eglSuf);
                eglTerminate(app.eglDpy);
            }
            app.eglDpy=EGL_NO_DISPLAY;
            app.eglCtx=EGL_NO_CONTEXT;
            app.eglSuf=EGL_NO_SURFACE;
            break;

        case APP_CMD_WINDOW_RESIZED:
        case APP_CMD_CONFIG_CHANGED:
            if (app.eglDpy != EGL_NO_DISPLAY) {
                eglQuerySurface(app.eglDpy,app.eglSuf,EGL_WIDTH, &app.W);
                eglQuerySurface(app.eglDpy,app.eglSuf,EGL_HEIGHT,&app.H);
            }
            break;

        default: break;
    }
}

static int32_t onInputEvent(android_app* aapp, AInputEvent* evt) {
    App& app = *(App*)aapp->userData;
    if (AInputEvent_getType(evt) != AINPUT_EVENT_TYPE_MOTION) return 0;

    int32_t action = AMotionEvent_getAction(evt) & AMOTION_EVENT_ACTION_MASK;
    float   tx     = AMotionEvent_getX(evt, 0);
    float   ty     = AMotionEvent_getY(evt, 0);

    auto clearPress = [&](){
        for (auto& b : app.mBtn) b.pressed = false;
        for (auto& b : app.sBtn) b.pressed = false;
        for (auto& b : app.cBtn) b.pressed = false;
    };

    if (action == AMOTION_EVENT_ACTION_DOWN) {
        for (auto& b : app.mBtn) if (hitTest(b,tx,ty)) b.pressed=true;
        for (auto& b : app.sBtn) if (hitTest(b,tx,ty)) b.pressed=true;
        for (auto& b : app.cBtn) if (hitTest(b,tx,ty)) b.pressed=true;
    } else if (action == AMOTION_EVENT_ACTION_UP) {
        clearPress();
        handleTap(app, tx, ty, aapp);
    } else if (action == AMOTION_EVENT_ACTION_CANCEL) {
        clearPress();
    }
    return 1;
}

/* ════════════════════════════════════════════════════════
 * GİRİŞ NOKTASI
 * ════════════════════════════════════════════════════════ */
void android_main(android_app* aapp) {
    App app;
    aapp->userData     = &app;
    aapp->onAppCmd     = onAppCmd;
    aapp->onInputEvent = onInputEvent;

    LOGI("MUHUR basliyor -- 'Kaderin, muhrunun ucunda.'");

    while (!aapp->destroyRequested) {
        int                  events = 0;
        android_poll_source* source = nullptr;
        int timeout = app.ready ? 0 : -1;

        while (ALooper_pollOnce(timeout, nullptr, &events,
                                (void**)&source) >= 0) {
            if (source) source->process(aapp, source);
            if (aapp->destroyRequested) break;
        }

        if (!app.ready) continue;

        /* FPS kısıtlama */
        long now     = nowMs();
        long frameMs = (app.fps == 30) ? 33L : 16L;
        if (now - app.lastFrameMs < frameMs) {
            usleep(1000);
            continue;
        }
        app.lastFrameMs = now;

        switch (app.scr) {
            case SCR_MENU:     renderMenu(app);     break;
            case SCR_SETTINGS: renderSettings(app); break;
            case SCR_BOOT:     renderBoot(app);     break;
            case SCR_CREDITS:  renderCredits(app);  break;
        }
    }

    LOGI("MUHUR kapaniyor");
}

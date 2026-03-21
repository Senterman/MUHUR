/**
 * MÜHÜR - Motorsuz Politik Strateji Oyunu
 * Android Native Activity - OpenGL ES 2.0 Renderer
 *
 * Bu dosya uygulamanın giriş noktasıdır.
 * logo.png dosyasını ekranda gösterir ve dokunma girişini dinler.
 */

#include <android/log.h>
#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/asset_manager.h>
#include <cstring>
#include <cstdlib>
#include <cmath>

#define LOG_TAG "MUHUR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─────────────────────────────────────────────
// Minimal PNG decoder (zlib-free, sadece RGBA8)
// Sadece basit, sıkıştırılmamış veya deflate PNG'ler için.
// Production'da stb_image kullanılması önerilir.
// ─────────────────────────────────────────────
#include "png_loader.h"   // aşağıda ayrı dosya olarak verilecek

// ─────────────────────────────────────────────
// GLSL Shader Kaynakları
// ─────────────────────────────────────────────
static const char* VERT_SRC = R"(
    attribute vec2 a_pos;
    attribute vec2 a_uv;
    varying   vec2 v_uv;
    void main(){
        gl_Position = vec4(a_pos, 0.0, 1.0);
        v_uv = a_uv;
    }
)";

static const char* FRAG_SRC = R"(
    precision mediump float;
    uniform sampler2D u_tex;
    varying vec2 v_uv;
    void main(){
        gl_FragColor = texture2D(u_tex, v_uv);
    }
)";

// ─────────────────────────────────────────────
// Uygulama Durumu
// ─────────────────────────────────────────────
struct AppState {
    EGLDisplay display  = EGL_NO_DISPLAY;
    EGLSurface surface  = EGL_NO_SURFACE;
    EGLContext context  = EGL_NO_CONTEXT;

    int32_t    width    = 0;
    int32_t    height   = 0;
    bool       running  = false;

    GLuint     program  = 0;
    GLuint     vbo      = 0;
    GLuint     texture  = 0;

    // Logo boyutu (piksel cinsinden, aspect ratio korumak için)
    int        logoW    = 1;
    int        logoH    = 1;
};

// ─────────────────────────────────────────────
// Shader Derleme Yardımcıları
// ─────────────────────────────────────────────
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[512]; glGetShaderInfoLog(s, 512, nullptr, buf);
        LOGE("Shader compile error: %s", buf);
        glDeleteShader(s); return 0;
    }
    return s;
}

static GLuint buildProgram(const char* vert, const char* frag) {
    GLuint v = compileShader(GL_VERTEX_SHADER,   vert);
    GLuint f = compileShader(GL_FRAGMENT_SHADER, frag);
    if (!v || !f) return 0;
    GLuint p = glCreateProgram();
    glAttachShader(p, v); glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v); glDeleteShader(f);
    GLint ok; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { LOGE("Program link failed"); glDeleteProgram(p); return 0; }
    return p;
}

// ─────────────────────────────────────────────
// EGL Başlatma
// ─────────────────────────────────────────────
static bool initEGL(AppState& st, ANativeWindow* window) {
    st.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(st.display, nullptr, nullptr);

    const EGLint attribs[] = {
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_BLUE_SIZE,  8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE,   8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config; EGLint numCfg;
    eglChooseConfig(st.display, attribs, &config, 1, &numCfg);

    st.surface = eglCreateWindowSurface(st.display, config, window, nullptr);

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    st.context = eglCreateContext(st.display, config, EGL_NO_CONTEXT, ctxAttribs);

    if (eglMakeCurrent(st.display, st.surface, st.surface, st.context) == EGL_FALSE) {
        LOGE("eglMakeCurrent failed");
        return false;
    }
    eglQuerySurface(st.display, st.surface, EGL_WIDTH,  &st.width);
    eglQuerySurface(st.display, st.surface, EGL_HEIGHT, &st.height);
    LOGI("EGL OK — %dx%d", st.width, st.height);
    return true;
}

// ─────────────────────────────────────────────
// Texture Yükleme (Assets'ten PNG)
// ─────────────────────────────────────────────
static GLuint loadTextureFromAsset(AAssetManager* am, const char* path,
                                   int& outW, int& outH) {
    AAsset* asset = AAssetManager_open(am, path, AASSET_MODE_BUFFER);
    if (!asset) { LOGE("Asset bulunamadı: %s", path); return 0; }

    const uint8_t* data = (const uint8_t*)AAsset_getBuffer(asset);
    int            size = (int)AAsset_getLength(asset);

    uint8_t* pixels = nullptr;
    int w = 0, h = 0;
    // png_loader.h içindeki minimal decoder
    if (!decodePNG(data, size, &pixels, &w, &h)) {
        LOGE("PNG decode başarısız: %s", path);
        AAsset_close(asset);
        return 0;
    }
    AAsset_close(asset);

    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,     GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    free(pixels);

    outW = w; outH = h;
    LOGI("Texture yüklendi: %s (%dx%d) → GL id=%u", path, w, h, tex);
    return tex;
}

// ─────────────────────────────────────────────
// VBO: Logo'yu ekranda ortalayan quad
// Aspect ratio korunarak ekranın %60'ına sığdırılır.
// ─────────────────────────────────────────────
static void buildLogoQuad(AppState& st) {
    float screenAR = (float)st.width  / (float)st.height;
    float logoAR   = (float)st.logoW  / (float)st.logoH;

    // NDC'de maksimum %60 genişlik / yükseklik
    float hw, hh;
    if (logoAR > screenAR) {
        hw = 0.60f;
        hh = hw / logoAR * screenAR;
    } else {
        hh = 0.60f;
        hw = hh * logoAR / screenAR;
    }

    // pos(x,y)  uv(u,v)
    float verts[] = {
        -hw, -hh,   0.0f, 1.0f,
         hw, -hh,   1.0f, 1.0f,
        -hw,  hh,   0.0f, 0.0f,
         hw,  hh,   1.0f, 0.0f,
    };

    if (!st.vbo) glGenBuffers(1, &st.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, st.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
}

// ─────────────────────────────────────────────
// OpenGL Kaynakları Başlat
// ─────────────────────────────────────────────
static bool initGL(AppState& st, AAssetManager* am) {
    st.program = buildProgram(VERT_SRC, FRAG_SRC);
    if (!st.program) return false;

    st.texture = loadTextureFromAsset(am, "logo.png", st.logoW, st.logoH);
    if (!st.texture) {
        // Texture yoksa düz renk göster (placeholder)
        LOGI("logo.png bulunamadı, placeholder kullanılıyor");
        glGenTextures(1, &st.texture);
        glBindTexture(GL_TEXTURE_2D, st.texture);
        uint8_t white[] = {255,255,255,255};
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,white);
    }

    buildLogoQuad(st);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    return true;
}

// ─────────────────────────────────────────────
// Tek Frame Render
// ─────────────────────────────────────────────
static void renderFrame(AppState& st) {
    glViewport(0, 0, st.width, st.height);
    glClearColor(0.05f, 0.03f, 0.02f, 1.0f); // Koyu, mürekkep gibi arka plan
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(st.program);

    glBindBuffer(GL_ARRAY_BUFFER, st.vbo);
    GLint aPos = glGetAttribLocation(st.program, "a_pos");
    GLint aUV  = glGetAttribLocation(st.program, "a_uv");
    glEnableVertexAttribArray(aPos);
    glEnableVertexAttribArray(aUV);
    glVertexAttribPointer(aPos, 2, GL_FLOAT, GL_FALSE, 4*sizeof(float), (void*)0);
    glVertexAttribPointer(aUV,  2, GL_FLOAT, GL_FALSE, 4*sizeof(float), (void*)(2*sizeof(float)));

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, st.texture);
    glUniform1i(glGetUniformLocation(st.program, "u_tex"), 0);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    eglSwapBuffers(st.display, st.surface);
}

// ─────────────────────────────────────────────
// Kaynak Temizleme
// ─────────────────────────────────────────────
static void destroyGL(AppState& st) {
    if (st.texture) { glDeleteTextures(1, &st.texture); st.texture = 0; }
    if (st.vbo)     { glDeleteBuffers(1, &st.vbo);      st.vbo     = 0; }
    if (st.program) { glDeleteProgram(st.program);      st.program = 0; }
}

static void destroyEGL(AppState& st) {
    if (st.display != EGL_NO_DISPLAY) {
        eglMakeCurrent(st.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (st.context != EGL_NO_CONTEXT) eglDestroyContext(st.display, st.context);
        if (st.surface != EGL_NO_SURFACE) eglDestroySurface(st.display, st.surface);
        eglTerminate(st.display);
    }
    st.display = EGL_NO_DISPLAY;
    st.context = EGL_NO_CONTEXT;
    st.surface = EGL_NO_SURFACE;
}

// ─────────────────────────────────────────────
// Android Event Callback
// ─────────────────────────────────────────────
static void onAppCmd(android_app* app, int32_t cmd) {
    AppState& st = *(AppState*)app->userData;
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            if (app->window) {
                initEGL(st, app->window);
                initGL(st, app->activity->assetManager);
                st.running = true;
                LOGI("Pencere hazır → oyun döngüsü başlıyor");
            }
            break;
        case APP_CMD_TERM_WINDOW:
            st.running = false;
            destroyGL(st);
            destroyEGL(st);
            LOGI("Pencere kapatıldı");
            break;
        case APP_CMD_WINDOW_RESIZED:
        case APP_CMD_CONFIG_CHANGED:
            eglQuerySurface(st.display, st.surface, EGL_WIDTH,  &st.width);
            eglQuerySurface(st.display, st.surface, EGL_HEIGHT, &st.height);
            buildLogoQuad(st); // Quad'ı yeni boyuta göre yeniden hesapla
            break;
        case APP_CMD_GAINED_FOCUS:
            LOGI("Focus kazanıldı");
            break;
        case APP_CMD_LOST_FOCUS:
            LOGI("Focus kaybedildi");
            break;
        default:
            break;
    }
}

static int32_t onInputEvent(android_app* /*app*/, AInputEvent* event) {
    if (AInputEvent_getType(event) == AINPUT_EVENT_TYPE_MOTION) {
        int32_t action = AMotionEvent_getAction(event) & AMOTION_EVENT_ACTION_MASK;
        if (action == AMOTION_EVENT_ACTION_UP) {
            float x = AMotionEvent_getX(event, 0);
            float y = AMotionEvent_getY(event, 0);
            LOGI("Dokunma: (%.1f, %.1f) → ileriki versiyonda kart etkileşimi buraya", x, y);
        }
        return 1; // tüketildi
    }
    return 0;
}

// ─────────────────────────────────────────────
// Uygulama Giriş Noktası
// ─────────────────────────────────────────────
void android_main(android_app* app) {
    AppState state;
    app->userData     = &state;
    app->onAppCmd     = onAppCmd;
    app->onInputEvent = onInputEvent;

    LOGI("══════════════════════════════");
    LOGI("  MÜHÜR başlatılıyor...");
    LOGI("══════════════════════════════");

    while (!app->destroyRequested) {
        int events;
        android_poll_source* source;

        // Pencere hazır değilse olay bekle, hazırsa 0ms timeout ile döngü kur
        int timeout = state.running ? 0 : -1;
        while (ALooper_pollOnce(timeout, nullptr, &events, (void**)&source) >= 0) {
            if (source) source->process(app, source);
            if (app->destroyRequested) break;
        }

        if (state.running) {
            renderFrame(state);
        }
    }

    LOGI("MÜHÜR kapatılıyor...");
    destroyGL(state);
    destroyEGL(state);
}

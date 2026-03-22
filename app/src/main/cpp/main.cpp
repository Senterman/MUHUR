/**
 * MÜHÜR — main.cpp (Visual Debug Sürümü)
 *
 * EGL hata durumlarını EKRAN RENGİYLE gösterir:
 *   Kırmızı  = eglInitialize başarısız
 *   Turuncu  = eglChooseConfig başarısız
 *   Sarı     = eglCreateWindowSurface başarısız
 *   Yeşil    = eglCreateContext başarısız
 *   Mavi     = eglMakeCurrent başarısız
 *   Mor      = eglSwapBuffers başarısız
 *   AMBER    = Her şey OK, döngü çalışıyor ← bunu görmen lazım
 */

#include <android_native_app_glue.h>
#include <android/asset_manager.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cmath>
#include <unistd.h>
#include <time.h>

#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── Durum ── */
static EGLDisplay g_dpy = EGL_NO_DISPLAY;
static EGLSurface g_suf = EGL_NO_SURFACE;
static EGLContext g_ctx = EGL_NO_CONTEXT;
static int32_t    g_W   = 0, g_H = 0;
static bool       g_ok  = false;
static int        g_errorStage = 0; /* 0=ok, 1-6=hata aşaması */
static int        g_frameCount = 0;

/* ── Zaman ── */
static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}
static long g_lastFrame = 0;

/* ── Hata renkleri ── */
static void setErrorColor(){
    switch(g_errorStage){
        case 1: glClearColor(0.8f,0.0f,0.0f,1.f); break; /* Kırmızı  = eglInit */
        case 2: glClearColor(0.8f,0.4f,0.0f,1.f); break; /* Turuncu  = config  */
        case 3: glClearColor(0.8f,0.8f,0.0f,1.f); break; /* Sarı     = surface */
        case 4: glClearColor(0.0f,0.7f,0.0f,1.f); break; /* Yeşil    = context */
        case 5: glClearColor(0.0f,0.0f,0.8f,1.f); break; /* Mavi     = makeCur */
        case 6: glClearColor(0.6f,0.0f,0.8f,1.f); break; /* Mor      = swap    */
        default:glClearColor(0.4f,0.3f,0.05f,1.f);break; /* Amber    = OK      */
    }
}

/* ── EGL Başlatma ── */
static bool initEGL(ANativeWindow* win){
    /* 1. Display */
    g_dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if(g_dpy == EGL_NO_DISPLAY){ g_errorStage=1; LE("getDisplay fail"); return false; }

    EGLint major=0, minor=0;
    if(!eglInitialize(g_dpy, &major, &minor)){
        g_errorStage=1; LE("eglInitialize fail 0x%x",eglGetError()); return false;
    }
    LI("EGL v%d.%d", major, minor);

    /* 2. Config — mümkün olan en basit konfigürasyon */
    const EGLint attribs[] = {
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,   8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE,  8,
        EGL_NONE
    };
    EGLConfig config; EGLint numConfigs=0;
    if(!eglChooseConfig(g_dpy, attribs, &config, 1, &numConfigs) || numConfigs==0){
        /* Daha da basit dene */
        const EGLint attribs2[] = {
            EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_NONE
        };
        if(!eglChooseConfig(g_dpy, attribs2, &config, 1, &numConfigs) || numConfigs==0){
            g_errorStage=2; LE("chooseConfig fail 0x%x",eglGetError()); return false;
        }
        LI("Fallback config kullanılıyor");
    }
    LI("Config OK numConfigs=%d", numConfigs);

    /* 3. Surface */
    g_suf = eglCreateWindowSurface(g_dpy, config, win, nullptr);
    if(g_suf == EGL_NO_SURFACE){
        g_errorStage=3; LE("createSurface fail 0x%x",eglGetError()); return false;
    }
    LI("Surface OK");

    /* 4. Context */
    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    g_ctx = eglCreateContext(g_dpy, config, EGL_NO_CONTEXT, ctxAttribs);
    if(g_ctx == EGL_NO_CONTEXT){
        g_errorStage=4; LE("createContext fail 0x%x",eglGetError()); return false;
    }
    LI("Context OK");

    /* 5. MakeCurrent */
    if(eglMakeCurrent(g_dpy, g_suf, g_suf, g_ctx) == EGL_FALSE){
        g_errorStage=5; LE("makeCurrent fail 0x%x",eglGetError()); return false;
    }
    LI("MakeCurrent OK");

    /* 6. V-Sync kapat */
    eglSwapInterval(g_dpy, 0);
    LI("SwapInterval=0 (vsync off)");

    /* 7. Surface boyutu */
    eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
    eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
    LI("Surface %dx%d", g_W, g_H);

    /* 8. Test rengi bas — hemen */
    glViewport(0, 0, g_W, g_H);
    glClearColor(0.4f, 0.3f, 0.05f, 1.f); /* Amber */
    glClear(GL_COLOR_BUFFER_BIT);
    EGLBoolean swapResult = eglSwapBuffers(g_dpy, g_suf);
    LI("Test swap result=%d err=0x%x", (int)swapResult, eglGetError());
    if(!swapResult){
        g_errorStage=6; LE("ilk swap başarısız!");
        /* Swap başarısız ama devam et, belki sonraki frame çalışır */
    }

    g_errorStage = 0; /* Tüm aşamalar OK */
    return true;
}

static void termEGL(){
    if(g_dpy != EGL_NO_DISPLAY){
        eglMakeCurrent(g_dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if(g_ctx != EGL_NO_CONTEXT){ eglDestroyContext(g_dpy, g_ctx); g_ctx=EGL_NO_CONTEXT; }
        if(g_suf != EGL_NO_SURFACE){ eglDestroySurface(g_dpy, g_suf); g_suf=EGL_NO_SURFACE; }
        eglTerminate(g_dpy); g_dpy=EGL_NO_DISPLAY;
    }
}

/* ── Render ── */
static void renderFrame(){
    if(g_W==0 || g_H==0) return;

    glViewport(0, 0, g_W, g_H);
    setErrorColor();
    glClear(GL_COLOR_BUFFER_BIT);

    EGLBoolean ok = eglSwapBuffers(g_dpy, g_suf);
    if(!ok){
        EGLint err = eglGetError();
        LE("swap fail frame=%d err=0x%x", g_frameCount, err);
        g_errorStage = 6;

        /* EGL_BAD_SURFACE veya EGL_CONTEXT_LOST durumunda yeniden başlat */
        if(err == EGL_BAD_SURFACE || err == EGL_CONTEXT_LOST ||
           err == EGL_BAD_NATIVE_WINDOW){
            LI("EGL yeniden başlatılıyor...");
            termEGL();
            g_ok = false;
        }
    } else {
        if(g_frameCount < 5 || g_frameCount % 60 == 0)
            LI("swap OK frame=%d stage=%d", g_frameCount, g_errorStage);
        if(g_errorStage == 6) g_errorStage = 0; /* İlk swap sonrası düzeldiyse */
    }
    g_frameCount++;
}

/* ── Android Callbacks ── */
static void onCmd(android_app* aapp, int32_t cmd){
    switch(cmd){
    case APP_CMD_INIT_WINDOW:
        LI("=== APP_CMD_INIT_WINDOW ===");
        if(!aapp->window){ LE("window null"); break; }
        g_frameCount = 0;
        g_errorStage = 0;
        if(initEGL(aapp->window)){
            g_ok = true;
            g_lastFrame = nowMs();
            LI("=== EGL HAZIR ===");
        } else {
            LI("EGL başlatma başarısız stage=%d", g_errorStage);
            /* Hata durumunda bile g_ok=true yap ki hata rengini gösterebilelim */
            /* Ancak EGL olmadan glClear çağıramayız, bu durumu özel handle et */
        }
        break;

    case APP_CMD_TERM_WINDOW:
        LI("APP_CMD_TERM_WINDOW");
        g_ok = false;
        termEGL();
        break;

    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if(g_dpy != EGL_NO_DISPLAY){
            eglQuerySurface(g_dpy, g_suf, EGL_WIDTH,  &g_W);
            eglQuerySurface(g_dpy, g_suf, EGL_HEIGHT, &g_H);
            LI("Resize %dx%d", g_W, g_H);
        }
        break;

    case APP_CMD_GAINED_FOCUS:
        LI("Focused");
        break;

    default: break;
    }
}

static int32_t onInput(android_app*, AInputEvent* evt){
    /* Dokunma: sahneyi ilerlet (şimdi sadece log) */
    if(AInputEvent_getType(evt) == AINPUT_EVENT_TYPE_MOTION){
        int32_t act = AMotionEvent_getAction(evt) & AMOTION_EVENT_ACTION_MASK;
        if(act == AMOTION_EVENT_ACTION_UP)
            LI("Dokunma: stage=%d frame=%d", g_errorStage, g_frameCount);
        return 1;
    }
    return 0;
}

/* ── Giriş Noktası ── */
void android_main(android_app* aapp){
    LI("====== android_main BASLADI ======");

    aapp->userData     = nullptr;
    aapp->onAppCmd     = onCmd;
    aapp->onInputEvent = onInput;

    LI("Event loop basliyor");

    while(!aapp->destroyRequested){
        int                  events = 0;
        android_poll_source* source = nullptr;

        /* g_ok=false ise olayları bekle (timeout=-1)
         * g_ok=true  ise hemen dön   (timeout=0)  */
        int timeout = g_ok ? 0 : -1;

        while(ALooper_pollOnce(timeout, nullptr, &events, (void**)&source) >= 0){
            if(source) source->process(aapp, source);
            if(aapp->destroyRequested) break;
        }

        if(!g_ok) continue;

        /* ~60 FPS */
        long now = nowMs();
        if(now - g_lastFrame < 16L){ usleep(1000); continue; }
        g_lastFrame = now;

        renderFrame();
    }

    LI("android_main BITTI");
    termEGL();
}

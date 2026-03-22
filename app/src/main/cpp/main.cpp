/**
 * MÜHÜR — main.cpp (State Renk Testi)
 *
 * Shader YOK — sadece glClearColor ile state geçişlerini test eder:
 *   Amber  → ST_CINEMA ilk 2 sn
 *   Koyu   → ST_CINEMA (story gösteriliyor)
 *   Yeşil  → ST_MENU
 *   Mavi   → ST_SETTINGS
 *
 * Eğer sadece amber kalıyorsa: timer çalışmıyor
 * Eğer koyu görünüyorsa: story state çalışıyor
 * Eğer yeşil görünüyorsa: menüye geçiş çalışıyor
 */

#include <android_native_app_glue.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <unistd.h>
#include <time.h>

#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static EGLDisplay g_dpy = EGL_NO_DISPLAY;
static EGLSurface g_suf = EGL_NO_SURFACE;
static EGLContext g_ctx = EGL_NO_CONTEXT;
static int32_t    g_W=0, g_H=0;
static bool       g_ok=false;

/* State */
static int  g_state   = 0;  /* 0=amber, 1=story, 2=menu, 3=settings */
static int  g_scene   = 0;
static long g_stateStart = 0;
static long g_lastFrame  = 0;
static int  g_frameCount = 0;

static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}

static void renderFrame(){
    glViewport(0,0,g_W,g_H);
    long elapsed = nowMs()-g_stateStart;

    switch(g_state){
        case 0: /* Amber — başlangıç */
            glClearColor(0.4f,0.3f,0.05f,1.f);
            if(elapsed > 1000){
                g_state=1; g_scene=0; g_stateStart=nowMs();
                LI("-> ST_CINEMA");
            }
            break;
        case 1: /* Story — her sahne 2 sn */
            /* Koyu amber ton, sahne numarasına göre biraz farklı */
            glClearColor(0.08f+(float)g_scene*0.02f,
                         0.06f,0.02f,1.f);
            if(elapsed > 2000){
                g_scene++;
                g_stateStart=nowMs();
                LI("Sahne: %d",g_scene);
                if(g_scene>=6){
                    g_state=2; g_stateStart=nowMs();
                    LI("-> ST_MENU");
                }
            }
            break;
        case 2: /* Menu — Koyu yeşil */
            glClearColor(0.02f,0.12f,0.04f,1.f);
            /* 3 sn sonra ayarlara geç */
            if(elapsed>3000){
                g_state=3; g_stateStart=nowMs();
                LI("-> ST_SETTINGS");
            }
            break;
        case 3: /* Settings — Koyu mavi */
            glClearColor(0.02f,0.04f,0.14f,1.f);
            if(elapsed>3000){
                g_state=2; g_stateStart=nowMs();
                LI("-> ST_MENU tekrar");
            }
            break;
    }
    glClear(GL_COLOR_BUFFER_BIT);

    EGLBoolean ok=eglSwapBuffers(g_dpy,g_suf);
    if(!ok) LE("swap fail frame=%d",g_frameCount);
    if(g_frameCount%60==0) LI("frame=%d state=%d scene=%d",g_frameCount,g_state,g_scene);
    g_frameCount++;
}

static void onCmd(android_app* aapp,int32_t cmd){
    switch(cmd){
    case APP_CMD_INIT_WINDOW:{
        LI("INIT_WINDOW");
        if(!aapp->window){LE("null win");break;}

        g_dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(g_dpy==EGL_NO_DISPLAY){LE("noDisp");break;}
        EGLint ma=0,mi=0;
        if(!eglInitialize(g_dpy,&ma,&mi)){LE("eglInit");break;}
        LI("EGL %d.%d",ma,mi);

        const EGLint att[]={
            EGL_SURFACE_TYPE,   EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,
            EGL_NONE};
        EGLConfig cfg; EGLint nc=0;
        if(!eglChooseConfig(g_dpy,att,&cfg,1,&nc)||nc==0){
            const EGLint a2[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
                EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_NONE};
            eglChooseConfig(g_dpy,a2,&cfg,1,&nc);
        }
        g_suf=eglCreateWindowSurface(g_dpy,cfg,aapp->window,nullptr);
        if(g_suf==EGL_NO_SURFACE){LE("surf 0x%x",eglGetError());break;}
        const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
        g_ctx=eglCreateContext(g_dpy,cfg,EGL_NO_CONTEXT,ca);
        if(g_ctx==EGL_NO_CONTEXT){LE("ctx 0x%x",eglGetError());break;}
        if(eglMakeCurrent(g_dpy,g_suf,g_suf,g_ctx)==EGL_FALSE){
            LE("makeCur 0x%x",eglGetError());break;}
        eglSwapInterval(g_dpy,0);
        eglQuerySurface(g_dpy,g_suf,EGL_WIDTH,&g_W);
        eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
        LI("size %dx%d",g_W,g_H);

        /* İlk frame */
        glViewport(0,0,g_W,g_H);
        glClearColor(0.4f,0.3f,0.05f,1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(g_dpy,g_suf);
        LI("ilk frame OK");

        g_state=0; g_scene=0;
        g_stateStart=nowMs();
        g_lastFrame=nowMs();
        g_frameCount=0;
        g_ok=true;
        LI("HAZIR");
        break;
    }
    case APP_CMD_TERM_WINDOW:
        g_ok=false;
        if(g_dpy!=EGL_NO_DISPLAY){
            eglMakeCurrent(g_dpy,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if(g_ctx!=EGL_NO_CONTEXT)eglDestroyContext(g_dpy,g_ctx);
            if(g_suf!=EGL_NO_SURFACE)eglDestroySurface(g_dpy,g_suf);
            eglTerminate(g_dpy);
        }
        g_dpy=EGL_NO_DISPLAY;g_ctx=EGL_NO_CONTEXT;g_suf=EGL_NO_SURFACE;
        break;
    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if(g_dpy!=EGL_NO_DISPLAY){
            eglQuerySurface(g_dpy,g_suf,EGL_WIDTH,&g_W);
            eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
        }
        break;
    default:break;
    }
}

static int32_t onInp(android_app*,AInputEvent* evt){
    if(!g_ok)return 0;
    if(AInputEvent_getType(evt)==AINPUT_EVENT_TYPE_MOTION){
        int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
        if(act==AMOTION_EVENT_ACTION_UP){
            /* Dokunma: state'i ilerlet */
            if(g_state==1){
                g_scene++;
                g_stateStart=nowMs();
                if(g_scene>=6){g_state=2;g_stateStart=nowMs();}
                LI("TAP -> scene=%d",g_scene);
            } else if(g_state==2){
                g_state=3; g_stateStart=nowMs();
                LI("TAP -> settings");
            } else if(g_state==3){
                g_state=2; g_stateStart=nowMs();
                LI("TAP -> menu");
            }
        }
        return 1;
    }
    return 0;
}

void android_main(android_app* aapp){
    LI("START");
    aapp->userData=nullptr;
    aapp->onAppCmd=onCmd;
    aapp->onInputEvent=onInp;

    while(!aapp->destroyRequested){
        int ev=0; android_poll_source* src=nullptr;
        int timeout=g_ok?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src)src->process(aapp,src);
            if(aapp->destroyRequested)break;
        }
        if(!g_ok)continue;

        long now=nowMs();
        if(now-g_lastFrame<16L){usleep(1000);continue;}
        g_lastFrame=now;

        renderFrame();
    }
    LI("END");
}

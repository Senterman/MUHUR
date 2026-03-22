/**
 * MÜHÜR — main.cpp  (Minimum Güvenli Sürüm)
 *
 * • Font tablosu YOK — her karakter inline çiziliyor (segment display stili)
 * • PNG yükleme YOK — saf dikdörtgen + renk
 * • new/malloc YOK   — her şey static global
 * • Tüm EGL adımları tek tek loglanıyor
 */

#include <android_native_app_glue.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <time.h>

/* ── Log ──────────────────────────────────────────────── */
#define T "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  T,__VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, T,__VA_ARGS__)

/* ── Renkler ──────────────────────────────────────────── */
static const float BG[4]   = {0.051f,0.039f,0.020f,1.f};
static const float GOLD[4] = {0.831f,0.659f,0.325f,1.f};
static const float DARK[4] = {0.090f,0.070f,0.035f,1.f};
static const float PRES[4] = {0.180f,0.140f,0.070f,1.f};
static const float GREY[4] = {0.260f,0.240f,0.220f,1.f};
static const float GDIM[4] = {0.420f,0.330f,0.160f,1.f};

/* ── Shader ───────────────────────────────────────────── */
static const char* VS =
    "attribute vec2 p;"
    "uniform vec2 r;"
    "void main(){"
    "vec2 n=(p/r)*2.0-1.0;"
    "gl_Position=vec4(n.x,-n.y,0,1);}";

static const char* FS =
    "precision mediump float;"
    "uniform vec4 c;"
    "void main(){gl_FragColor=c;}";

/* ── Global durum ─────────────────────────────────────── */
static EGLDisplay g_dpy = EGL_NO_DISPLAY;
static EGLSurface g_suf = EGL_NO_SURFACE;
static EGLContext g_ctx = EGL_NO_CONTEXT;
static GLuint     g_prog = 0;
static int        g_W = 0, g_H = 0;
static bool       g_ready = false;

/* Oyun durumu */
static bool  g_inSettings = false;
static bool  g_inBoot     = false;
static int   g_fps        = 60;
static long  g_bootStart  = 0;
static long  g_lastFrame  = 0;

/* Buton alanları (hit-test için) */
static float g_btnX[4], g_btnY[4], g_btnW[4], g_btnH[4];
static bool  g_btnPr[4];
static float g_sbX[3], g_sbY[3], g_sbW[3], g_sbH[3];

/* ── Zaman ────────────────────────────────────────────── */
static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}

/* ── Shader derleme ───────────────────────────────────── */
static GLuint mkShader(GLenum t,const char* s){
    GLuint h=glCreateShader(t);
    glShaderSource(h,1,&s,nullptr); glCompileShader(h);
    GLint ok=0; glGetShaderiv(h,GL_COMPILE_STATUS,&ok);
    if(!ok){char b[256]={};glGetShaderInfoLog(h,256,nullptr,b);LE("SH:%s",b);}
    return h;
}
static GLuint mkProg(){
    GLuint v=mkShader(GL_VERTEX_SHADER,VS);
    GLuint f=mkShader(GL_FRAGMENT_SHADER,FS);
    GLuint p=glCreateProgram();
    glAttachShader(p,v); glAttachShader(p,f);
    glLinkProgram(p);
    glDeleteShader(v); glDeleteShader(f);
    GLint ok=0; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    LI("prog id=%u ok=%d",p,ok);
    return p;
}

/* ── Temel dikdörtgen ─────────────────────────────────── */
static void rect(float x,float y,float w,float h,const float* col){
    float v[]={x,y, x+w,y, x,y+h, x+w,y+h};
    glUseProgram(g_prog);
    glUniform2f(glGetUniformLocation(g_prog,"r"),(float)g_W,(float)g_H);
    glUniform4fv(glGetUniformLocation(g_prog,"c"),1,col);
    GLuint ap=(GLuint)glGetAttribLocation(g_prog,"p");
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
}

/* ── 7-Segment tarzı pixel karakter (9×13 grid) ─────────
 * Her harf yatay/dikey çubuklar + noktalarla çizilir.
 * Hiçbir tablo yok — sadece rect() çağrısı.
 * ──────────────────────────────────────────────────────── */
static float gPs=0; /* pixel boyutu — render başında set edilir */

/* Temel segment yardımcısı */
static void seg(float ox,float oy,float x,float y,float w,float h,
                const float* col){
    rect(ox+x*gPs, oy+y*gPs, w*gPs, h*gPs, col);
}

/* Her harf için manuel segment çizimi (A-Z + 0-9 + boşluk) */
static void drawChar(char c,float ox,float oy,const float* col){
    /* Toplam karakter kutusu: 7ps geniş, 11ps yüksek */
    float sw=gPs, sh=gPs; (void)sw; (void)sh;
    /* Yatay çubuk */
    #define H(xx,yy) seg(ox,oy,(xx),(yy),5,1,col)
    /* Dikey çubuk */
    #define V(xx,yy) seg(ox,oy,(xx),(yy),1,3,col)
    /* Nokta */
    #define D(xx,yy) seg(ox,oy,(xx),(yy),1,1,col)

    switch(c){
    case 'A': H(1,0);V(0,1);V(6,1);H(1,4);V(0,5);V(6,5);H(1,9); break;
    case 'B': H(1,0);V(0,1);V(5,1);H(1,4);V(0,5);V(5,5);H(1,9); break;
    case 'C': H(1,0);V(0,1);V(0,5);H(1,9); break;
    case 'D': H(1,0);V(0,1);V(6,1);V(0,5);V(6,5);H(1,9); break;
    case 'E': H(0,0);V(0,1);H(0,4);V(0,5);H(0,9); break;
    case 'F': H(0,0);V(0,1);H(0,4);V(0,5); break;
    case 'G': H(1,0);V(0,1);H(3,4);V(0,5);V(6,5);H(1,9); break;
    case 'H': V(0,1);V(6,1);H(1,4);V(0,5);V(6,5); break;
    case 'I': H(1,0);V(3,1);V(3,5);H(1,9); break;
    case 'J': H(2,0);V(5,1);V(0,6);V(5,5);H(1,9); break;
    case 'K': V(0,1);H(1,4);V(4,2);V(4,6);V(0,5); break;
    case 'L': V(0,1);V(0,5);H(1,9); break;
    case 'M': V(0,1);V(6,1);V(0,5);V(6,5);D(3,2);D(3,3); break;
    case 'N': V(0,1);V(6,1);V(0,5);V(6,5);D(1,2);D(2,3);D(3,4);D(4,5); break;
    case 'O': H(1,0);V(0,1);V(6,1);V(0,5);V(6,5);H(1,9); break;
    case 'P': H(1,0);V(0,1);V(6,1);H(1,4);V(0,5); break;
    case 'R': H(1,0);V(0,1);V(6,1);H(1,4);V(0,5);V(4,5);V(6,6); break;
    case 'S': H(1,0);V(0,1);H(1,4);V(6,5);H(1,9); break;
    case 'T': H(0,0);V(3,1);V(3,5); break;
    case 'U': V(0,1);V(6,1);V(0,5);V(6,5);H(1,9); break;
    case 'V': V(0,1);V(5,1);V(0,5);V(5,5);D(2,8);D(3,8); break;
    case 'W': V(0,1);V(6,1);V(0,5);V(6,5);D(3,6);D(3,7); break;
    case 'Y': V(0,1);V(6,1);H(1,4);V(3,5); break;
    case 'Z': H(0,0);D(5,2);D(4,3);D(3,4);D(2,5);H(0,9); break;
    case '0': H(1,0);V(0,1);V(6,1);V(0,5);V(6,5);H(1,9); break;
    case '1': V(3,0);V(3,5); break;
    case '2': H(1,0);V(6,1);H(1,4);V(0,5);H(1,9); break;
    case '3': H(1,0);V(6,1);H(1,4);V(6,5);H(1,9); break;
    case '4': V(0,1);V(6,1);H(1,4);V(6,5); break;
    case '5': H(1,0);V(0,1);H(1,4);V(6,5);H(1,9); break;
    case '6': H(1,0);V(0,1);H(1,4);V(0,5);V(6,5);H(1,9); break;
    case '7': H(1,0);V(6,1);V(6,5); break;
    case '8': H(1,0);V(0,1);V(6,1);H(1,4);V(0,5);V(6,5);H(1,9); break;
    case '9': H(1,0);V(0,1);V(6,1);H(1,4);V(6,5);H(1,9); break;
    case ':': D(3,2);D(3,7); break;
    case '.': D(3,9); break;
    case ',': D(3,9);D(2,10); break;
    case '-': H(1,4); break;
    case '/': D(5,1);D(4,3);D(3,5);D(2,7);D(1,9); break;
    case '!': V(3,0);D(3,9); break;
    default:  break; /* boşluk ve bilinmeyenler */
    }
    #undef H
    #undef V
    #undef D
}

static void drawStr(const char* s,float x,float y,
                    float ps,const float* col){
    gPs=ps;
    float cx=x;
    while(*s){
        unsigned char c=(unsigned char)*s;
        if(c>=0x80){ /* UTF-8 multibyte: atla */
            s++; if(*s&&((unsigned char)*s&0xC0)==0x80)s++;
            cx+=9*ps; continue;
        }
        drawChar((char)(c>='a'&&c<='z'?c-32:c),cx,y,col);
        cx+=9*ps; s++;
    }
}

/* Ortaya hizala */
static int strLen(const char* s){
    int n=0;
    const unsigned char* p=(const unsigned char*)s;
    while(*p){ if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++;}else p++; n++; }
    return n;
}
static void drawStrC(const char* s,float cy,float ps,const float* col){
    float w=(float)strLen(s)*9*ps;
    drawStr(s,(float)g_W*.5f-w*.5f,cy,ps,col);
}

/* ── Buton çiz ────────────────────────────────────────── */
static void drawBtn(float x,float y,float w,float h,
                    const char* lbl,bool en,bool pr,float ps){
    float bw=(float)g_W*.004f;
    const float* gc=en?GOLD:GREY;
    float bc[4]={gc[0],gc[1],gc[2],en?1.f:.35f};
    rect(x-bw,y-bw,w+2*bw,h+2*bw,bc);
    rect(x,y,w,h,pr?PRES:DARK);
    /* köşe noktaları */
    float cs=(float)g_W*.009f;
    rect(x,y,cs,cs,bc); rect(x+w-cs,y,cs,cs,bc);
    rect(x,y+h-cs,cs,cs,bc); rect(x+w-cs,y+h-cs,cs,cs,bc);
    /* metin */
    float lc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    float tw=(float)strLen(lbl)*9*ps;
    drawStr(lbl,x+(w-tw)*.5f,y+(h-11*ps)*.5f,ps,lc);
}

/* ── Hit-test ─────────────────────────────────────────── */
static bool hit(float bx,float by,float bw,float bh,
                float tx,float ty){
    return tx>=bx&&tx<=bx+bw&&ty>=by&&ty<=by+bh;
}

/* ════════════════════════════════════════════════════════
 * RENDER FONKSİYONLARI
 * ════════════════════════════════════════════════════════ */

static void renderMenu(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1.f); glClear(GL_COLOR_BUFFER_BIT);

    /* Tarama çizgileri */
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=5.f) rect(0,y,sw,1.5f,sc);

    /* Şeritler */
    rect(0,sh*.008f,sw,sh*.004f,GOLD);
    rect(0,sh*.972f,sw,sh*.004f,GOLD);

    /* Başlık */
    float ps=sw*.010f;
    drawStrC("MUHUR",sh*.07f,ps,GOLD);

    /* Motto çizgisi */
    rect(sw*.10f,sh*.175f,sw*.80f,sh*.002f,GDIM);

    /* Motto */
    float mps=sw*.007f;
    drawStrC("KADERIN MUHRUNUN UCUNDA",sh*.185f,mps,GDIM);

    /* Ayraç */
    rect(sw*.08f,sh*.235f,sw*.84f,sh*.003f,GOLD);

    /* Butonlar */
    float bw=sw*.72f, bh=sh*.078f;
    float bx=(sw-bw)*.5f, gap=sh*.022f;
    float sy=sh*.26f;
    float bp=sw*.013f;

    const char* lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool  en[4]={true,false,true,true}; /* DEVAM ET sönük */

    for(int i=0;i<4;i++){
        g_btnX[i]=bx; g_btnY[i]=sy+(float)i*(bh+gap);
        g_btnW[i]=bw; g_btnH[i]=bh;
        drawBtn(bx,sy+(float)i*(bh+gap),bw,bh,lb[i],en[i],g_btnPr[i],bp);
    }

    /* Alt bilgi */
    float dps=sw*.006f;
    float dw=(float)strLen("BONECASTOFFICIAL")*9*dps;
    drawStr("BONECASTOFFICIAL",sw*.5f-dw*.5f,sh*.930f,dps,GDIM);
}

static void renderBoot(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
    long el=nowMs()-g_bootStart;
    /* İlerleme çubuğu */
    float prog=(float)el/2000.f; if(prog>1)prog=1;
    float bx=sw*.15f,by=sh*.52f,bw=sw*.70f,bh=sh*.018f;
    rect(bx,by,bw,bh,DARK); rect(bx,by,bw*prog,bh,GOLD);
    float ps=sw*.009f;
    drawStrC("SISTEM BASLATILIYOR",sh*.40f,ps,GOLD);
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=5.f) rect(0,y,sw,1.5f,sc);
    if(el>2200) g_inBoot=false;
}

static void renderSettings(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=5.f) rect(0,y,sw,1.5f,sc);
    rect(0,sh*.008f,sw,sh*.004f,GOLD);
    float ps=sw*.010f;
    drawStrC("AYARLAR",sh*.07f,ps,GOLD);
    rect(sw*.10f,sh*.155f,sw*.80f,sh*.003f,GDIM);

    float bw=sw*.70f,bh=sh*.080f,bx=(sw-bw)*.5f,bp=sw*.012f;

    /* FPS 60 */
    float hl60[4]={GOLD[0],GOLD[1],GOLD[2],g_fps==60?.18f:.0f};
    rect(bx,sh*.22f,bw,bh,hl60);
    g_sbX[0]=bx;g_sbY[0]=sh*.22f;g_sbW[0]=bw;g_sbH[0]=bh;
    drawBtn(bx,sh*.22f,bw,bh,"FPS 60",true,false,bp);

    /* FPS 30 */
    float hl30[4]={GOLD[0],GOLD[1],GOLD[2],g_fps==30?.18f:.0f};
    rect(bx,sh*.34f,bw,bh,hl30);
    g_sbX[1]=bx;g_sbY[1]=sh*.34f;g_sbW[1]=bw;g_sbH[1]=bh;
    drawBtn(bx,sh*.34f,bw,bh,"FPS 30",true,false,bp);

    /* Geri */
    g_sbX[2]=bx;g_sbY[2]=sh*.56f;g_sbW[2]=bw;g_sbH[2]=bh;
    drawBtn(bx,sh*.56f,bw,bh,"GERI",true,false,bp);

    char ft[24]; snprintf(ft,sizeof(ft),"HEDEF %d FPS",g_fps);
    float fps_ps=sw*.007f;
    float fw=(float)strLen(ft)*9*fps_ps;
    drawStr(ft,sw*.5f-fw*.5f,sh*.73f,fps_ps,GDIM);
}

/* ════════════════════════════════════════════════════════
 * ANDROID CALLBACKS
 * ════════════════════════════════════════════════════════ */
static void onCmd(android_app* aapp, int32_t cmd){
    switch(cmd){

    case APP_CMD_INIT_WINDOW:{
        LI("=== INIT_WINDOW ===");
        if(!aapp->window){ LE("window null"); break; }

        /* EGL — her adım ayrı kontrol */
        g_dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(g_dpy==EGL_NO_DISPLAY){LE("getDisplay fail");break;}
        LI("getDisplay OK");

        EGLint ma=0,mi=0;
        if(!eglInitialize(g_dpy,&ma,&mi)){LE("init fail");break;}
        LI("eglInit v%d.%d",ma,mi);

        const EGLint att[]={
            EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,
            EGL_BLUE_SIZE,8,EGL_NONE};
        EGLConfig cfg; EGLint nc=0;
        if(!eglChooseConfig(g_dpy,att,&cfg,1,&nc)||nc==0){
            LE("chooseConfig fail nc=%d",nc); break;}
        LI("chooseConfig OK");

        g_suf=eglCreateWindowSurface(g_dpy,cfg,aapp->window,nullptr);
        if(g_suf==EGL_NO_SURFACE){
            LE("createSurface fail 0x%x",eglGetError()); break;}
        LI("createSurface OK");

        const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
        g_ctx=eglCreateContext(g_dpy,cfg,EGL_NO_CONTEXT,ca);
        if(g_ctx==EGL_NO_CONTEXT){
            LE("createContext fail 0x%x",eglGetError()); break;}
        LI("createContext OK");

        if(eglMakeCurrent(g_dpy,g_suf,g_suf,g_ctx)==EGL_FALSE){
            LE("makeCurrent fail 0x%x",eglGetError()); break;}
        LI("makeCurrent OK");

        eglQuerySurface(g_dpy,g_suf,EGL_WIDTH, &g_W);
        eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
        LI("surface %dx%d",g_W,g_H);

        /* AŞAMA 1: Düz renk — shader olmadan */
        glViewport(0,0,g_W,g_H);
        glClearColor(BG[0],BG[1],BG[2],1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(g_dpy,g_suf);
        LI("AŞAMA1 clearColor OK");

        /* AŞAMA 2: Shader */
        g_prog=mkProg();
        if(!g_prog){ LE("prog fail"); break; }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        LI("AŞAMA2 shader OK");

        memset(g_btnPr,0,sizeof(g_btnPr));
        g_lastFrame=nowMs();
        g_ready=true;
        LI("=== HAZIR ===");
        break;
    }

    case APP_CMD_TERM_WINDOW:
        LI("TERM_WINDOW");
        g_ready=false;
        if(g_prog){glDeleteProgram(g_prog);g_prog=0;}
        if(g_dpy!=EGL_NO_DISPLAY){
            eglMakeCurrent(g_dpy,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if(g_ctx!=EGL_NO_CONTEXT) eglDestroyContext(g_dpy,g_ctx);
            if(g_suf!=EGL_NO_SURFACE) eglDestroySurface(g_dpy,g_suf);
            eglTerminate(g_dpy);
        }
        g_dpy=EGL_NO_DISPLAY; g_ctx=EGL_NO_CONTEXT; g_suf=EGL_NO_SURFACE;
        break;

    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if(g_dpy!=EGL_NO_DISPLAY){
            eglQuerySurface(g_dpy,g_suf,EGL_WIDTH, &g_W);
            eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
            LI("resize %dx%d",g_W,g_H);
        }
        break;

    default: break;
    }
}

static int32_t onInp(android_app* aapp, AInputEvent* evt){
    if(!g_ready) return 0;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION) return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float tx=AMotionEvent_getX(evt,0), ty=AMotionEvent_getY(evt,0);

    if(act==AMOTION_EVENT_ACTION_DOWN){
        if(!g_inSettings&&!g_inBoot)
            for(int i=0;i<4;i++)
                g_btnPr[i]=hit(g_btnX[i],g_btnY[i],g_btnW[i],g_btnH[i],tx,ty);
        return 1;
    }
    if(act!=AMOTION_EVENT_ACTION_UP) return 1;
    memset(g_btnPr,0,sizeof(g_btnPr));

    if(g_inBoot){ g_inBoot=false; return 1; }

    if(g_inSettings){
        if(hit(g_sbX[0],g_sbY[0],g_sbW[0],g_sbH[0],tx,ty)) g_fps=60;
        else if(hit(g_sbX[1],g_sbY[1],g_sbW[1],g_sbH[1],tx,ty)) g_fps=30;
        else if(hit(g_sbX[2],g_sbY[2],g_sbW[2],g_sbH[2],tx,ty)) g_inSettings=false;
        return 1;
    }

    /* Ana menü */
    if(hit(g_btnX[0],g_btnY[0],g_btnW[0],g_btnH[0],tx,ty)){
        g_inBoot=true; g_bootStart=nowMs();
    } else if(hit(g_btnX[2],g_btnY[2],g_btnW[2],g_btnH[2],tx,ty)){
        g_inSettings=true;
    } else if(hit(g_btnX[3],g_btnY[3],g_btnW[3],g_btnH[3],tx,ty)){
        ANativeActivity_finish(aapp->activity);
    }
    return 1;
}

/* ════════════════════════════════════════════════════════
 * GİRİŞ NOKTASI
 * ════════════════════════════════════════════════════════ */
void android_main(android_app* aapp){
    LI("android_main START");

    /* userData gerek yok — her şey static global */
    aapp->userData    =nullptr;
    aapp->onAppCmd    =onCmd;
    aapp->onInputEvent=onInp;

    while(!aapp->destroyRequested){
        int ev=0; android_poll_source* src=nullptr;
        int timeout=g_ready?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src) src->process(aapp,src);
            if(aapp->destroyRequested) break;
        }
        if(!g_ready) continue;

        /* FPS limiter */
        long now=nowMs();
        long fm=(g_fps==30)?33L:16L;
        if(now-g_lastFrame<fm){ usleep(2000); continue; }
        g_lastFrame=now;

        if(g_inBoot)          renderBoot();
        else if(g_inSettings) renderSettings();
        else                  renderMenu();

        eglSwapBuffers(g_dpy,g_suf);
    }
    LI("android_main END");
}

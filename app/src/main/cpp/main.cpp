/**
 * MÜHÜR — main.cpp
 * Frame sayacı tabanlı — timer bağımlılığı yok
 * Splash → Ana Menü
 */

#include <android_native_app_glue.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <cstdio>
#include <cmath>
#include <unistd.h>

#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── Renkler ── */
static const float BG[4]   = {0.051f, 0.039f, 0.020f, 1.f};
static const float GOLD[4] = {0.831f, 0.659f, 0.325f, 1.f};
static const float GDIM[4] = {0.420f, 0.330f, 0.160f, 1.f};
static const float DARK[4] = {0.090f, 0.070f, 0.035f, 1.f};
static const float PRES[4] = {0.180f, 0.140f, 0.070f, 1.f};
static const float GREY[4] = {0.260f, 0.240f, 0.220f, 1.f};

/* ── Shader ── */
static const char* VS =
    "attribute vec2 p;uniform vec2 r;"
    "void main(){"
    "  vec2 n=(p/r)*2.0-1.0;"
    "  gl_Position=vec4(n.x,-n.y,0.0,1.0);"
    "}";

static const char* FS =
    "precision mediump float;"
    "uniform vec4 c;"
    "void main(){ gl_FragColor=c; }";

/* ── EGL + GL global ── */
static EGLDisplay g_dpy = EGL_NO_DISPLAY;
static EGLSurface g_suf = EGL_NO_SURFACE;
static EGLContext g_ctx = EGL_NO_CONTEXT;
static int32_t    g_W=0, g_H=0;
static bool       g_ok=false;
static GLuint     g_prog=0;

/* ── Oyun durumu ── */
enum State { ST_SPLASH, ST_MENU, ST_SETTINGS };
static State g_state = ST_SPLASH;
static int   g_frame = 0;       /* toplam frame sayısı */
static int   g_fps   = 60;
static bool  g_sndOn = true;

/* Splash: 120 frame (~2 sn @ 60fps) sonra menüye geç */
#define SPLASH_FRAMES 120

/* Buton alanları */
static float g_bx[4],g_by[4],g_bw[4],g_bh[4];
static bool  g_bp[4]={};
static float g_sx[4],g_sy[4],g_sw2[4],g_sh2[4];

/* ── Shader derleme ── */
static GLuint mkSh(GLenum t,const char* s){
    GLuint h=glCreateShader(t);
    glShaderSource(h,1,&s,nullptr); glCompileShader(h);
    GLint ok=0; glGetShaderiv(h,GL_COMPILE_STATUS,&ok);
    if(!ok){char b[256]={};glGetShaderInfoLog(h,256,nullptr,b);LE("SH:%s",b);}
    return h;
}
static GLuint mkPr(const char* v,const char* f){
    GLuint p=glCreateProgram();
    GLuint vs=mkSh(GL_VERTEX_SHADER,v);
    GLuint fs=mkSh(GL_FRAGMENT_SHADER,f);
    glAttachShader(p,vs); glAttachShader(p,fs); glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok=0; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    LI("prog=%u link=%d",p,ok);
    return p;
}

/* ── Dikdörtgen çiz ── */
static void Rect(float x,float y,float w,float h,const float* col){
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

/* ── Pixel font (5x9 segment) ── */
static float gPS=0.f;
static void SH(float ox,float oy,float x,float y,float w,const float*c){Rect(ox+x*gPS,oy+y*gPS,w*gPS,gPS,c);}
static void SV(float ox,float oy,float x,float y,float h,const float*c){Rect(ox+x*gPS,oy+y*gPS,gPS,h*gPS,c);}
static void DP(float ox,float oy,float x,float y,const float*c){Rect(ox+x*gPS,oy+y*gPS,gPS,gPS,c);}

static void drawCh(char ch,float ox,float oy,const float*c){
    switch(ch){
    case 'A':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,4,c);SV(ox,oy,4,1,4,c);SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,4,c);SV(ox,oy,4,5,4,c);break;
    case 'B':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,4,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,4,c);break;
    case 'C':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'D':SH(ox,oy,0,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);SH(ox,oy,0,8,3,c);break;
    case 'E':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case 'F':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);SV(ox,oy,0,5,4,c);break;
    case 'G':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,2,4,3,c);SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case 'H':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SH(ox,oy,1,4,3,c);break;
    case 'I':SH(ox,oy,1,0,3,c);SV(ox,oy,2,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'J':SH(ox,oy,1,0,3,c);SV(ox,oy,3,1,6,c);SV(ox,oy,0,6,2,c);SH(ox,oy,1,8,2,c);break;
    case 'K':SV(ox,oy,0,0,9,c);SH(ox,oy,1,4,3,c);SV(ox,oy,3,0,4,c);SV(ox,oy,3,5,4,c);break;
    case 'L':SV(ox,oy,0,0,8,c);SH(ox,oy,1,8,4,c);break;
    case 'M':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);DP(ox,oy,1,1,c);DP(ox,oy,2,2,c);DP(ox,oy,3,1,c);break;
    case 'N':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);DP(ox,oy,1,1,c);DP(ox,oy,2,3,c);DP(ox,oy,3,5,c);break;
    case 'O':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'P':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,4,c);break;
    case 'R':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,4,c);SV(ox,oy,3,5,4,c);break;
    case 'S':SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,3,c);SH(ox,oy,1,4,3,c);SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,4,c);break;
    case 'T':SH(ox,oy,0,0,5,c);SV(ox,oy,2,1,8,c);break;
    case 'U':SV(ox,oy,0,0,8,c);SV(ox,oy,4,0,8,c);SH(ox,oy,1,8,3,c);break;
    case 'V':SV(ox,oy,0,0,6,c);SV(ox,oy,4,0,6,c);DP(ox,oy,1,6,c);DP(ox,oy,3,6,c);DP(ox,oy,2,7,c);break;
    case 'W':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SV(ox,oy,2,5,4,c);break;
    case 'Y':SV(ox,oy,0,0,4,c);SV(ox,oy,4,0,4,c);SH(ox,oy,1,4,3,c);SV(ox,oy,2,5,4,c);break;
    case 'Z':SH(ox,oy,0,0,5,c);DP(ox,oy,3,2,c);DP(ox,oy,2,4,c);DP(ox,oy,1,6,c);SH(ox,oy,0,8,5,c);break;
    case '0':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);SH(ox,oy,1,8,3,c);break;
    case '1':SV(ox,oy,2,0,9,c);break;
    case '2':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '3':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '4':SV(ox,oy,0,0,5,c);SV(ox,oy,4,0,9,c);SH(ox,oy,0,4,5,c);break;
    case '5':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,5,c);SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '6':SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,4,4,c);SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case '7':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,4,c);SV(ox,oy,2,5,4,c);break;
    case '8':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case '9':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,7,c);SH(ox,oy,1,4,3,c);SH(ox,oy,1,8,3,c);break;
    case ':':DP(ox,oy,2,2,c);DP(ox,oy,2,6,c);break;
    case '.':DP(ox,oy,2,8,c);break;
    case '-':SH(ox,oy,1,4,3,c);break;
    case '/':DP(ox,oy,3,1,c);DP(ox,oy,2,3,c);DP(ox,oy,1,5,c);break;
    default: break;
    }
}

static int nCh(const char* s){
    int n=0;
    for(const char* p=s;*p&&*p!='\n';p++) n++;
    return n;
}
static void drawStr(const char* s,float x,float y,float ps,const float* col){
    gPS=ps; float cx=x,cy=y;
    for(const unsigned char* p=(const unsigned char*)s;*p;p++){
        if(*p=='\n'){cy+=12*ps;cx=x;continue;}
        if(*p>=0x80) continue;
        char ch=(char)*p; if(ch>='a'&&ch<='z')ch-=32;
        drawCh(ch,cx,cy,col); cx+=7*ps;
    }
}
static void drawStrC(const char* s,float cy,float ps,const float* col){
    float w=(float)nCh(s)*7*ps;
    drawStr(s,(float)g_W*.5f-w*.5f,cy,ps,col);
}

/* ── Buton ── */
static void drawBtn(float x,float y,float w,float h,
                    const char* lbl,bool en,bool pr,float ps){
    float bw2=(float)g_W*.005f;
    const float* gc=en?GOLD:GREY;
    float bc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    Rect(x-bw2,y-bw2,w+2*bw2,h+2*bw2,bc);
    Rect(x,y,w,h,pr?PRES:DARK);
    /* Köşe noktaları */
    float cs=(float)g_W*.009f;
    Rect(x,y,cs,cs,bc); Rect(x+w-cs,y,cs,cs,bc);
    Rect(x,y+h-cs,cs,cs,bc); Rect(x+w-cs,y+h-cs,cs,cs,bc);
    /* Etiket */
    float lc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    float tw=(float)nCh(lbl)*7*ps;
    drawStr(lbl,x+(w-tw)*.5f,y+(h-9*ps)*.5f,ps,lc);
}
static bool hitR(float bx,float by,float bw,float bh,float tx,float ty){
    return tx>=bx&&tx<=bx+bw&&ty>=by&&ty<=by+bh;
}

/* ════ RENDER ════ */

static void renderSplash(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    /* Tarama çizgileri */
    float sc[4]={0,0,0,.12f};
    for(float y=0;y<sh;y+=4.f) Rect(0,y,sw,1.5f,sc);

    /* Üst/alt altın şerit */
    Rect(0,sh*.010f,sw,sh*.004f,GOLD);
    Rect(0,sh*.974f,sw,sh*.004f,GOLD);

    /* Geliştirici metni */
    float ps=sw*.009f;
    drawStrC("BONECASTOFFICIAL",sh*.42f,ps,GOLD);

    float dps=sw*.007f;
    drawStrC("SUNAR",sh*.42f+12*ps,dps,GDIM);

    /* İlerleme çubuğu */
    float prog=(float)g_frame/(float)SPLASH_FRAMES;
    if(prog>1.f)prog=1.f;
    float bx=sw*.20f,by=sh*.68f,bw=sw*.60f,bh=sh*.006f;
    Rect(bx,by,bw,bh,DARK);
    Rect(bx,by,bw*prog,bh,GOLD);
    /* Çubuk üst parlaması */
    float shine[4]={GOLD[0]*1.3f,GOLD[1]*1.3f,GOLD[2]*1.3f,0.6f};
    Rect(bx,by,bw*prog,bh*.3f,shine);

    /* Android sistem bilgisi */
    char info[32]; snprintf(info,32,"ANDROID NDK");
    drawStrC(info,sh*.76f,dps,GDIM);
    drawStrC("OPENGL ES 2.0",sh*.76f+10*dps,dps,GDIM);
}

static void renderMenu(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    /* Tarama çizgileri */
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) Rect(0,y,sw,1.5f,sc);

    /* Üst/alt şerit */
    Rect(0,sh*.010f,sw,sh*.004f,GOLD);
    Rect(0,sh*.974f,sw,sh*.004f,GOLD);

    /* Başlık */
    float ps=sw*.014f;
    drawStrC("MUHUR",sh*.055f,ps,GOLD);

    /* Ayraç */
    Rect(sw*.08f,sh*.165f,sw*.84f,sh*.002f,GDIM);

    /* Motto */
    float mps=sw*.008f;
    drawStrC("KADERIN MUHRUNUN UCUNDA",sh*.178f,mps,GDIM);

    /* İkinci ayraç */
    Rect(sw*.08f,sh*.230f,sw*.84f,sh*.002f,GOLD);

    /* Butonlar */
    float bw=sw*.72f, bh=sh*.080f;
    float bx=(sw-bw)*.5f, gap=sh*.022f, sy=sh*.260f, bp=sw*.013f;
    const char* lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool  en[4]={true,false,true,true};

    for(int i=0;i<4;i++){
        float fy=sy+(float)i*(bh+gap);
        g_bx[i]=bx; g_by[i]=fy; g_bw[i]=bw; g_bh[i]=bh;
        drawBtn(bx,fy,bw,bh,lb[i],en[i],g_bp[i],bp);
    }

    /* Alt bilgi */
    float dps=sw*.007f;
    drawStrC("BONECASTOFFICIAL",sh*.930f,dps,GDIM);
    drawStr("V0.1",sw*.04f,sh*.950f,dps,GDIM);
}

static void renderSettings(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) Rect(0,y,sw,1.5f,sc);
    Rect(0,sh*.010f,sw,sh*.004f,GOLD);

    float ps=sw*.012f;
    drawStrC("AYARLAR",sh*.060f,ps,GOLD);
    Rect(sw*.10f,sh*.155f,sw*.80f,sh*.003f,GDIM);

    float bw=sw*.70f,bh=sh*.080f,bx=(sw-bw)*.5f,bp=sw*.012f;

    /* SES */
    char sesL[24]; snprintf(sesL,24,"SES %s",g_sndOn?"ACIK":"KAPALI");
    g_sx[0]=bx;g_sy[0]=sh*.22f;g_sw2[0]=bw;g_sh2[0]=bh;
    drawBtn(bx,sh*.22f,bw,bh,sesL,true,false,bp);

    /* FPS 60 */
    if(g_fps==60){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.20f};Rect(bx,sh*.33f,bw,bh,hl);}
    g_sx[1]=bx;g_sy[1]=sh*.33f;g_sw2[1]=bw;g_sh2[1]=bh;
    drawBtn(bx,sh*.33f,bw,bh,"FPS 60",true,false,bp);

    /* FPS 30 */
    if(g_fps==30){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.20f};Rect(bx,sh*.44f,bw,bh,hl);}
    g_sx[2]=bx;g_sy[2]=sh*.44f;g_sw2[2]=bw;g_sh2[2]=bh;
    drawBtn(bx,sh*.44f,bw,bh,"FPS 30",true,false,bp);

    /* GERİ */
    g_sx[3]=bx;g_sy[3]=sh*.62f;g_sw2[3]=bw;g_sh2[3]=bh;
    drawBtn(bx,sh*.62f,bw,bh,"GERI",true,false,bp);

    char ft[24]; snprintf(ft,24,"HEDEF %d FPS",g_fps);
    float dps=sw*.007f;
    drawStrC(ft,sh*.74f,dps,GDIM);
}

/* ════ DOKUNMA ════ */
static void onUp(float tx,float ty,android_app* aapp){
    if(g_state==ST_SPLASH){
        g_state=ST_MENU; LI("Splash skip");
        return;
    }
    if(g_state==ST_MENU){
        memset(g_bp,0,sizeof(g_bp));
        if(hitR(g_bx[0],g_by[0],g_bw[0],g_bh[0],tx,ty)){
            LI("YENİ OYUN");
            /* TODO: sinematik */
        }else if(hitR(g_bx[2],g_by[2],g_bw[2],g_bh[2],tx,ty)){
            g_state=ST_SETTINGS;
        }else if(hitR(g_bx[3],g_by[3],g_bw[3],g_bh[3],tx,ty)){
            ANativeActivity_finish(aapp->activity);
        }
        return;
    }
    if(g_state==ST_SETTINGS){
        if(hitR(g_sx[0],g_sy[0],g_sw2[0],g_sh2[0],tx,ty)) g_sndOn=!g_sndOn;
        else if(hitR(g_sx[1],g_sy[1],g_sw2[1],g_sh2[1],tx,ty)) g_fps=60;
        else if(hitR(g_sx[2],g_sy[2],g_sw2[2],g_sh2[2],tx,ty)) g_fps=30;
        else if(hitR(g_sx[3],g_sy[3],g_sw2[3],g_sh2[3],tx,ty)) g_state=ST_MENU;
        return;
    }
}

/* ════ ANDROID CALLBACKS ════ */
static void onCmd(android_app* aapp,int32_t cmd){
    switch(cmd){
    case APP_CMD_INIT_WINDOW:{
        LI("INIT_WINDOW");
        if(!aapp->window){LE("null");break;}

        g_dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(g_dpy==EGL_NO_DISPLAY){LE("noDisp");break;}
        EGLint ma=0,mi=0;
        if(!eglInitialize(g_dpy,&ma,&mi)){LE("eglInit");break;}

        const EGLint att[]={
            EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
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

        /* Test frame */
        glViewport(0,0,g_W,g_H);
        glClearColor(0.4f,0.3f,0.05f,1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(g_dpy,g_suf);

        g_prog=mkPr(VS,FS);
        if(!g_prog){LE("prog fail");break;}
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);

        memset(g_bp,0,sizeof(g_bp));
        g_state=ST_SPLASH;
        g_frame=0;
        g_ok=true;
        LI("HAZIR");
        break;
    }
    case APP_CMD_TERM_WINDOW:
        g_ok=false;
        if(g_prog){glDeleteProgram(g_prog);g_prog=0;}
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

static int32_t onInp(android_app* aapp,AInputEvent* evt){
    if(!g_ok)return 0;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION)return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float tx=AMotionEvent_getX(evt,0),ty=AMotionEvent_getY(evt,0);
    if(act==AMOTION_EVENT_ACTION_DOWN){
        if(g_state==ST_MENU)
            for(int i=0;i<4;i++) g_bp[i]=hitR(g_bx[i],g_by[i],g_bw[i],g_bh[i],tx,ty);
    }else if(act==AMOTION_EVENT_ACTION_UP){
        memset(g_bp,0,sizeof(g_bp));
        onUp(tx,ty,aapp);
    }else if(act==AMOTION_EVENT_ACTION_CANCEL){
        memset(g_bp,0,sizeof(g_bp));
    }
    return 1;
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

        /* Her frame render et — usleep yok, vsync yok */
        switch(g_state){
            case ST_SPLASH:
                renderSplash();
                g_frame++;
                if(g_frame>=SPLASH_FRAMES) g_state=ST_MENU;
                break;
            case ST_MENU:
                renderMenu();
                break;
            case ST_SETTINGS:
                renderSettings();
                break;
        }
        eglSwapBuffers(g_dpy,g_suf);
    }
    LI("END");
}

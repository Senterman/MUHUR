/**
 * MÜHÜR — main.cpp  (Tam Oyun)
 * Shader VBO + doğru koordinat sistemi
 * Splash → Ana Menü → Ayarlar
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

/* ════ SHADER ════
 * Koordinat: piksel (0,0) = sol üst, (W,H) = sağ alt
 * Y ekseni: Android dokunma koordinatıyla aynı yön
 */
static const char* VERT =
"attribute vec2 aPos;\n"
"uniform   vec2 uRes;\n"
"void main() {\n"
"    float nx =  (aPos.x / uRes.x) * 2.0 - 1.0;\n"
"    float ny = -(aPos.y / uRes.y) * 2.0 + 1.0;\n"
"    gl_Position = vec4(nx, ny, 0.0, 1.0);\n"
"}\n";

static const char* FRAG =
"precision mediump float;\n"
"uniform vec4 uColor;\n"
"void main() {\n"
"    gl_FragColor = uColor;\n"
"}\n";

/* ════ RENKLER ════ */
static const float BG[4]   = {0.051f,0.039f,0.020f,1.0f};
static const float GOLD[4] = {0.831f,0.659f,0.325f,1.0f};
static const float GDIM[4] = {0.420f,0.330f,0.160f,1.0f};
static const float DARK[4] = {0.090f,0.070f,0.035f,1.0f};
static const float PRES[4] = {0.180f,0.140f,0.070f,1.0f};
static const float GREY[4] = {0.260f,0.240f,0.220f,1.0f};

/* ════ GLOBAL ════ */
static EGLDisplay g_dpy  = EGL_NO_DISPLAY;
static EGLSurface g_suf  = EGL_NO_SURFACE;
static EGLContext g_ctx  = EGL_NO_CONTEXT;
static int32_t    g_W    = 1, g_H = 1;
static bool       g_ok   = false;
static GLuint     g_prog = 0;
static GLuint     g_vbo  = 0;
static GLint      g_aPos = -1;
static GLint      g_uRes = -1;
static GLint      g_uCol = -1;

enum State { ST_SPLASH, ST_MENU, ST_SETTINGS };
static State g_state = ST_SPLASH;
static int   g_frame = 0;
static int   g_fps   = 60;
static bool  g_sndOn = true;

static float g_bx[4],g_by[4],g_bw[4],g_bh[4];
static bool  g_bp[4]={};
static float g_sx[4],g_sy[4],g_sw2[4],g_sh2[4];

/* ════ SHADER ════ */
static GLuint mkSh(GLenum t, const char* src) {
    GLuint h = glCreateShader(t);
    glShaderSource(h,1,&src,nullptr);
    glCompileShader(h);
    GLint ok=0; glGetShaderiv(h,GL_COMPILE_STATUS,&ok);
    if(!ok){ char b[256]={};glGetShaderInfoLog(h,256,nullptr,b);LE("SH:%s",b); }
    return h;
}
static GLuint mkProg() {
    GLuint vs=mkSh(GL_VERTEX_SHADER,VERT);
    GLuint fs=mkSh(GL_FRAGMENT_SHADER,FRAG);
    GLuint p=glCreateProgram();
    glAttachShader(p,vs); glAttachShader(p,fs);
    glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok=0; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    LI("prog=%u link=%d",p,ok);
    return p;
}

/* ════ ÇİZİM ════ */
static void rect(float x,float y,float w,float h,const float* col) {
    float v[8]={x,y, x+w,y, x,y+h, x+w,y+h};
    glBindBuffer(GL_ARRAY_BUFFER,g_vbo);
    glBufferData(GL_ARRAY_BUFFER,sizeof(v),v,GL_DYNAMIC_DRAW);
    glEnableVertexAttribArray((GLuint)g_aPos);
    glVertexAttribPointer((GLuint)g_aPos,2,GL_FLOAT,GL_FALSE,0,nullptr);
    glUniform2f(g_uRes,(float)g_W,(float)g_H);
    glUniform4f(g_uCol,col[0],col[1],col[2],col[3]);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray((GLuint)g_aPos);
    glBindBuffer(GL_ARRAY_BUFFER,0);
}

/* Tarama çizgileri */
static void scanlines() {
    float c[4]={0,0,0,0.10f};
    for(float y=0;y<(float)g_H;y+=4.f) rect(0,y,(float)g_W,1.5f,c);
}

/* ════ PIXEL FONT ════ */
static float gPS=0;
static void fH(float ox,float oy,float x,float y,float w,const float*c){rect(ox+x*gPS,oy+y*gPS,w*gPS,gPS,c);}
static void fV(float ox,float oy,float x,float y,float h,const float*c){rect(ox+x*gPS,oy+y*gPS,gPS,h*gPS,c);}
static void fD(float ox,float oy,float x,float y,const float*c){rect(ox+x*gPS,oy+y*gPS,gPS,gPS,c);}

static void drawCh(char ch,float ox,float oy,const float*c){
    switch(ch){
    case 'A':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,4,c);fV(ox,oy,4,1,4,c);fH(ox,oy,1,4,3,c);fV(ox,oy,0,5,4,c);fV(ox,oy,4,5,4,c);break;
    case 'B':fH(ox,oy,0,0,4,c);fV(ox,oy,0,1,3,c);fV(ox,oy,4,1,3,c);fH(ox,oy,0,4,4,c);fV(ox,oy,0,5,3,c);fV(ox,oy,4,5,3,c);fH(ox,oy,0,8,4,c);break;
    case 'C':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,7,c);fH(ox,oy,1,8,3,c);break;
    case 'D':fH(ox,oy,0,0,3,c);fV(ox,oy,0,1,7,c);fV(ox,oy,4,1,7,c);fH(ox,oy,0,8,3,c);break;
    case 'E':fH(ox,oy,0,0,5,c);fV(ox,oy,0,1,3,c);fH(ox,oy,0,4,4,c);fV(ox,oy,0,5,3,c);fH(ox,oy,0,8,5,c);break;
    case 'F':fH(ox,oy,0,0,5,c);fV(ox,oy,0,1,3,c);fH(ox,oy,0,4,4,c);fV(ox,oy,0,5,4,c);break;
    case 'G':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,7,c);fH(ox,oy,2,4,3,c);fV(ox,oy,4,5,3,c);fH(ox,oy,1,8,3,c);break;
    case 'H':fV(ox,oy,0,0,9,c);fV(ox,oy,4,0,9,c);fH(ox,oy,1,4,3,c);break;
    case 'I':fH(ox,oy,1,0,3,c);fV(ox,oy,2,1,7,c);fH(ox,oy,1,8,3,c);break;
    case 'J':fH(ox,oy,1,0,3,c);fV(ox,oy,3,1,6,c);fV(ox,oy,0,6,2,c);fH(ox,oy,1,8,2,c);break;
    case 'K':fV(ox,oy,0,0,9,c);fH(ox,oy,1,4,3,c);fV(ox,oy,3,0,4,c);fV(ox,oy,3,5,4,c);break;
    case 'L':fV(ox,oy,0,0,8,c);fH(ox,oy,1,8,4,c);break;
    case 'M':fV(ox,oy,0,0,9,c);fV(ox,oy,4,0,9,c);fD(ox,oy,1,1,c);fD(ox,oy,2,2,c);fD(ox,oy,3,1,c);break;
    case 'N':fV(ox,oy,0,0,9,c);fV(ox,oy,4,0,9,c);fD(ox,oy,1,1,c);fD(ox,oy,2,3,c);fD(ox,oy,3,5,c);break;
    case 'O':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,7,c);fV(ox,oy,4,1,7,c);fH(ox,oy,1,8,3,c);break;
    case 'P':fH(ox,oy,0,0,4,c);fV(ox,oy,0,1,8,c);fV(ox,oy,4,1,3,c);fH(ox,oy,0,4,4,c);break;
    case 'R':fH(ox,oy,0,0,4,c);fV(ox,oy,0,1,8,c);fV(ox,oy,4,1,3,c);fH(ox,oy,0,4,4,c);fV(ox,oy,3,5,4,c);break;
    case 'S':fH(ox,oy,1,0,4,c);fV(ox,oy,0,1,3,c);fH(ox,oy,1,4,3,c);fV(ox,oy,4,5,3,c);fH(ox,oy,0,8,4,c);break;
    case 'T':fH(ox,oy,0,0,5,c);fV(ox,oy,2,1,8,c);break;
    case 'U':fV(ox,oy,0,0,8,c);fV(ox,oy,4,0,8,c);fH(ox,oy,1,8,3,c);break;
    case 'V':fV(ox,oy,0,0,6,c);fV(ox,oy,4,0,6,c);fD(ox,oy,1,6,c);fD(ox,oy,3,6,c);fD(ox,oy,2,7,c);break;
    case 'W':fV(ox,oy,0,0,9,c);fV(ox,oy,4,0,9,c);fV(ox,oy,2,5,4,c);break;
    case 'Y':fV(ox,oy,0,0,4,c);fV(ox,oy,4,0,4,c);fH(ox,oy,1,4,3,c);fV(ox,oy,2,5,4,c);break;
    case 'Z':fH(ox,oy,0,0,5,c);fD(ox,oy,3,2,c);fD(ox,oy,2,4,c);fD(ox,oy,1,6,c);fH(ox,oy,0,8,5,c);break;
    case '0':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,7,c);fV(ox,oy,4,1,7,c);fH(ox,oy,1,8,3,c);break;
    case '1':fV(ox,oy,2,0,9,c);break;
    case '2':fH(ox,oy,0,0,5,c);fV(ox,oy,4,1,3,c);fH(ox,oy,0,4,5,c);fV(ox,oy,0,5,3,c);fH(ox,oy,0,8,5,c);break;
    case '3':fH(ox,oy,0,0,5,c);fV(ox,oy,4,1,3,c);fH(ox,oy,0,4,5,c);fV(ox,oy,4,5,3,c);fH(ox,oy,0,8,5,c);break;
    case '4':fV(ox,oy,0,0,5,c);fV(ox,oy,4,0,9,c);fH(ox,oy,0,4,5,c);break;
    case '5':fH(ox,oy,0,0,5,c);fV(ox,oy,0,1,3,c);fH(ox,oy,0,4,5,c);fV(ox,oy,4,5,3,c);fH(ox,oy,0,8,5,c);break;
    case '6':fH(ox,oy,1,0,4,c);fV(ox,oy,0,1,7,c);fH(ox,oy,1,4,4,c);fV(ox,oy,4,5,3,c);fH(ox,oy,1,8,3,c);break;
    case '7':fH(ox,oy,0,0,5,c);fV(ox,oy,4,1,4,c);fV(ox,oy,2,5,4,c);break;
    case '8':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,3,c);fV(ox,oy,4,1,3,c);fH(ox,oy,1,4,3,c);fV(ox,oy,0,5,3,c);fV(ox,oy,4,5,3,c);fH(ox,oy,1,8,3,c);break;
    case '9':fH(ox,oy,1,0,3,c);fV(ox,oy,0,1,3,c);fV(ox,oy,4,1,7,c);fH(ox,oy,1,4,3,c);fH(ox,oy,1,8,3,c);break;
    case ':':fD(ox,oy,2,2,c);fD(ox,oy,2,6,c);break;
    case '.':fD(ox,oy,2,8,c);break;
    case '-':fH(ox,oy,1,4,3,c);break;
    case '/':fD(ox,oy,3,1,c);fD(ox,oy,2,3,c);fD(ox,oy,1,5,c);break;
    default: break;
    }
}

static int strn(const char* s){
    int n=0; for(const char*p=s;*p&&*p!='\n';p++) n++;
    return n;
}
static void drawStr(const char*s,float x,float y,float ps,const float*col){
    gPS=ps; float cx=x,cy=y;
    for(const unsigned char*p=(const unsigned char*)s;*p;p++){
        if(*p=='\n'){cy+=12*ps;cx=x;continue;}
        if(*p>=0x80)continue;
        char ch=(char)*p; if(ch>='a'&&ch<='z')ch-=32;
        drawCh(ch,cx,cy,col); cx+=7*ps;
    }
}
static void drawStrC(const char*s,float cy,float ps,const float*col){
    float w=(float)strn(s)*7*ps;
    drawStr(s,(float)g_W*.5f-w*.5f,cy,ps,col);
}

/* ════ BUTON ════ */
static void drawBtn(float x,float y,float w,float h,
                    const char*lbl,bool en,bool pr,float ps){
    float bw2=(float)g_W*.005f;
    const float*gc=en?GOLD:GREY;
    float bc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    rect(x-bw2,y-bw2,w+2*bw2,h+2*bw2,bc);
    rect(x,y,w,h,pr?PRES:DARK);
    float cs=(float)g_W*.009f;
    rect(x,y,cs,cs,bc); rect(x+w-cs,y,cs,cs,bc);
    rect(x,y+h-cs,cs,cs,bc); rect(x+w-cs,y+h-cs,cs,cs,bc);
    float lc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    float tw=(float)strn(lbl)*7*ps;
    drawStr(lbl,x+(w-tw)*.5f,y+(h-9*ps)*.5f,ps,lc);
}
static bool hit(float bx,float by,float bw,float bh,float tx,float ty){
    return tx>=bx&&tx<=bx+bw&&ty>=by&&ty<=by+bh;
}

/* ════ RENDER ════ */
static void rSplash(){
    float sw=(float)g_W,sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(g_prog);

    scanlines();
    rect(0,sh*.010f,sw,sh*.004f,GOLD);
    rect(0,sh*.974f,sw,sh*.004f,GOLD);

    float ps=sw*.010f;
    drawStrC("BONECASTOFFICIAL",sh*.370f,ps,GOLD);
    drawStrC("SUNAR",sh*.370f+14*ps,sw*.007f,GDIM);

    /* İlerleme çubuğu */
    float prog=fminf((float)g_frame/120.f,1.f);
    float bx=sw*.20f,by=sh*.640f,bw=sw*.60f,bh=sh*.007f;
    rect(bx,by,bw,bh,DARK);
    rect(bx,by,bw*prog,bh,GOLD);

    drawStrC("ANDROID NDK   OPENGL ES 2.0",sh*.720f,sw*.007f,GDIM);
}

static void rMenu(){
    float sw=(float)g_W,sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(g_prog);

    scanlines();
    rect(0,sh*.010f,sw,sh*.004f,GOLD);
    rect(0,sh*.974f,sw,sh*.004f,GOLD);

    /* Başlık */
    float ps=sw*.014f;
    drawStrC("MUHUR",sh*.055f,ps,GOLD);

    /* Ayraç + motto */
    rect(sw*.08f,sh*.165f,sw*.84f,sh*.002f,GDIM);
    drawStrC("KADERIN MUHRUNUN UCUNDA",sh*.178f,sw*.008f,GDIM);
    rect(sw*.08f,sh*.230f,sw*.84f,sh*.002f,GOLD);

    /* Butonlar */
    float bw=sw*.72f,bh=sh*.080f;
    float bx=(sw-bw)*.5f,gap=sh*.022f,sy=sh*.260f,bp=sw*.013f;
    const char*lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool en[4]={true,false,true,true};
    for(int i=0;i<4;i++){
        float fy=sy+(float)i*(bh+gap);
        g_bx[i]=bx;g_by[i]=fy;g_bw[i]=bw;g_bh[i]=bh;
        drawBtn(bx,fy,bw,bh,lb[i],en[i],g_bp[i],bp);
    }

    drawStrC("BONECASTOFFICIAL",sh*.930f,sw*.007f,GDIM);
    drawStr("V0.1",sw*.04f,sh*.950f,sw*.007f,GDIM);
}

static void rSettings(){
    float sw=(float)g_W,sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(g_prog);

    scanlines();
    rect(0,sh*.010f,sw,sh*.004f,GOLD);
    drawStrC("AYARLAR",sh*.060f,sw*.012f,GOLD);
    rect(sw*.10f,sh*.155f,sw*.80f,sh*.003f,GDIM);

    float bw=sw*.70f,bh=sh*.080f,bx=(sw-bw)*.5f,bp=sw*.012f;
    char sesL[24]; snprintf(sesL,24,"SES %s",g_sndOn?"ACIK":"KAPALI");
    g_sx[0]=bx;g_sy[0]=sh*.22f;g_sw2[0]=bw;g_sh2[0]=bh;
    drawBtn(bx,sh*.22f,bw,bh,sesL,true,false,bp);

    if(g_fps==60){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.20f};rect(bx,sh*.33f,bw,bh,hl);}
    g_sx[1]=bx;g_sy[1]=sh*.33f;g_sw2[1]=bw;g_sh2[1]=bh;
    drawBtn(bx,sh*.33f,bw,bh,"FPS 60",true,false,bp);

    if(g_fps==30){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.20f};rect(bx,sh*.44f,bw,bh,hl);}
    g_sx[2]=bx;g_sy[2]=sh*.44f;g_sw2[2]=bw;g_sh2[2]=bh;
    drawBtn(bx,sh*.44f,bw,bh,"FPS 30",true,false,bp);

    g_sx[3]=bx;g_sy[3]=sh*.62f;g_sw2[3]=bw;g_sh2[3]=bh;
    drawBtn(bx,sh*.62f,bw,bh,"GERI",true,false,bp);

    char ft[24]; snprintf(ft,24,"HEDEF %d FPS",g_fps);
    drawStrC(ft,sh*.74f,sw*.008f,GDIM);
}

/* ════ DOKUNMA ════ */
static void onUp(float tx,float ty,android_app*aapp){
    if(g_state==ST_SPLASH){ g_state=ST_MENU; return; }
    if(g_state==ST_MENU){
        memset(g_bp,0,sizeof(g_bp));
        if(hit(g_bx[0],g_by[0],g_bw[0],g_bh[0],tx,ty)) LI("YENİ OYUN");
        else if(hit(g_bx[2],g_by[2],g_bw[2],g_bh[2],tx,ty)) g_state=ST_SETTINGS;
        else if(hit(g_bx[3],g_by[3],g_bw[3],g_bh[3],tx,ty)) ANativeActivity_finish(aapp->activity);
        return;
    }
    if(g_state==ST_SETTINGS){
        if(hit(g_sx[0],g_sy[0],g_sw2[0],g_sh2[0],tx,ty)) g_sndOn=!g_sndOn;
        else if(hit(g_sx[1],g_sy[1],g_sw2[1],g_sh2[1],tx,ty)) g_fps=60;
        else if(hit(g_sx[2],g_sy[2],g_sw2[2],g_sh2[2],tx,ty)) g_fps=30;
        else if(hit(g_sx[3],g_sy[3],g_sw2[3],g_sh2[3],tx,ty)) g_state=ST_MENU;
        return;
    }
}

/* ════ CALLBACKS ════ */
static void onCmd(android_app*aapp,int32_t cmd){
    switch(cmd){
    case APP_CMD_INIT_WINDOW:{
        LI("INIT");
        if(!aapp->window){LE("null win");break;}
        g_dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(g_dpy==EGL_NO_DISPLAY){LE("noDisp");break;}
        EGLint ma=0,mi=0;
        if(!eglInitialize(g_dpy,&ma,&mi)){LE("init");break;}
        const EGLint att[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
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
        if(g_ctx==EGL_NO_CONTEXT){LE("ctx");break;}
        if(eglMakeCurrent(g_dpy,g_suf,g_suf,g_ctx)==EGL_FALSE){LE("cur");break;}
        eglSwapInterval(g_dpy,0);
        eglQuerySurface(g_dpy,g_suf,EGL_WIDTH,&g_W);
        eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
        LI("size %dx%d",g_W,g_H);

        g_prog=mkProg();
        if(!g_prog){LE("prog");break;}
        g_aPos=glGetAttribLocation(g_prog,"aPos");
        g_uRes=glGetUniformLocation(g_prog,"uRes");
        g_uCol=glGetUniformLocation(g_prog,"uColor");
        LI("loc aPos=%d uRes=%d uCol=%d",g_aPos,g_uRes,g_uCol);
        if(g_aPos<0||g_uRes<0||g_uCol<0){LE("loc fail");break;}

        glGenBuffers(1,&g_vbo);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);

        memset(g_bp,0,sizeof(g_bp));
        g_state=ST_SPLASH; g_frame=0;
        g_ok=true;
        LI("HAZIR %dx%d",g_W,g_H);
        break;
    }
    case APP_CMD_TERM_WINDOW:
        g_ok=false;
        if(g_vbo){glDeleteBuffers(1,&g_vbo);g_vbo=0;}
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

static int32_t onInp(android_app*aapp,AInputEvent*evt){
    if(!g_ok)return 0;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION)return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float tx=AMotionEvent_getX(evt,0),ty=AMotionEvent_getY(evt,0);
    if(act==AMOTION_EVENT_ACTION_DOWN){
        if(g_state==ST_MENU)
            for(int i=0;i<4;i++)g_bp[i]=hit(g_bx[i],g_by[i],g_bw[i],g_bh[i],tx,ty);
    }else if(act==AMOTION_EVENT_ACTION_UP){
        memset(g_bp,0,sizeof(g_bp)); onUp(tx,ty,aapp);
    }else if(act==AMOTION_EVENT_ACTION_CANCEL){
        memset(g_bp,0,sizeof(g_bp));
    }
    return 1;
}

void android_main(android_app*aapp){
    LI("START");
    aapp->userData=nullptr;
    aapp->onAppCmd=onCmd;
    aapp->onInputEvent=onInp;
    while(!aapp->destroyRequested){
        int ev=0; android_poll_source*src=nullptr;
        int timeout=g_ok?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src)src->process(aapp,src);
            if(aapp->destroyRequested)break;
        }
        if(!g_ok)continue;
        switch(g_state){
            case ST_SPLASH:
                rSplash();
                g_frame++;
                if(g_frame>=120)g_state=ST_MENU;
                break;
            case ST_MENU:     rMenu();     break;
            case ST_SETTINGS: rSettings(); break;
        }
        eglSwapBuffers(g_dpy,g_suf);
    }
    LI("END");
}

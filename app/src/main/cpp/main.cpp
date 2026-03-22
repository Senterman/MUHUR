/**
 * MÜHÜR — main.cpp
 * Papers, Please estetiği · OpenGL ES 2.0 · Android Native Activity
 * Pixel font (bitmap), dokunma yönetimi, FPS kontrolü, güvenli asset yükleme
 */

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <time.h>

#define TAG "MUHUR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════
// RENK PALETİ  (#0d0a05 siyah · #d4a853 altın)
// ═══════════════════════════════════════════════════════════
static const float C_BG[4]      = {0.051f, 0.039f, 0.020f, 1.0f};
static const float C_GOLD[4]    = {0.831f, 0.659f, 0.325f, 1.0f};
static const float C_GOLD_DIM[4]= {0.420f, 0.330f, 0.160f, 1.0f};
static const float C_DARK[4]    = {0.080f, 0.062f, 0.031f, 1.0f};
static const float C_PRESS[4]   = {0.160f, 0.124f, 0.060f, 1.0f};
static const float C_GREY[4]    = {0.220f, 0.200f, 0.180f, 1.0f};
static const float C_SCAN[4]    = {0.000f, 0.000f, 0.000f, 0.18f};

// ═══════════════════════════════════════════════════════════
// PIXEL BİTMAP FONT  (5×7, ASCII 32–90)
// Her karakter 5 sütun × 7 satır, bit=1 → piksel dolu
// ═══════════════════════════════════════════════════════════
static const uint8_t FONT5x7[][7] = {
/* ' '*/  {0x00,0x00,0x00,0x00,0x00,0x00,0x00},
/* '!'*/  {0x04,0x04,0x04,0x04,0x00,0x04,0x00},
/* '"'*/  {0x0A,0x0A,0x00,0x00,0x00,0x00,0x00},
/* '#'*/  {0x0A,0x1F,0x0A,0x0A,0x1F,0x0A,0x00},
/* '$'*/  {0x04,0x0F,0x14,0x0E,0x05,0x1E,0x04},
/* '%'*/  {0x18,0x19,0x02,0x04,0x13,0x03,0x00},
/* '&'*/  {0x0C,0x12,0x14,0x08,0x15,0x12,0x0D},
/* '\''*/ {0x04,0x04,0x00,0x00,0x00,0x00,0x00},
/* '('*/  {0x02,0x04,0x08,0x08,0x08,0x04,0x02},
/* ')'*/  {0x08,0x04,0x02,0x02,0x02,0x04,0x08},
/* '*'*/  {0x00,0x04,0x15,0x0E,0x15,0x04,0x00},
/* '+'*/  {0x00,0x04,0x04,0x1F,0x04,0x04,0x00},
/* ','*/  {0x00,0x00,0x00,0x00,0x06,0x04,0x08},
/* '-'*/  {0x00,0x00,0x00,0x1F,0x00,0x00,0x00},
/* '.'*/  {0x00,0x00,0x00,0x00,0x00,0x06,0x06},
/* '/'*/  {0x01,0x01,0x02,0x04,0x08,0x10,0x10},
/* '0'*/  {0x0E,0x11,0x13,0x15,0x19,0x11,0x0E},
/* '1'*/  {0x04,0x0C,0x04,0x04,0x04,0x04,0x0E},
/* '2'*/  {0x0E,0x11,0x01,0x02,0x04,0x08,0x1F},
/* '3'*/  {0x1F,0x02,0x04,0x02,0x01,0x11,0x0E},
/* '4'*/  {0x02,0x06,0x0A,0x12,0x1F,0x02,0x02},
/* '5'*/  {0x1F,0x10,0x1E,0x01,0x01,0x11,0x0E},
/* '6'*/  {0x06,0x08,0x10,0x1E,0x11,0x11,0x0E},
/* '7'*/  {0x1F,0x01,0x02,0x04,0x08,0x08,0x08},
/* '8'*/  {0x0E,0x11,0x11,0x0E,0x11,0x11,0x0E},
/* '9'*/  {0x0E,0x11,0x11,0x0F,0x01,0x02,0x0C},
/* ':'*/  {0x00,0x06,0x06,0x00,0x06,0x06,0x00},
/* ';'*/  {0x00,0x06,0x06,0x00,0x06,0x04,0x08},
/* '<'*/  {0x02,0x04,0x08,0x10,0x08,0x04,0x02},
/* '='*/  {0x00,0x00,0x1F,0x00,0x1F,0x00,0x00},
/* '>'*/  {0x08,0x04,0x02,0x01,0x02,0x04,0x08},
/* '?'*/  {0x0E,0x11,0x01,0x02,0x04,0x00,0x04},
/* '@'*/  {0x0E,0x11,0x01,0x0D,0x15,0x15,0x0E},
/* 'A'*/  {0x0E,0x11,0x11,0x1F,0x11,0x11,0x11},
/* 'B'*/  {0x1E,0x11,0x11,0x1E,0x11,0x11,0x1E},
/* 'C'*/  {0x0E,0x11,0x10,0x10,0x10,0x11,0x0E},
/* 'D'*/  {0x1C,0x12,0x11,0x11,0x11,0x12,0x1C},
/* 'E'*/  {0x1F,0x10,0x10,0x1E,0x10,0x10,0x1F},
/* 'F'*/  {0x1F,0x10,0x10,0x1E,0x10,0x10,0x10},
/* 'G'*/  {0x0E,0x11,0x10,0x17,0x11,0x11,0x0F},
/* 'H'*/  {0x11,0x11,0x11,0x1F,0x11,0x11,0x11},
/* 'I'*/  {0x0E,0x04,0x04,0x04,0x04,0x04,0x0E},
/* 'J'*/  {0x07,0x02,0x02,0x02,0x02,0x12,0x0C},
/* 'K'*/  {0x11,0x12,0x14,0x18,0x14,0x12,0x11},
/* 'L'*/  {0x10,0x10,0x10,0x10,0x10,0x10,0x1F},
/* 'M'*/  {0x11,0x1B,0x15,0x15,0x11,0x11,0x11},
/* 'N'*/  {0x11,0x19,0x19,0x15,0x13,0x13,0x11},
/* 'O'*/  {0x0E,0x11,0x11,0x11,0x11,0x11,0x0E},
/* 'P'*/  {0x1E,0x11,0x11,0x1E,0x10,0x10,0x10},
/* 'Q'*/  {0x0E,0x11,0x11,0x11,0x15,0x12,0x0D},
/* 'R'*/  {0x1E,0x11,0x11,0x1E,0x14,0x12,0x11},
/* 'S'*/  {0x0F,0x10,0x10,0x0E,0x01,0x01,0x1E},
/* 'T'*/  {0x1F,0x04,0x04,0x04,0x04,0x04,0x04},
/* 'U'*/  {0x11,0x11,0x11,0x11,0x11,0x11,0x0E},
/* 'V'*/  {0x11,0x11,0x11,0x11,0x11,0x0A,0x04},
/* 'W'*/  {0x11,0x11,0x11,0x15,0x15,0x15,0x0A},
/* 'X'*/  {0x11,0x11,0x0A,0x04,0x0A,0x11,0x11},
/* 'Y'*/  {0x11,0x11,0x0A,0x04,0x04,0x04,0x04},
/* 'Z'*/  {0x1F,0x01,0x02,0x04,0x08,0x10,0x1F},
};

// Türkçe karakter yerine ASCII fallback tablosu
static char toAscii(char c){
    switch((uint8_t)c){
        case 0xC4: return 'I'; // İ (UTF-8 iki bayt, basit fallback)
        case 0xC5: return 'S'; // Ş
        case 0xC3: return 'U'; // Ü/Ö/Ç — sonraki bayta bakılmadan
        default:   return c;
    }
}

// ═══════════════════════════════════════════════════════════
// SHADER
// ═══════════════════════════════════════════════════════════
static const char* VS = R"(
    attribute vec2 aP;
    uniform   vec2 uR;
    void main(){
        vec2 n=(aP/uR)*2.0-1.0;
        gl_Position=vec4(n.x,-n.y,0,1);
    }
)";
static const char* FS = R"(
    precision mediump float;
    uniform vec4 uC;
    void main(){ gl_FragColor=uC; }
)";
static const char* VS_T = R"(
    attribute vec2 aP;
    attribute vec2 aU;
    uniform   vec2 uR;
    varying   vec2 vU;
    void main(){
        vec2 n=(aP/uR)*2.0-1.0;
        gl_Position=vec4(n.x,-n.y,0,1);
        vU=aU;
    }
)";
static const char* FS_T = R"(
    precision mediump float;
    uniform sampler2D uT;
    uniform float uA;
    varying vec2 vU;
    void main(){
        vec4 c=texture2D(uT,vU);
        gl_FragColor=vec4(c.rgb,c.a*uA);
    }
)";

static GLuint mkShader(GLenum t,const char* s){
    GLuint h=glCreateShader(t);
    glShaderSource(h,1,&s,nullptr); glCompileShader(h);
    GLint ok; glGetShaderiv(h,GL_COMPILE_STATUS,&ok);
    if(!ok){char b[256];glGetShaderInfoLog(h,256,nullptr,b);LOGE("SH:%s",b);}
    return h;
}
static GLuint mkProg(const char* v,const char* f){
    GLuint p=glCreateProgram();
    GLuint vs=mkShader(GL_VERTEX_SHADER,v);
    GLuint fs=mkShader(GL_FRAGMENT_SHADER,f);
    glAttachShader(p,vs); glAttachShader(p,fs);
    glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    return p;
}

// ═══════════════════════════════════════════════════════════
// PNG DECODE  (sadece stored-block, crash-safe)
// ═══════════════════════════════════════════════════════════
static inline uint32_t u32be(const uint8_t* p){
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|((uint32_t)p[2]<<8)|p[3];
}
static uint8_t paeth_p(uint8_t a,uint8_t b,uint8_t c){
    int pa=abs((int)b-c),pb=abs((int)a-c),pc=abs((int)a+(int)b-2*(int)c);
    return (pa<=pb&&pa<=pc)?a:(pb<=pc)?b:c;
}
static bool zInflate(const uint8_t* s,int sn,uint8_t* d,int dn){
    if(sn<2)return false;
    int p=2,o=0;
    while(p<sn-4&&o<dn){
        bool fin=(s[p]&1)!=0; int bt=(s[p]>>1)&3; p++;
        if(bt==0){
            if(p&1)p++;
            if(p+4>sn)return false;
            uint16_t len=(uint16_t)s[p]|(uint16_t)s[p+1]<<8; p+=4;
            if(p+len>sn||o+len>dn)return false;
            memcpy(d+o,s+p,len); o+=len; p+=len;
        } else { LOGE("PNG deflate: stb_image gerekli"); return false; }
        if(fin)break;
    }
    return o==dn;
}
static bool decodePNG(const uint8_t* data,int sz,uint8_t** px,int* W,int* H){
    static const uint8_t SIG[8]={137,80,78,71,13,10,26,10};
    if(sz<8||memcmp(data,SIG,8))return false;
    int pos=8,w=0,h=0,ch=0;
    uint8_t* idat=nullptr; int idl=0,idc=0; bool end=false;
    while(pos+12<=sz&&!end){
        int cl=(int)u32be(data+pos); pos+=4;
        char tp[5]; memcpy(tp,data+pos,4); tp[4]=0; pos+=4;
        if(!strcmp(tp,"IHDR")){
            w=(int)u32be(data+pos); h=(int)u32be(data+pos+4);
            int bd=data[pos+8],ct=data[pos+9];
            ch=(ct==6)?4:(ct==2)?3:0;
            if(bd!=8||ch==0){LOGE("PNG: RGB/RGBA 8bit gerekli");free(idat);return false;}
        } else if(!strcmp(tp,"IDAT")){
            if(idl+cl>idc){idc=idl+cl+65536;idat=(uint8_t*)realloc(idat,idc);}
            memcpy(idat+idl,data+pos,cl); idl+=cl;
        } else if(!strcmp(tp,"IEND")) end=true;
        pos+=cl+4;
    }
    if(!end||!idat||w==0){free(idat);return false;}
    int stride=w*ch, rawsz=h*(stride+1);
    uint8_t* raw=(uint8_t*)malloc(rawsz);
    if(!zInflate(idat,idl,raw,rawsz)){free(idat);free(raw);return false;}
    free(idat);
    uint8_t* out=(uint8_t*)malloc(w*h*4);
    uint8_t* prev=(uint8_t*)calloc(stride,1);
    for(int y=0;y<h;y++){
        uint8_t* row=raw+y*(stride+1); uint8_t ft=row[0],*cur=row+1;
        for(int x=0;x<stride;x++){
            uint8_t a=(x>=ch)?cur[x-ch]:0,b=prev[x],c=(x>=ch)?prev[x-ch]:0;
            switch(ft){case 1:cur[x]+=a;break;case 2:cur[x]+=b;break;
                        case 3:cur[x]+=(uint8_t)((a+b)/2);break;
                        case 4:cur[x]+=paeth_p(a,b,c);break;}
        }
        memcpy(prev,cur,stride);
        uint8_t* o2=out+y*w*4;
        for(int x=0;x<w;x++){
            o2[x*4]=cur[x*ch]; o2[x*4+1]=cur[x*ch+1]; o2[x*4+2]=cur[x*ch+2];
            o2[x*4+3]=(ch==4)?cur[x*ch+3]:255;
        }
    }
    free(raw); free(prev);
    *px=out; *W=w; *H=h; return true;
}
static GLuint loadTex(AAssetManager* am,const char* path,int* tw,int* th){
    if(!am)return 0;
    AAsset* a=AAssetManager_open(am,path,AASSET_MODE_BUFFER);
    if(!a){LOGE("Asset yok: %s",path);return 0;}
    const uint8_t* buf=(const uint8_t*)AAsset_getBuffer(a);
    int sz=(int)AAsset_getLength(a);
    uint8_t* px=nullptr; int w=0,h=0;
    bool ok=decodePNG(buf,sz,&px,&w,&h);
    AAsset_close(a);
    if(!ok)return 0;
    GLuint tex; glGenTextures(1,&tex);
    glBindTexture(GL_TEXTURE_2D,tex);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); // piksel netliği
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,px);
    free(px); if(tw)*tw=w; if(th)*th=h;
    LOGI("Tex: %s %dx%d",path,w,h);
    return tex;
}

// ═══════════════════════════════════════════════════════════
// TEMEL ÇİZİM
// ═══════════════════════════════════════════════════════════
struct R2 { float sw,sh; GLuint cp,tp; };

static void rect(R2& r,float x,float y,float w,float h,const float* c){
    float v[]={x,y,x+w,y,x,y+h,x+w,y+h};
    glUseProgram(r.cp);
    GLint ap=glGetAttribLocation(r.cp,"aP");
    glUniform2f(glGetUniformLocation(r.cp,"uR"),r.sw,r.sh);
    glUniform4fv(glGetUniformLocation(r.cp,"uC"),1,c);
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
}
static void rectBorder(R2& r,float x,float y,float w,float h,
                        const float* fill,const float* border,float bw){
    rect(r,x-bw,y-bw,w+2*bw,h+2*bw,border);
    rect(r,x,y,w,h,fill);
}
static void tex(R2& r,GLuint t,float x,float y,float w,float h,float a=1.0f){
    if(!t)return;
    float v[]={x,y,0,0, x+w,y,1,0, x,y+h,0,1, x+w,y+h,1,1};
    glUseProgram(r.tp);
    GLint ap=glGetAttribLocation(r.tp,"aP"),au=glGetAttribLocation(r.tp,"aU");
    glUniform2f(glGetUniformLocation(r.tp,"uR"),r.sw,r.sh);
    glUniform1i(glGetUniformLocation(r.tp,"uT"),0);
    glUniform1f(glGetUniformLocation(r.tp,"uA"),a);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,t);
    glEnableVertexAttribArray(ap); glEnableVertexAttribArray(au);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)0);
    glVertexAttribPointer(au,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap); glDisableVertexAttribArray(au);
}

// ═══════════════════════════════════════════════════════════
// PIXEL FONT ÇİZİMİ
// ═══════════════════════════════════════════════════════════
static void drawChar(R2& r,char ch,float x,float y,float ps,const float* col){
    int idx=(int)ch-32;
    if(idx<0||idx>=(int)(sizeof(FONT5x7)/7))idx=0;
    for(int row=0;row<7;row++){
        uint8_t bits=FONT5x7[idx][row];
        for(int col2=0;col2<5;col2++){
            if(bits&(0x10>>col2))
                rect(r,x+col2*ps,y+row*ps,ps,ps,col);
        }
    }
}
// ASCII'ye dönüştürerek yaz (UTF-8 Türkçe toleranslı)
static void drawText(R2& r,const char* txt,float x,float y,
                     float ps,const float* col){
    float cx=x;
    const uint8_t* p=(const uint8_t*)txt;
    while(*p){
        if(*p>=0x80){ p++; if(*p&&(*p&0xC0)==0x80)p++; cx+=6*ps; continue; }
        drawChar(r,(char)*p,cx,y,ps,col);
        cx+=6*ps; p++;
    }
}
static float textWidth(const char* t,float ps){
    int n=0; const uint8_t* p=(const uint8_t*)t;
    while(*p){ if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++;}else p++; n++; }
    return n*6*ps;
}
static void drawTextCenter(R2& r,const char* t,float cx,float y,
                            float ps,const float* col){
    drawText(r,t,cx-textWidth(t,ps)*0.5f,y,ps,col);
}

// ═══════════════════════════════════════════════════════════
// EKRAN / DURUM
// ═══════════════════════════════════════════════════════════
enum Screen { S_MENU, S_SETTINGS, S_CREDITS, S_BOOTING };

struct Btn { float x,y,w,h; bool en,pr; const char* lbl; };
static bool hit(const Btn& b,float tx,float ty){
    return b.en&&tx>=b.x&&tx<=b.x+b.w&&ty>=b.y&&ty<=b.y+b.h;
}

// ═══════════════════════════════════════════════════════════
// UYGULAMA
// ═══════════════════════════════════════════════════════════
struct App {
    // EGL
    EGLDisplay dpy=EGL_NO_DISPLAY;
    EGLSurface suf=EGL_NO_SURFACE;
    EGLContext ctx=EGL_NO_CONTEXT;
    int32_t W=0,H=0; bool ready=false;

    // GL
    GLuint cp=0,tp=0;          // colour/texture program
    GLuint texBG=0,texLogo=0;
    int bgW=0,bgH=0,lgW=0,lgH=0;

    // Asset
    AAssetManager* am=nullptr;

    // Oyun
    Screen scr=S_MENU;
    bool hasSave=false;
    bool soundOn=true;
    int  fps=60;               // 30 veya 60

    // Booting animasyon
    int  bootFrame=0;
    long bootStart=0;

    // Scan-line zamanlama
    long lastFrame=0;

    // Butonlar (her frame yeniden hesaplanır)
    Btn mb[4];   // Menü butonları
    Btn sb[4];   // Ayarlar
    Btn cb[1];   // Credits geri
};

// ═══════════════════════════════════════════════════════════
// EGL
// ═══════════════════════════════════════════════════════════
static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}
static bool eglInit(App& a,ANativeWindow* w){
    a.dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(a.dpy,nullptr,nullptr);
    const EGLint att[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
    EGLConfig cfg; EGLint n;
    eglChooseConfig(a.dpy,att,&cfg,1,&n);
    a.suf=eglCreateWindowSurface(a.dpy,cfg,w,nullptr);
    const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
    a.ctx=eglCreateContext(a.dpy,cfg,EGL_NO_CONTEXT,ca);
    if(eglMakeCurrent(a.dpy,a.suf,a.suf,a.ctx)==EGL_FALSE){LOGE("EGL fail");return false;}
    eglQuerySurface(a.dpy,a.suf,EGL_WIDTH,&a.W);
    eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
    LOGI("EGL %dx%d",a.W,a.H); return true;
}
static void eglTerm(App& a){
    if(a.dpy!=EGL_NO_DISPLAY){
        eglMakeCurrent(a.dpy,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
        if(a.ctx!=EGL_NO_CONTEXT)eglDestroyContext(a.dpy,a.ctx);
        if(a.suf!=EGL_NO_SURFACE)eglDestroySurface(a.dpy,a.suf);
        eglTerminate(a.dpy);
    }
    a.dpy=EGL_NO_DISPLAY; a.ctx=EGL_NO_CONTEXT; a.suf=EGL_NO_SURFACE;
}

// ═══════════════════════════════════════════════════════════
// RENDER YARDIMCISI — BUTON
// ═══════════════════════════════════════════════════════════
static void drawBtn(R2& r,const Btn& b,float ps){
    const float* fill = b.pr  ? C_PRESS  : C_DARK;
    const float* bord = b.en  ? C_GOLD   : C_GREY;
    float ba = b.en ? 1.0f : 0.35f;

    float bc[4]={bord[0],bord[1],bord[2],ba};
    float fc[4]={fill[0],fill[1],fill[2],0.92f};

    // Piksel çerçeve (2px solid)
    rectBorder(r,b.x,b.y,b.w,b.h,fc,bc,r.sw*0.004f);

    // İç köşe noktaları (Papers Please stili)
    float cs=r.sw*0.008f;
    rect(r,b.x,b.y,cs,cs,bc);
    rect(r,b.x+b.w-cs,b.y,cs,cs,bc);
    rect(r,b.x,b.y+b.h-cs,cs,cs,bc);
    rect(r,b.x+b.w-cs,b.y+b.h-cs,cs,cs,bc);

    // Etiket
    const float* tc = b.en ? C_GOLD : C_GREY;
    float lc[4]={tc[0],tc[1],tc[2],b.en?1.0f:0.5f};
    float ty=b.y+(b.h-7*ps)*0.5f;
    float tx=b.x+(b.w-textWidth(b.lbl,ps))*0.5f;
    drawText(r,b.lbl,tx,ty,ps,lc);
}

// ═══════════════════════════════════════════════════════════
// RENDER — ANA MENÜ
// ═══════════════════════════════════════════════════════════
static void renderMenu(App& a){
    R2 r={float(a.W),float(a.H),a.cp,a.tp};
    float sw=r.sw,sh=r.sh;

    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1); glClear(GL_COLOR_BUFFER_BIT);

    // Arka plan
    if(a.texBG) tex(r,a.texBG,0,0,sw,sh,0.85f);
    rect(r,0,0,sw,sh,C_BG); // overlay

    // Pixel tarama çizgileri (Papers Please CRT efekti)
    float scanA[4]={0,0,0,0.12f};
    for(float y=0;y<sh;y+=4) rect(r,0,y,sw,1.5f,scanA);

    // Logo
    if(a.texLogo&&a.lgW>0){
        float ar=(float)a.lgW/a.lgH;
        float lw=sw*0.55f,lh=lw/ar;
        if(lh>sh*0.20f){lh=sh*0.20f;lw=lh*ar;}
        tex(r,a.texLogo,(sw-lw)*0.5f,sh*0.04f,lw,lh);
    } else {
        // Fallback: "MUHUR" büyük piksel yazı
        float ps=sw*0.018f;
        float tx=(sw-textWidth("MUHUR",ps))*0.5f;
        drawText(r,"MUHUR",tx,sh*0.06f,ps,C_GOLD);
    }

    // Motto
    float ps=sw*0.028f;  // buton font boyutu
    float mps=sw*0.015f; // motto font boyutu
    drawTextCenter(r,"KADERIN, MUHRUNUN UCUNDA.",sw*0.5f,sh*0.27f,mps,C_GOLD_DIM);

    // Ayraç çizgisi
    float lineY=sh*0.32f, lineH=sh*0.003f;
    rect(r,sw*0.08f,lineY,sw*0.84f,lineH,C_GOLD_DIM);
    // Çizgi ortası altın kare
    float sq=sw*0.015f;
    rect(r,sw*0.5f-sq*0.5f,lineY-sq*0.3f,sq,sq,C_GOLD);

    // ── BUTONLAR ──────────────────────────────────────────
    float bw=sw*0.70f, bh=sh*0.072f, bx=(sw-bw)*0.5f;
    float gap=sh*0.020f, sy=sh*0.36f;
    const char* lbl[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool en[4]={true,a.hasSave,true,true};

    for(int i=0;i<4;i++){
        a.mb[i]={bx,sy+i*(bh+gap),bw,bh,en[i],a.mb[i].pr,lbl[i]};
        drawBtn(r,a.mb[i],ps);
    }

    // Geliştirici notu
    float dps=sw*0.011f;
    drawTextCenter(r,"BONECASTOFFICIAL",sw*0.5f,sh*0.94f,dps,C_GOLD_DIM);

    // Sürüm
    drawText(r,"V0.1",sw*0.04f,sh*0.95f,dps,C_GOLD_DIM);

    eglSwapBuffers(a.dpy,a.suf);
}

// ═══════════════════════════════════════════════════════════
// RENDER — BOOT / SİSTEM BAŞLATILIYOR
// ═══════════════════════════════════════════════════════════
static void renderBoot(App& a){
    R2 r={float(a.W),float(a.H),a.cp,a.tp};
    float sw=r.sw,sh=r.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);

    float ps=sw*0.022f;
    long elapsed=nowMs()-a.bootStart;
    int  dots=(int)(elapsed/400)%4;

    char line[64]; line[0]=0;
    strcat(line,"SİSTEM BAŞLATILIYOR");
    for(int i=0;i<dots;i++) strcat(line,".");

    float tx=(sw-textWidth(line,ps))*0.5f;
    drawText(r,line,tx,sh*0.44f,ps,C_GOLD);

    // İlerleme çubuğu
    float bx=sw*0.15f,by=sh*0.55f,bw=sw*0.70f,bh=sh*0.018f;
    float prog=fminf((float)elapsed/2000.0f,1.0f);
    rect(r,bx,by,bw,bh,C_DARK);
    rect(r,bx,by,bw*prog,bh,C_GOLD);
    rect(r,bx,by,bw,sh*0.003f,C_GOLD_DIM); // üst şerit

    float dps=sw*0.013f;
    drawTextCenter(r,"BONECASTOFFICIAL",sw*0.5f,sh*0.65f,dps,C_GOLD_DIM);

    eglSwapBuffers(a.dpy,a.suf);

    if(elapsed>2200){ a.scr=S_MENU; } // 2.2 sn sonra menüye dön
}

// ═══════════════════════════════════════════════════════════
// RENDER — AYARLAR
// ═══════════════════════════════════════════════════════════
static void renderSettings(App& a){
    R2 r={float(a.W),float(a.H),a.cp,a.tp};
    float sw=r.sw,sh=r.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    if(a.texBG) tex(r,a.texBG,0,0,sw,sh,0.4f);

    float scanA[4]={0,0,0,0.12f};
    for(float y=0;y<sh;y+=4) rect(r,0,y,sw,1.5f,scanA);

    float ps=sw*0.025f;
    drawTextCenter(r,"AYARLAR",sw*0.5f,sh*0.06f,ps,C_GOLD);
    float lh=sh*0.004f;
    rect(r,sw*0.10f,sh*0.14f,sw*0.80f,lh,C_GOLD_DIM);

    float bw=sw*0.68f,bh=sh*0.075f,bx=(sw-bw)*0.5f;
    float bp=sw*0.024f;

    // --- Ses ---
    char sesLbl[32]; snprintf(sesLbl,32,"SES: %s",a.soundOn?"ACIK":"KAPALI");
    a.sb[0]={bx,sh*0.22f,bw,bh,true,a.sb[0].pr,sesLbl};
    drawBtn(r,a.sb[0],bp);

    // --- FPS 30 ---
    const char* f30lbl="FPS: 30";
    a.sb[1]={bx,sh*0.33f,bw,bh,true,a.sb[1].pr,f30lbl};
    // aktif seçim vurgusu
    if(a.fps==30){
        float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.15f};
        rect(r,bx,sh*0.33f,bw,bh,hl);
    }
    drawBtn(r,a.sb[1],bp);

    // --- FPS 60 ---
    const char* f60lbl="FPS: 60";
    a.sb[2]={bx,sh*0.44f,bw,bh,true,a.sb[2].pr,f60lbl};
    if(a.fps==60){
        float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],0.15f};
        rect(r,bx,sh*0.44f,bw,bh,hl);
    }
    drawBtn(r,a.sb[2],bp);

    // --- Geri ---
    a.sb[3]={bx,sh*0.62f,bw,bh,true,a.sb[3].pr,"GERİ"};
    drawBtn(r,a.sb[3],bp);

    // Aktif fps göstergesi
    char fpstxt[32]; snprintf(fpstxt,32,"HEDEF: %d FPS",a.fps);
    float dps=sw*0.013f;
    drawTextCenter(r,fpstxt,sw*0.5f,sh*0.74f,dps,C_GOLD_DIM);

    eglSwapBuffers(a.dpy,a.suf);
}

// ═══════════════════════════════════════════════════════════
// RENDER — GELİŞTİRİCİLER
// ═══════════════════════════════════════════════════════════
static void renderCredits(App& a){
    R2 r={float(a.W),float(a.H),a.cp,a.tp};
    float sw=r.sw,sh=r.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    if(a.texBG) tex(r,a.texBG,0,0,sw,sh,0.3f);

    float ps=sw*0.025f;
    drawTextCenter(r,"GELİSTİRİCİLER",sw*0.5f,sh*0.06f,ps,C_GOLD);
    float lh=sh*0.004f;
    rect(r,sw*0.10f,sh*0.14f,sw*0.80f,lh,C_GOLD_DIM);

    float dp=sw*0.020f;
    drawTextCenter(r,"BONECASTOFFICIAL",sw*0.5f,sh*0.35f,dp,C_GOLD);

    float sp=sw*0.013f;
    drawTextCenter(r,"OYUN TASARIMI & GELISTIRME",sw*0.5f,sh*0.46f,sp,C_GOLD_DIM);
    drawTextCenter(r,"MUHUR - 2025",sw*0.5f,sh*0.52f,sp,C_GOLD_DIM);

    // Geri
    float bw=sw*0.55f,bh=sh*0.075f,bx=(sw-bw)*0.5f;
    a.cb[0]={bx,sh*0.70f,bw,bh,true,a.cb[0].pr,"GERİ"};
    drawBtn(r,a.cb[0],sw*0.022f);

    eglSwapBuffers(a.dpy,a.suf);
}

// ═══════════════════════════════════════════════════════════
// DOKUNMA
// ═══════════════════════════════════════════════════════════
static void onTouch(App& a,float tx,float ty,android_app* aapp){
    if(a.scr==S_MENU){
        if(hit(a.mb[0],tx,ty)){ a.scr=S_BOOTING; a.bootStart=nowMs(); a.bootFrame=0; }
        else if(hit(a.mb[1],tx,ty)&&a.hasSave) LOGI("DEVAM ET");
        else if(hit(a.mb[2],tx,ty)) a.scr=S_SETTINGS;
        else if(hit(a.mb[3],tx,ty)) ANativeActivity_finish(aapp->activity);
    } else if(a.scr==S_SETTINGS){
        if(hit(a.sb[0],tx,ty))      a.soundOn=!a.soundOn;
        else if(hit(a.sb[1],tx,ty)) a.fps=30;
        else if(hit(a.sb[2],tx,ty)) a.fps=60;
        else if(hit(a.sb[3],tx,ty)) a.scr=S_MENU;
    } else if(a.scr==S_CREDITS){
        if(hit(a.cb[0],tx,ty)) a.scr=S_MENU;
    } else if(a.scr==S_BOOTING){
        // boot animasyonu atla
        a.scr=S_MENU;
    }
}

// ═══════════════════════════════════════════════════════════
// ANDROID CALLBACKS
// ═══════════════════════════════════════════════════════════
static void onCmd(android_app* aapp,int32_t cmd){
    App& a=*(App*)aapp->userData;
    switch(cmd){
        case APP_CMD_INIT_WINDOW:
            if(!aapp->window)break;
            eglInit(a,aapp->window);
            a.cp=mkProg(VS,FS); a.tp=mkProg(VS_T,FS_T);
            a.am=aapp->activity->assetManager;
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            a.texBG  =loadTex(a.am,"menu_bg.png",&a.bgW,&a.bgH);
            a.texLogo=loadTex(a.am,"logo.png",   &a.lgW,&a.lgH);
            memset(a.mb,0,sizeof(a.mb));
            memset(a.sb,0,sizeof(a.sb));
            memset(a.cb,0,sizeof(a.cb));
            a.ready=true;
            a.lastFrame=nowMs();
            LOGI("GL hazir");
            break;
        case APP_CMD_TERM_WINDOW:
            a.ready=false;
            if(a.texBG)   glDeleteTextures(1,&a.texBG);
            if(a.texLogo) glDeleteTextures(1,&a.texLogo);
            if(a.cp) glDeleteProgram(a.cp);
            if(a.tp) glDeleteProgram(a.tp);
            a.texBG=a.texLogo=a.cp=a.tp=0;
            eglTerm(a);
            break;
        case APP_CMD_WINDOW_RESIZED:
        case APP_CMD_CONFIG_CHANGED:
            eglQuerySurface(a.dpy,a.suf,EGL_WIDTH,&a.W);
            eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
            break;
        default: break;
    }
}
static int32_t onInput(android_app* aapp,AInputEvent* evt){
    App& a=*(App*)aapp->userData;
    if(AInputEvent_getType(evt)==AINPUT_EVENT_TYPE_MOTION){
        int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
        float tx=AMotionEvent_getX(evt,0),ty=AMotionEvent_getY(evt,0);
        if(act==AMOTION_EVENT_ACTION_DOWN){
            for(auto& b:a.mb) if(hit(b,tx,ty))b.pr=true;
            for(auto& b:a.sb) if(hit(b,tx,ty))b.pr=true;
            for(auto& b:a.cb) if(hit(b,tx,ty))b.pr=true;
        } else if(act==AMOTION_EVENT_ACTION_UP){
            for(auto& b:a.mb) b.pr=false;
            for(auto& b:a.sb) b.pr=false;
            for(auto& b:a.cb) b.pr=false;
            onTouch(a,tx,ty,aapp);
        } else if(act==AMOTION_EVENT_ACTION_CANCEL){
            for(auto& b:a.mb) b.pr=false;
            for(auto& b:a.sb) b.pr=false;
            for(auto& b:a.cb) b.pr=false;
        }
        return 1;
    }
    return 0;
}

// ═══════════════════════════════════════════════════════════
// GİRİŞ NOKTASI
// ═══════════════════════════════════════════════════════════
void android_main(android_app* aapp){
    App a;
    aapp->userData    =&a;
    aapp->onAppCmd    =onCmd;
    aapp->onInputEvent=onInput;
    LOGI("MUHUR -- 'Kaderin, muhrunun ucunda.'");

    while(!aapp->destroyRequested){
        int ev; android_poll_source* src;
        int timeout=a.ready?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src)src->process(aapp,src);
            if(aapp->destroyRequested)break;
        }
        if(!a.ready)continue;

        // FPS kısıtlama
        long now=nowMs();
        long frameMs=(a.fps==30)?33L:16L;
        if(now-a.lastFrame<frameMs){ usleep(1000); continue; }
        a.lastFrame=now;

        switch(a.scr){
            case S_MENU:     renderMenu(a);     break;
            case S_SETTINGS: renderSettings(a); break;
            case S_CREDITS:  renderCredits(a);  break;
            case S_BOOTING:  renderBoot(a);     break;
        }
    }
    LOGI("MUHUR kapaniyor");
}

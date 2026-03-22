/**
 * MÜHÜR — main.cpp  (Sıfır Çökme / Safe-Start Sürümü)
 *
 * AŞAMA 1 — Sadece düz renk (glClearColor). Asset yok, shader yok.
 *           Ekran açılırsa EGL çalışıyor demektir.
 * AŞAMA 2 — Shader + dikdörtgen çizimi. Texture yok.
 *           Butonlar çizilirse GLES2 çalışıyor demektir.
 * AŞAMA 3 — Asset yükleme. Hata olursa fallback, asla crash yok.
 *
 * App struct'ı HEAP'te tutulur (stack overflow önlemi).
 */

/* ── NDK / sistem başlıkları ── */
#include <android_native_app_glue.h>
#include <android/asset_manager.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

/* ── Standart C ── */
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cmath>
#include <unistd.h>
#include <time.h>

/* ────────────────────────────────────────────────────────
 * LOGCAT
 * ──────────────────────────────────────────────────────── */
#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ────────────────────────────────────────────────────────
 * RENKLER
 * ──────────────────────────────────────────────────────── */
static const float C_BG[4]   = {0.051f,0.039f,0.020f,1.0f};
static const float C_GOLD[4] = {0.831f,0.659f,0.325f,1.0f};
static const float C_GDIM[4] = {0.420f,0.330f,0.160f,1.0f};
static const float C_DARK[4] = {0.090f,0.070f,0.035f,1.0f};
static const float C_PRES[4] = {0.170f,0.130f,0.065f,1.0f};
static const float C_GREY[4] = {0.240f,0.220f,0.200f,1.0f};

/* ────────────────────────────────────────────────────────
 * PIXEL FONT 5×7  (ASCII 32–90)
 * ──────────────────────────────────────────────────────── */
static const uint8_t FONT[59][7] = {
{0x00,0x00,0x00,0x00,0x00,0x00,0x00}, /* spc */
{0x04,0x04,0x04,0x04,0x00,0x04,0x00}, /* !   */
{0x0A,0x0A,0x00,0x00,0x00,0x00,0x00}, /* "   */
{0x0A,0x1F,0x0A,0x0A,0x1F,0x0A,0x00}, /* #  */
{0x04,0x0F,0x14,0x0E,0x05,0x1E,0x04}, /* $  */
{0x18,0x19,0x02,0x04,0x13,0x03,0x00}, /* %  */
{0x0C,0x12,0x14,0x08,0x15,0x12,0x0D}, /* &  */
{0x04,0x04,0x00,0x00,0x00,0x00,0x00}, /* '  */
{0x02,0x04,0x08,0x08,0x08,0x04,0x02}, /* (  */
{0x08,0x04,0x02,0x02,0x02,0x04,0x08}, /* )  */
{0x00,0x04,0x15,0x0E,0x15,0x04,0x00}, /* *  */
{0x00,0x04,0x04,0x1F,0x04,0x04,0x00}, /* +  */
{0x00,0x00,0x00,0x00,0x06,0x04,0x08}, /* ,  */
{0x00,0x00,0x00,0x1F,0x00,0x00,0x00}, /* -  */
{0x00,0x00,0x00,0x00,0x00,0x06,0x06}, /* .  */
{0x01,0x01,0x02,0x04,0x08,0x10,0x10}, /* /  */
{0x0E,0x11,0x13,0x15,0x19,0x11,0x0E}, /* 0  */
{0x04,0x0C,0x04,0x04,0x04,0x04,0x0E}, /* 1  */
{0x0E,0x11,0x01,0x02,0x04,0x08,0x1F}, /* 2  */
{0x1F,0x02,0x04,0x02,0x01,0x11,0x0E}, /* 3  */
{0x02,0x06,0x0A,0x12,0x1F,0x02,0x02}, /* 4  */
{0x1F,0x10,0x1E,0x01,0x01,0x11,0x0E}, /* 5  */
{0x06,0x08,0x10,0x1E,0x11,0x11,0x0E}, /* 6  */
{0x1F,0x01,0x02,0x04,0x08,0x08,0x08}, /* 7  */
{0x0E,0x11,0x11,0x0E,0x11,0x11,0x0E}, /* 8  */
{0x0E,0x11,0x11,0x0F,0x01,0x02,0x0C}, /* 9  */
{0x00,0x06,0x06,0x00,0x06,0x06,0x00}, /* :  */
{0x00,0x06,0x06,0x00,0x06,0x04,0x08}, /* ;  */
{0x02,0x04,0x08,0x10,0x08,0x04,0x02}, /* <  */
{0x00,0x00,0x1F,0x00,0x1F,0x00,0x00}, /* =  */
{0x08,0x04,0x02,0x01,0x02,0x04,0x08}, /* >  */
{0x0E,0x11,0x01,0x02,0x04,0x00,0x04}, /* ?  */
{0x0E,0x11,0x01,0x0D,0x15,0x15,0x0E}, /* @  */
{0x0E,0x11,0x11,0x1F,0x11,0x11,0x11}, /* A  */
{0x1E,0x11,0x11,0x1E,0x11,0x11,0x1E}, /* B  */
{0x0E,0x11,0x10,0x10,0x10,0x11,0x0E}, /* C  */
{0x1C,0x12,0x11,0x11,0x11,0x12,0x1C}, /* D  */
{0x1F,0x10,0x10,0x1E,0x10,0x10,0x1F}, /* E  */
{0x1F,0x10,0x10,0x1E,0x10,0x10,0x10}, /* F  */
{0x0E,0x11,0x10,0x17,0x11,0x11,0x0F}, /* G  */
{0x11,0x11,0x11,0x1F,0x11,0x11,0x11}, /* H  */
{0x0E,0x04,0x04,0x04,0x04,0x04,0x0E}, /* I  */
{0x07,0x02,0x02,0x02,0x02,0x12,0x0C}, /* J  */
{0x11,0x12,0x14,0x18,0x14,0x12,0x11}, /* K  */
{0x10,0x10,0x10,0x10,0x10,0x10,0x1F}, /* L  */
{0x11,0x1B,0x15,0x15,0x11,0x11,0x11}, /* M  */
{0x11,0x19,0x15,0x13,0x11,0x11,0x11}, /* N  */
{0x0E,0x11,0x11,0x11,0x11,0x11,0x0E}, /* O  */
{0x1E,0x11,0x11,0x1E,0x10,0x10,0x10}, /* P  */
{0x0E,0x11,0x11,0x11,0x15,0x12,0x0D}, /* Q  */
{0x1E,0x11,0x11,0x1E,0x14,0x12,0x11}, /* R  */
{0x0F,0x10,0x10,0x0E,0x01,0x01,0x1E}, /* S  */
{0x1F,0x04,0x04,0x04,0x04,0x04,0x04}, /* T  */
{0x11,0x11,0x11,0x11,0x11,0x11,0x0E}, /* U  */
{0x11,0x11,0x11,0x11,0x11,0x0A,0x04}, /* V  */
{0x11,0x11,0x11,0x15,0x15,0x15,0x0A}, /* W  */
{0x11,0x11,0x0A,0x04,0x0A,0x11,0x11}, /* X  */
{0x11,0x11,0x0A,0x04,0x04,0x04,0x04}, /* Y  */
{0x1F,0x01,0x02,0x04,0x08,0x10,0x1F}, /* Z  */
};

/* ────────────────────────────────────────────────────────
 * SHADER KAYNAKLARI
 * ──────────────────────────────────────────────────────── */
static const char* VS_SRC =
    "attribute vec2 aPos;\n"
    "uniform   vec2 uRes;\n"
    "void main(){\n"
    "  vec2 n=(aPos/uRes)*2.0-1.0;\n"
    "  gl_Position=vec4(n.x,-n.y,0.0,1.0);\n"
    "}\n";

static const char* FS_SRC =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "void main(){ gl_FragColor=uColor; }\n";

static const char* VS_TEX =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "uniform   vec2 uRes;\n"
    "varying   vec2 vUV;\n"
    "void main(){\n"
    "  vec2 n=(aPos/uRes)*2.0-1.0;\n"
    "  gl_Position=vec4(n.x,-n.y,0.0,1.0);\n"
    "  vUV=aUV;\n"
    "}\n";

static const char* FS_TEX =
    "precision mediump float;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uAlpha;\n"
    "varying vec2 vUV;\n"
    "void main(){\n"
    "  vec4 c=texture2D(uTex,vUV);\n"
    "  gl_FragColor=vec4(c.rgb,c.a*uAlpha);\n"
    "}\n";

/* ────────────────────────────────────────────────────────
 * SHADER DERLEME
 * ──────────────────────────────────────────────────────── */
static GLuint compileShader(GLenum type, const char* src){
    GLuint s = glCreateShader(type);
    if(!s){ LE("glCreateShader fail"); return 0; }
    glShaderSource(s,1,&src,nullptr);
    glCompileShader(s);
    GLint ok=0; glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
    if(!ok){
        char buf[512]={};
        glGetShaderInfoLog(s,512,nullptr,buf);
        LE("Shader hata: %s",buf);
        glDeleteShader(s); return 0;
    }
    return s;
}

static GLuint createProg(const char* vs, const char* fs){
    GLuint v=compileShader(GL_VERTEX_SHADER,  vs); if(!v)return 0;
    GLuint f=compileShader(GL_FRAGMENT_SHADER,fs);
    if(!f){ glDeleteShader(v); return 0; }
    GLuint p=glCreateProgram();
    glAttachShader(p,v); glAttachShader(p,f);
    glLinkProgram(p);
    glDeleteShader(v); glDeleteShader(f);
    GLint ok=0; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    if(!ok){ LE("Program link fail"); glDeleteProgram(p); return 0; }
    LI("Program OK id=%u",p);
    return p;
}

/* ────────────────────────────────────────────────────────
 * PNG DECODE  (stored-block only)
 * ──────────────────────────────────────────────────────── */
static inline uint32_t rd32be(const uint8_t* p){
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|
           ((uint32_t)p[2]<<8)|(uint32_t)p[3];
}
static uint8_t paethP(uint8_t a,uint8_t b,uint8_t c){
    int pa=abs((int)b-c),pb=abs((int)a-c),
        pc=abs((int)a+(int)b-2*(int)c);
    return (pa<=pb&&pa<=pc)?a:(pb<=pc)?b:c;
}
static bool inflate0(const uint8_t* s,int sn,uint8_t* d,int dn){
    if(sn<2)return false;
    int p=2,o=0;
    while(p<sn-4&&o<dn){
        bool fin=(s[p]&1)!=0; int bt=(s[p]>>1)&3; p++;
        if(bt==0){
            if(p&1)p++;
            if(p+4>sn)return false;
            uint16_t len=(uint16_t)s[p]|(uint16_t)((uint16_t)s[p+1]<<8);
            p+=4;
            if(p+len>sn||o+(int)len>dn)return false;
            memcpy(d+o,s+p,len); o+=len; p+=len;
        } else {
            LE("PNG: deflate blok. stb_image.h ekleyin.");
            return false;
        }
        if(fin) break;
    }
    return o==dn;
}
static bool decodePNG(const uint8_t* data, int sz,
                      uint8_t** outPx, int* outW, int* outH){
    static const uint8_t SIG[8]={137,80,78,71,13,10,26,10};
    if(sz<8||memcmp(data,SIG,8)!=0){ LE("PNG sig fail"); return false; }
    int pos=8,w=0,h=0,ch=0;
    uint8_t* idat=nullptr; int il=0,ic=0; bool gotEnd=false;
    while(pos+12<=sz&&!gotEnd){
        int cl=(int)rd32be(data+pos); pos+=4;
        char tp[5]; memcpy(tp,data+pos,4); tp[4]=0; pos+=4;
        if(!strcmp(tp,"IHDR")){
            w=(int)rd32be(data+pos); h=(int)rd32be(data+pos+4);
            int bd=data[pos+8],ct=data[pos+9];
            ch=(ct==6)?4:(ct==2)?3:0;
            if(bd!=8||ch==0){
                LE("PNG: sadece RGB/RGBA 8bit. ct=%d bd=%d",ct,bd);
                free(idat); return false;
            }
            LI("PNG IHDR %dx%d ch=%d",w,h,ch);
        } else if(!strcmp(tp,"IDAT")){
            if(il+cl>ic){
                ic=il+cl+65536;
                idat=(uint8_t*)realloc(idat,(size_t)ic);
                if(!idat){ LE("realloc fail"); return false; }
            }
            memcpy(idat+il,data+pos,(size_t)cl); il+=cl;
        } else if(!strcmp(tp,"IEND")) gotEnd=true;
        pos+=cl+4;
    }
    if(!gotEnd||!idat||w==0||h==0){ free(idat); return false; }
    int stride=w*ch, rawSz=h*(stride+1);
    uint8_t* raw=(uint8_t*)malloc((size_t)rawSz);
    if(!raw){ free(idat); return false; }
    if(!inflate0(idat,il,raw,rawSz)){ free(idat); free(raw); return false; }
    free(idat);
    uint8_t* px=(uint8_t*)malloc((size_t)(w*h*4));
    uint8_t* prev=(uint8_t*)calloc((size_t)stride,1);
    if(!px||!prev){ free(raw);free(px);free(prev); return false; }
    for(int y=0;y<h;y++){
        uint8_t* row=raw+y*(stride+1);
        uint8_t  ft=row[0], *cur=row+1;
        for(int x=0;x<stride;x++){
            uint8_t a=(x>=ch)?cur[x-ch]:0, b=prev[x],
                    c=(x>=ch)?prev[x-ch]:0;
            switch(ft){
                case 1:cur[x]+=a;    break;
                case 2:cur[x]+=b;    break;
                case 3:cur[x]+=(uint8_t)((a+b)/2); break;
                case 4:cur[x]+=paethP(a,b,c); break;
                default: break;
            }
        }
        memcpy(prev,cur,(size_t)stride);
        uint8_t* o2=px+y*w*4;
        for(int x=0;x<w;x++){
            o2[x*4+0]=cur[x*ch+0];
            o2[x*4+1]=cur[x*ch+1];
            o2[x*4+2]=cur[x*ch+2];
            o2[x*4+3]=(ch==4)?cur[x*ch+3]:255;
        }
    }
    free(raw); free(prev);
    *outPx=px; *outW=w; *outH=h;
    LI("PNG decode OK %dx%d",w,h);
    return true;
}

static GLuint loadTex(AAssetManager* am, const char* name,
                      int* tw, int* th){
    if(!am){ LE("am null"); return 0; }
    /* İki yol dene */
    AAsset* a=AAssetManager_open(am,name,AASSET_MODE_BUFFER);
    if(!a){
        char alt[128]; snprintf(alt,sizeof(alt),"assets/%s",name);
        a=AAssetManager_open(am,alt,AASSET_MODE_BUFFER);
        if(!a){ LE("Asset bulunamadi: %s",name); return 0; }
    }
    LI("Asset acildi: %s boyut=%d",name,(int)AAsset_getLength(a));
    const uint8_t* buf=(const uint8_t*)AAsset_getBuffer(a);
    int sz=(int)AAsset_getLength(a);
    uint8_t* px=nullptr; int w=0,h=0;
    bool ok=decodePNG(buf,sz,&px,&w,&h);
    AAsset_close(a);
    if(!ok){ LE("PNG decode fail: %s",name); return 0; }
    GLuint tex=0; glGenTextures(1,&tex);
    glBindTexture(GL_TEXTURE_2D,tex);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,
                 GL_RGBA,GL_UNSIGNED_BYTE,px);
    free(px);
    if(tw)*tw=w; if(th)*th=h;
    LI("Texture OK: %s id=%u %dx%d",name,tex,w,h);
    return tex;
}

/* ────────────────────────────────────────────────────────
 * ÇİZİM YARDIMCILARI
 * ──────────────────────────────────────────────────────── */
static float gSW=1, gSH=1;
static GLuint gCP=0, gTP=0;  /* colour program, tex program */

static void drawRect(float x,float y,float w,float h,const float* c){
    float v[]={x,y,x+w,y,x,y+h,x+w,y+h};
    glUseProgram(gCP);
    glUniform2f(glGetUniformLocation(gCP,"uRes"),gSW,gSH);
    glUniform4fv(glGetUniformLocation(gCP,"uColor"),1,c);
    GLint ap=glGetAttribLocation(gCP,"aPos");
    glEnableVertexAttribArray((GLuint)ap);
    glVertexAttribPointer((GLuint)ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray((GLuint)ap);
}

static void drawTex(GLuint t,float x,float y,float w,float h,float a){
    if(!t) return;
    float v[]={x,y,0,0, x+w,y,1,0, x,y+h,0,1, x+w,y+h,1,1};
    glUseProgram(gTP);
    glUniform2f(glGetUniformLocation(gTP,"uRes"),gSW,gSH);
    glUniform1i(glGetUniformLocation(gTP,"uTex"),0);
    glUniform1f(glGetUniformLocation(gTP,"uAlpha"),a);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,t);
    GLint ap=glGetAttribLocation(gTP,"aPos");
    GLint au=glGetAttribLocation(gTP,"aUV");
    glEnableVertexAttribArray((GLuint)ap);
    glEnableVertexAttribArray((GLuint)au);
    glVertexAttribPointer((GLuint)ap,2,GL_FLOAT,GL_FALSE,
                          4*sizeof(float),(void*)0);
    glVertexAttribPointer((GLuint)au,2,GL_FLOAT,GL_FALSE,
                          4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray((GLuint)ap);
    glDisableVertexAttribArray((GLuint)au);
}

static void drawPix(unsigned char c,float x,float y,float ps,
                    const float* col){
    int idx=(int)c-32;
    if(idx<0||idx>=59) idx=0;
    for(int r=0;r<7;r++){
        uint8_t bits=FONT[idx][r];
        for(int cc=0;cc<5;cc++)
            if(bits&(0x10>>cc))
                drawRect(x+(float)cc*ps,y+(float)r*ps,ps,ps,col);
    }
}

static float textWidth(const char* t,float ps){
    int n=0;
    const unsigned char* p=(const unsigned char*)t;
    while(*p){ if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++;}else p++; n++; }
    return (float)n*6.f*ps;
}

static void drawText(const char* t,float x,float y,
                     float ps,const float* col){
    float cx=x;
    const unsigned char* p=(const unsigned char*)t;
    while(*p){
        if(*p>=0x80){ p++; if(*p&&(*p&0xC0)==0x80)p++;
                      cx+=6.f*ps; continue; }
        drawPix(*p,cx,y,ps,col);
        cx+=6.f*ps; p++;
    }
}

static void drawTextC(const char* t,float cx,float y,
                      float ps,const float* col){
    drawText(t,cx-textWidth(t,ps)*.5f,y,ps,col);
}

/* ────────────────────────────────────────────────────────
 * BUTON
 * ──────────────────────────────────────────────────────── */
struct Btn{ float x,y,w,h; bool en,pr; const char* lbl; };

static bool hitBtn(const Btn& b,float tx,float ty){
    return b.en&&tx>=b.x&&tx<=b.x+b.w&&ty>=b.y&&ty<=b.y+b.h;
}

static void drawBtn(const Btn& b,float ps){
    float bw=gSW*.005f;
    const float* gc= b.en?C_GOLD:C_GREY;
    float bc[4]={gc[0],gc[1],gc[2], b.en?1.f:.30f};
    drawRect(b.x-bw,b.y-bw,b.w+2*bw,b.h+2*bw,bc);
    const float* fi=b.pr?C_PRES:C_DARK;
    float fc[4]={fi[0],fi[1],fi[2],.94f};
    drawRect(b.x,b.y,b.w,b.h,fc);
    /* köşe noktaları */
    float cs=gSW*.010f;
    drawRect(b.x,        b.y,        cs,cs,bc);
    drawRect(b.x+b.w-cs, b.y,        cs,cs,bc);
    drawRect(b.x,        b.y+b.h-cs, cs,cs,bc);
    drawRect(b.x+b.w-cs, b.y+b.h-cs, cs,cs,bc);
    /* etiket */
    float lc[4]={gc[0],gc[1],gc[2],b.en?1.f:.4f};
    drawText(b.lbl,
             b.x+(b.w-textWidth(b.lbl,ps))*.5f,
             b.y+(b.h-7.f*ps)*.5f,
             ps,lc);
}

/* ────────────────────────────────────────────────────────
 * UYGULAMA YAPISI  (HEAP'TE TUTULUR)
 * ──────────────────────────────────────────────────────── */
enum Scr{ S_MENU,S_SETTINGS,S_BOOT };

struct App {
    /* EGL */
    EGLDisplay  dpy = EGL_NO_DISPLAY;
    EGLSurface  suf = EGL_NO_SURFACE;
    EGLContext  ctx = EGL_NO_CONTEXT;
    int32_t     W=0, H=0;

    /* Aşama bayrakları */
    bool        eglOk    = false;  /* AŞAMA 1 tamamlandı */
    bool        shaderOk = false;  /* AŞAMA 2 tamamlandı */

    /* Textures */
    GLuint      texBG=0, texLogo=0;
    int         bgW=0,bgH=0, lgW=0,lgH=0;

    AAssetManager* am=nullptr;

    Scr         scr     = S_MENU;
    bool        hasSave = false;
    bool        sndOn   = true;
    int         fps     = 60;
    long        bootT   = 0;
    long        lastF   = 0;

    /* Butonlar */
    Btn         mb[4];
    Btn         sb[4];

    /* Label bufferları (pointer güvenliği) */
    char        sesLbl[32] = {};
    char        fpsTxt[32] = {};
};

static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}

static void scanLines(){
    float c[4]={0,0,0,.11f};
    for(float y=0;y<gSH;y+=4.f) drawRect(0,y,gSW,1.5f,c);
}

/* ════════════════ RENDER ════════════════ */

/* AŞAMA 1: Sadece düz renk — shader kullanmadan */
static void renderPhase1(App& a, EGLDisplay dpy, EGLSurface suf){
    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(dpy,suf);
    LI("AŞAMA1: clearColor OK");
}

/* AŞAMA 2+3: Tam menü */
static void renderMenu(App& a){
    float sw=gSW, sh=gSH;
    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    if(a.texBG) drawTex(a.texBG,0,0,sw,sh,.78f);
    else {
        /* Fallback: koyu vinyeta */
        float fade[4]={C_BG[0],C_BG[1],C_BG[2],.60f};
        drawRect(0,0,sw,sh,fade);
    }

    float ov[4]={C_BG[0],C_BG[1],C_BG[2],.48f};
    drawRect(0,0,sw,sh,ov);
    scanLines();

    /* Üst/alt şeritler */
    drawRect(0,sh*.010f,sw,sh*.004f,C_GOLD);
    drawRect(0,sh*.975f,sw,sh*.004f,C_GOLD);

    /* Logo */
    if(a.texLogo&&a.lgW>0){
        float ar=(float)a.lgW/(float)a.lgH;
        float lw=sw*.48f, lh=lw/ar;
        if(lh>sh*.18f){lh=sh*.18f; lw=lh*ar;}
        drawTex(a.texLogo,(sw-lw)*.5f,sh*.04f,lw,lh,1.f);
    } else {
        float ps=sw*.026f;
        drawTextC("MUHUR",sw*.5f,sh*.07f,ps,C_GOLD);
    }

    /* Motto */
    drawTextC("KADERIN, MUHRUNUN UCUNDA.",
              sw*.5f,sh*.265f,sw*.013f,C_GDIM);

    /* Ayraç */
    drawRect(sw*.08f,sh*.310f,sw*.84f,sh*.003f,C_GDIM);

    /* Butonlar */
    float bw=sw*.72f, bh=sh*.074f;
    float bx=(sw-bw)*.5f, gap=sh*.020f, sy=sh*.355f;
    float ps=sw*.024f;
    const char* lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool  en[4]={true,a.hasSave,true,true};
    for(int i=0;i<4;i++){
        a.mb[i].x=bx; a.mb[i].y=sy+(float)i*(bh+gap);
        a.mb[i].w=bw; a.mb[i].h=bh;
        a.mb[i].en=en[i]; a.mb[i].lbl=lb[i];
        drawBtn(a.mb[i],ps);
    }

    /* Alt bilgi */
    float dps=sw*.012f;
    drawTextC("BONECASTOFFICIAL",sw*.5f,sh*.938f,dps,C_GDIM);
    drawText("V0.1",sw*.04f,sh*.953f,dps,C_GDIM);
}

static void renderBoot(App& a){
    float sw=gSW, sh=gSH;
    glViewport(0,0,a.W,a.H);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
    long el=nowMs()-a.bootT;
    int  dots=(int)(el/400)%4;
    char ln[48]="SİSTEM BAŞLATILIYOR";
    for(int i=0;i<dots;i++) strncat(ln,".",sizeof(ln)-strlen(ln)-1);
    float ps=sw*.020f;
    drawTextC(ln,sw*.5f,sh*.42f,ps,C_GOLD);
    float prog=fminf((float)el/2000.f,1.f);
    float bx=sw*.15f,by=sh*.53f,bw=sw*.70f,bh=sh*.018f;
    drawRect(bx,by,bw,bh,C_DARK);
    drawRect(bx,by,bw*prog,bh,C_GOLD);
    scanLines();
    if(el>2200) a.scr=S_MENU;
}

static void renderSettings(App& a){
    float sw=gSW, sh=gSH;
    glViewport(0,0,a.W,a.H);
    glClearColor(C_BG[0],C_BG[1],C_BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    if(a.texBG) drawTex(a.texBG,0,0,sw,sh,.28f);
    float ov[4]={C_BG[0],C_BG[1],C_BG[2],.72f};
    drawRect(0,0,sw,sh,ov); scanLines();

    float ps=sw*.026f;
    drawTextC("AYARLAR",sw*.5f,sh*.06f,ps,C_GOLD);
    drawRect(sw*.10f,sh*.145f,sw*.80f,sh*.003f,C_GDIM);

    float bw=sw*.70f,bh=sh*.075f,bx=(sw-bw)*.5f,bp=sw*.022f;

    snprintf(a.sesLbl,sizeof(a.sesLbl),"SES: %s",a.sndOn?"ACIK":"KAPALI");
    a.sb[0]={bx,sh*.22f,bw,bh,true,a.sb[0].pr,a.sesLbl};
    drawBtn(a.sb[0],bp);

    if(a.fps==30){float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],.15f};
                  drawRect(bx,sh*.33f,bw,bh,hl);}
    a.sb[1]={bx,sh*.33f,bw,bh,true,a.sb[1].pr,"FPS: 30"};
    drawBtn(a.sb[1],bp);

    if(a.fps==60){float hl[4]={C_GOLD[0],C_GOLD[1],C_GOLD[2],.15f};
                  drawRect(bx,sh*.44f,bw,bh,hl);}
    a.sb[2]={bx,sh*.44f,bw,bh,true,a.sb[2].pr,"FPS: 60"};
    drawBtn(a.sb[2],bp);

    Btn back={bx,sh*.62f,bw,bh,true,false,"GERİ"};
    drawBtn(back,bp);
    /* Geri butonunu sabit çizmek için sb[3] de kullan */
    a.sb[3]=back;

    snprintf(a.fpsTxt,sizeof(a.fpsTxt),"HEDEF: %d FPS",a.fps);
    drawTextC(a.fpsTxt,sw*.5f,sh*.73f,sw*.013f,C_GDIM);
}

/* ────────────────────────────────────────────────────────
 * DOKUNMA
 * ──────────────────────────────────────────────────────── */
static void onTap(App& a,float tx,float ty,android_app* aapp){
    if(a.scr==S_MENU){
        if     (hitBtn(a.mb[0],tx,ty)){a.scr=S_BOOT;a.bootT=nowMs();}
        else if(hitBtn(a.mb[1],tx,ty)&&a.hasSave) LI("DEVAM ET");
        else if(hitBtn(a.mb[2],tx,ty)) a.scr=S_SETTINGS;
        else if(hitBtn(a.mb[3],tx,ty)) ANativeActivity_finish(aapp->activity);
    } else if(a.scr==S_SETTINGS){
        if     (hitBtn(a.sb[0],tx,ty)) a.sndOn=!a.sndOn;
        else if(hitBtn(a.sb[1],tx,ty)) a.fps=30;
        else if(hitBtn(a.sb[2],tx,ty)) a.fps=60;
        else if(hitBtn(a.sb[3],tx,ty)) a.scr=S_MENU;
    } else if(a.scr==S_BOOT){
        a.scr=S_MENU;
    }
}

/* ────────────────────────────────────────────────────────
 * ANDROID CALLBACKS
 * ──────────────────────────────────────────────────────── */
static void onCmd(android_app* aapp, int32_t cmd){
    App* ap=(App*)aapp->userData;
    if(!ap) return;
    App& a=*ap;

    switch(cmd){

    case APP_CMD_INIT_WINDOW:{
        LI("=== APP_CMD_INIT_WINDOW ===");
        if(!aapp->window){ LE("window=null"); break; }

        /* ── EGL ── */
        a.dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(a.dpy==EGL_NO_DISPLAY){ LE("eglGetDisplay fail"); break; }
        LI("eglGetDisplay OK");

        EGLint maj=0,min=0;
        if(!eglInitialize(a.dpy,&maj,&min)){ LE("eglInitialize fail"); break; }
        LI("eglInitialize OK v%d.%d",maj,min);

        const EGLint att[]={
            EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8, EGL_GREEN_SIZE,8,
            EGL_BLUE_SIZE,8, EGL_ALPHA_SIZE,8,
            EGL_NONE};
        EGLConfig cfg; EGLint nc=0;
        if(!eglChooseConfig(a.dpy,att,&cfg,1,&nc)||nc==0){
            LE("eglChooseConfig fail (nc=%d)",nc); break; }
        LI("eglChooseConfig OK cfg=%p",cfg);

        a.suf=eglCreateWindowSurface(a.dpy,cfg,aapp->window,nullptr);
        if(a.suf==EGL_NO_SURFACE){
            LE("eglCreateWindowSurface fail err=0x%x",eglGetError()); break; }
        LI("eglCreateWindowSurface OK");

        const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
        a.ctx=eglCreateContext(a.dpy,cfg,EGL_NO_CONTEXT,ca);
        if(a.ctx==EGL_NO_CONTEXT){
            LE("eglCreateContext fail err=0x%x",eglGetError()); break; }
        LI("eglCreateContext OK");

        if(eglMakeCurrent(a.dpy,a.suf,a.suf,a.ctx)==EGL_FALSE){
            LE("eglMakeCurrent fail err=0x%x",eglGetError()); break; }
        LI("eglMakeCurrent OK");

        eglQuerySurface(a.dpy,a.suf,EGL_WIDTH, &a.W);
        eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
        LI("Surface %dx%d",a.W,a.H);

        a.eglOk=true;
        gSW=(float)a.W; gSH=(float)a.H;

        /* AŞAMA 1: Düz renk göster */
        renderPhase1(a,a.dpy,a.suf);

        /* ── AŞAMA 2: Shader ── */
        gCP=createProg(VS_SRC,FS_SRC);
        gTP=createProg(VS_TEX,FS_TEX);
        if(!gCP||!gTP){ LE("Shader fail"); break; }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        a.shaderOk=true;
        LI("AŞAMA2: shader OK");

        /* ── AŞAMA 3: Assets (hata olursa devam) ── */
        a.am=aapp->activity->assetManager;
        LI("AssetManager=%p",a.am);
        a.texBG  =loadTex(a.am,"menu_bg.png",&a.bgW,&a.bgH);
        a.texLogo=loadTex(a.am,"logo.png",   &a.lgW,&a.lgH);
        LI("AŞAMA3: texBG=%u texLogo=%u",a.texBG,a.texLogo);

        memset(a.mb,0,sizeof(a.mb));
        memset(a.sb,0,sizeof(a.sb));
        a.lastF=nowMs();
        LI("=== HAZIR ===");
        break;
    }

    case APP_CMD_TERM_WINDOW:
        LI("APP_CMD_TERM_WINDOW");
        a.eglOk=false; a.shaderOk=false;
        if(a.texBG)  {glDeleteTextures(1,&a.texBG);  a.texBG=0;}
        if(a.texLogo){glDeleteTextures(1,&a.texLogo);a.texLogo=0;}
        if(gCP){glDeleteProgram(gCP);gCP=0;}
        if(gTP){glDeleteProgram(gTP);gTP=0;}
        if(a.dpy!=EGL_NO_DISPLAY){
            eglMakeCurrent(a.dpy,EGL_NO_SURFACE,
                           EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if(a.ctx!=EGL_NO_CONTEXT) eglDestroyContext(a.dpy,a.ctx);
            if(a.suf!=EGL_NO_SURFACE) eglDestroySurface(a.dpy,a.suf);
            eglTerminate(a.dpy);
        }
        a.dpy=EGL_NO_DISPLAY; a.ctx=EGL_NO_CONTEXT; a.suf=EGL_NO_SURFACE;
        break;

    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if(a.dpy!=EGL_NO_DISPLAY){
            eglQuerySurface(a.dpy,a.suf,EGL_WIDTH, &a.W);
            eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
            gSW=(float)a.W; gSH=(float)a.H;
            LI("Resize %dx%d",a.W,a.H);
        }
        break;

    default: break;
    }
}

static int32_t onInp(android_app* aapp,AInputEvent* evt){
    App* ap=(App*)aapp->userData;
    if(!ap||!ap->shaderOk) return 0;
    App& a=*ap;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION) return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float   tx =AMotionEvent_getX(evt,0);
    float   ty =AMotionEvent_getY(evt,0);
    auto clrPr=[&](){
        for(auto& b:a.mb) b.pr=false;
        for(auto& b:a.sb) b.pr=false;
    };
    if(act==AMOTION_EVENT_ACTION_DOWN){
        for(auto& b:a.mb) if(hitBtn(b,tx,ty)) b.pr=true;
        for(auto& b:a.sb) if(hitBtn(b,tx,ty)) b.pr=true;
    } else if(act==AMOTION_EVENT_ACTION_UP){
        clrPr(); onTap(a,tx,ty,aapp);
    } else if(act==AMOTION_EVENT_ACTION_CANCEL){ clrPr(); }
    return 1;
}

/* ────────────────────────────────────────────────────────
 * GİRİŞ NOKTASI
 * ──────────────────────────────────────────────────────── */
void android_main(android_app* aapp){
    LI("android_main START");

    /* App HEAP'te — stack overflow yok */
    App* app=new App();
    aapp->userData    =app;
    aapp->onAppCmd    =onCmd;
    aapp->onInputEvent=onInp;

    LI("Event loop basliyor");
    while(!aapp->destroyRequested){
        int                  ev=0;
        android_poll_source* src=nullptr;
        bool ready=(app->eglOk&&app->shaderOk);
        int  timeout=ready?0:-1;

        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src) src->process(aapp,src);
            if(aapp->destroyRequested) break;
        }

        if(!app->eglOk||!app->shaderOk) continue;

        /* FPS limiter */
        long now=nowMs();
        long fm=(app->fps==30)?33L:16L;
        if(now-app->lastF<fm){ usleep(2000); continue; }
        app->lastF=now;

        switch(app->scr){
            case S_MENU:     renderMenu(*app);     break;
            case S_BOOT:     renderBoot(*app);     break;
            case S_SETTINGS: renderSettings(*app); break;
        }
        eglSwapBuffers(app->dpy,app->suf);
    }

    LI("android_main END");
    delete app;
}

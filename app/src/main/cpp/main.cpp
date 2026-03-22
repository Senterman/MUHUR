/**
 * MÜHÜR — main.cpp
 * Ultra-savunmacı yaklaşım: her adımda logcat çıktısı.
 * Asset yoksa altın ekran göster, asla çökme.
 */

/* ── Sıralı include (NDK kuralı) ────────────────────────── */
#include <android_native_app_glue.h>
#include <android/asset_manager.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <unistd.h>
#include <time.h>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <cstdio>

/* ── Logcat ─────────────────────────────────────────────── */
#define TAG "MUHUR"
#define LI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

/* ════════════════════════════════════════════════════════
 * RENKLER
 * ════════════════════════════════════════════════════════ */
static const float BG[4]   = {0.051f,0.039f,0.020f,1.0f}; /* #0d0a05 */
static const float GOLD[4] = {0.831f,0.659f,0.325f,1.0f}; /* #d4a853 */
static const float GDIM[4] = {0.420f,0.330f,0.160f,1.0f};
static const float DARK[4] = {0.090f,0.070f,0.035f,1.0f};
static const float PRES[4] = {0.170f,0.130f,0.065f,1.0f};
static const float GREY[4] = {0.240f,0.220f,0.200f,1.0f};

/* ════════════════════════════════════════════════════════
 * PIXEL FONT 5×7 (ASCII 32–90)
 * ════════════════════════════════════════════════════════ */
static const uint8_t F57[59][7] = {
{0x00,0x00,0x00,0x00,0x00,0x00,0x00}, /* ' ' */
{0x04,0x04,0x04,0x04,0x00,0x04,0x00}, /* '!' */
{0x0A,0x0A,0x00,0x00,0x00,0x00,0x00}, /* '"' */
{0x0A,0x1F,0x0A,0x0A,0x1F,0x0A,0x00}, /* '#' */
{0x04,0x0F,0x14,0x0E,0x05,0x1E,0x04}, /* '$' */
{0x18,0x19,0x02,0x04,0x13,0x03,0x00}, /* '%' */
{0x0C,0x12,0x14,0x08,0x15,0x12,0x0D}, /* '&' */
{0x04,0x04,0x00,0x00,0x00,0x00,0x00}, /* ''' */
{0x02,0x04,0x08,0x08,0x08,0x04,0x02}, /* '(' */
{0x08,0x04,0x02,0x02,0x02,0x04,0x08}, /* ')' */
{0x00,0x04,0x15,0x0E,0x15,0x04,0x00}, /* '*' */
{0x00,0x04,0x04,0x1F,0x04,0x04,0x00}, /* '+' */
{0x00,0x00,0x00,0x00,0x06,0x04,0x08}, /* ',' */
{0x00,0x00,0x00,0x1F,0x00,0x00,0x00}, /* '-' */
{0x00,0x00,0x00,0x00,0x00,0x06,0x06}, /* '.' */
{0x01,0x01,0x02,0x04,0x08,0x10,0x10}, /* '/' */
{0x0E,0x11,0x13,0x15,0x19,0x11,0x0E}, /* '0' */
{0x04,0x0C,0x04,0x04,0x04,0x04,0x0E}, /* '1' */
{0x0E,0x11,0x01,0x02,0x04,0x08,0x1F}, /* '2' */
{0x1F,0x02,0x04,0x02,0x01,0x11,0x0E}, /* '3' */
{0x02,0x06,0x0A,0x12,0x1F,0x02,0x02}, /* '4' */
{0x1F,0x10,0x1E,0x01,0x01,0x11,0x0E}, /* '5' */
{0x06,0x08,0x10,0x1E,0x11,0x11,0x0E}, /* '6' */
{0x1F,0x01,0x02,0x04,0x08,0x08,0x08}, /* '7' */
{0x0E,0x11,0x11,0x0E,0x11,0x11,0x0E}, /* '8' */
{0x0E,0x11,0x11,0x0F,0x01,0x02,0x0C}, /* '9' */
{0x00,0x06,0x06,0x00,0x06,0x06,0x00}, /* ':' */
{0x00,0x06,0x06,0x00,0x06,0x04,0x08}, /* ';' */
{0x02,0x04,0x08,0x10,0x08,0x04,0x02}, /* '<' */
{0x00,0x00,0x1F,0x00,0x1F,0x00,0x00}, /* '=' */
{0x08,0x04,0x02,0x01,0x02,0x04,0x08}, /* '>' */
{0x0E,0x11,0x01,0x02,0x04,0x00,0x04}, /* '?' */
{0x0E,0x11,0x01,0x0D,0x15,0x15,0x0E}, /* '@' */
{0x0E,0x11,0x11,0x1F,0x11,0x11,0x11}, /* 'A' */
{0x1E,0x11,0x11,0x1E,0x11,0x11,0x1E}, /* 'B' */
{0x0E,0x11,0x10,0x10,0x10,0x11,0x0E}, /* 'C' */
{0x1C,0x12,0x11,0x11,0x11,0x12,0x1C}, /* 'D' */
{0x1F,0x10,0x10,0x1E,0x10,0x10,0x1F}, /* 'E' */
{0x1F,0x10,0x10,0x1E,0x10,0x10,0x10}, /* 'F' */
{0x0E,0x11,0x10,0x17,0x11,0x11,0x0F}, /* 'G' */
{0x11,0x11,0x11,0x1F,0x11,0x11,0x11}, /* 'H' */
{0x0E,0x04,0x04,0x04,0x04,0x04,0x0E}, /* 'I' */
{0x07,0x02,0x02,0x02,0x02,0x12,0x0C}, /* 'J' */
{0x11,0x12,0x14,0x18,0x14,0x12,0x11}, /* 'K' */
{0x10,0x10,0x10,0x10,0x10,0x10,0x1F}, /* 'L' */
{0x11,0x1B,0x15,0x15,0x11,0x11,0x11}, /* 'M' */
{0x11,0x19,0x15,0x13,0x11,0x11,0x11}, /* 'N' */
{0x0E,0x11,0x11,0x11,0x11,0x11,0x0E}, /* 'O' */
{0x1E,0x11,0x11,0x1E,0x10,0x10,0x10}, /* 'P' */
{0x0E,0x11,0x11,0x11,0x15,0x12,0x0D}, /* 'Q' */
{0x1E,0x11,0x11,0x1E,0x14,0x12,0x11}, /* 'R' */
{0x0F,0x10,0x10,0x0E,0x01,0x01,0x1E}, /* 'S' */
{0x1F,0x04,0x04,0x04,0x04,0x04,0x04}, /* 'T' */
{0x11,0x11,0x11,0x11,0x11,0x11,0x0E}, /* 'U' */
{0x11,0x11,0x11,0x11,0x11,0x0A,0x04}, /* 'V' */
{0x11,0x11,0x11,0x15,0x15,0x15,0x0A}, /* 'W' */
{0x11,0x11,0x0A,0x04,0x0A,0x11,0x11}, /* 'X' */
{0x11,0x11,0x0A,0x04,0x04,0x04,0x04}, /* 'Y' */
{0x1F,0x01,0x02,0x04,0x08,0x10,0x1F}, /* 'Z' */
};

/* ════════════════════════════════════════════════════════
 * SHADER
 * ════════════════════════════════════════════════════════ */
static const char* VS = R"(
attribute vec2 aPos;
uniform   vec2 uRes;
void main(){
    vec2 n=(aPos/uRes)*2.0-1.0;
    gl_Position=vec4(n.x,-n.y,0.0,1.0);
}
)";
static const char* FS = R"(
precision mediump float;
uniform vec4 uColor;
void main(){ gl_FragColor=uColor; }
)";
static const char* VS2 = R"(
attribute vec2 aPos;
attribute vec2 aUV;
uniform   vec2 uRes;
varying   vec2 vUV;
void main(){
    vec2 n=(aPos/uRes)*2.0-1.0;
    gl_Position=vec4(n.x,-n.y,0.0,1.0);
    vUV=aUV;
}
)";
static const char* FS2 = R"(
precision mediump float;
uniform sampler2D uTex;
uniform float uA;
varying vec2 vUV;
void main(){
    vec4 c=texture2D(uTex,vUV);
    gl_FragColor=vec4(c.rgb,c.a*uA);
}
)";

static GLuint mkSh(GLenum t, const char* s){
    GLuint h=glCreateShader(t);
    glShaderSource(h,1,&s,nullptr);
    glCompileShader(h);
    GLint ok=0; glGetShaderiv(h,GL_COMPILE_STATUS,&ok);
    if(!ok){ char b[512]; glGetShaderInfoLog(h,512,nullptr,b); LE("SH: %s",b); }
    return h;
}
static GLuint mkPr(const char* v,const char* f){
    GLuint p=glCreateProgram();
    GLuint vs=mkSh(GL_VERTEX_SHADER,v);
    GLuint fs=mkSh(GL_FRAGMENT_SHADER,f);
    glAttachShader(p,vs); glAttachShader(p,fs);
    glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok=0; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    if(!ok){ LE("PROG link fail"); }
    LI("Program id=%u ok=%d",p,ok);
    return p;
}

/* ════════════════════════════════════════════════════════
 * PNG DECODE (stored-block only)
 * ════════════════════════════════════════════════════════ */
static inline uint32_t u32be(const uint8_t* p){
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|
           ((uint32_t)p[2]<<8)|(uint32_t)p[3];
}
static uint8_t paeth(uint8_t a,uint8_t b,uint8_t c){
    int pa=abs((int)b-c),pb=abs((int)a-c),
        pc=abs((int)a+(int)b-2*(int)c);
    return (pa<=pb&&pa<=pc)?a:(pb<=pc)?b:c;
}
static bool inflate0(const uint8_t* s,int sn,uint8_t* d,int dn){
    if(sn<2)return false;
    int p=2,o=0;
    while(p<sn-4&&o<dn){
        bool fin=(s[p]&1)!=0;
        int  bt=(s[p]>>1)&3; p++;
        if(bt==0){
            if(p&1)p++;
            if(p+4>sn)return false;
            uint16_t len=(uint16_t)s[p]|(uint16_t)(s[p+1]<<8);
            p+=4;
            if(p+len>sn||o+len>dn)return false;
            memcpy(d+o,s+p,len); o+=len; p+=len;
        } else { LW("PNG:deflate,stb_image gerekli"); return false; }
        if(fin)break;
    }
    return o==dn;
}
static bool pngDecode(const uint8_t* data,int sz,
                      uint8_t** px,int* W,int* H){
    static const uint8_t SIG[8]={137,80,78,71,13,10,26,10};
    if(sz<8||memcmp(data,SIG,8))return false;
    int pos=8,w=0,h=0,ch=0;
    uint8_t* idat=nullptr; int il=0,ic=0; bool end=false;
    while(pos+12<=sz&&!end){
        int cl=(int)u32be(data+pos); pos+=4;
        char tp[5]; memcpy(tp,data+pos,4); tp[4]=0; pos+=4;
        if(!strcmp(tp,"IHDR")){
            w=(int)u32be(data+pos); h=(int)u32be(data+pos+4);
            int bd=data[pos+8],ct=data[pos+9];
            ch=(ct==6)?4:(ct==2)?3:0;
            if(bd!=8||ch==0){LE("PNG:bad fmt");free(idat);return false;}
            LI("PNG IHDR %dx%d ch=%d",w,h,ch);
        } else if(!strcmp(tp,"IDAT")){
            if(il+cl>ic){ic=il+cl+65536;idat=(uint8_t*)realloc(idat,(size_t)ic);}
            memcpy(idat+il,data+pos,(size_t)cl); il+=cl;
        } else if(!strcmp(tp,"IEND")) end=true;
        pos+=cl+4;
    }
    if(!end||!idat||w==0||h==0){free(idat);return false;}
    int st=w*ch, rs=h*(st+1);
    uint8_t* raw=(uint8_t*)malloc((size_t)rs);
    if(!raw){free(idat);return false;}
    if(!inflate0(idat,il,raw,rs)){free(idat);free(raw);return false;}
    free(idat);
    uint8_t* out=(uint8_t*)malloc((size_t)(w*h*4));
    uint8_t* prev=(uint8_t*)calloc((size_t)st,1);
    if(!out||!prev){free(raw);free(out);free(prev);return false;}
    for(int y=0;y<h;y++){
        uint8_t* row=raw+y*(st+1);
        uint8_t  ft=row[0],*cur=row+1;
        for(int x=0;x<st;x++){
            uint8_t a=(x>=ch)?cur[x-ch]:0,b=prev[x],
                    c=(x>=ch)?prev[x-ch]:0;
            switch(ft){
                case 1:cur[x]+=a;break; case 2:cur[x]+=b;break;
                case 3:cur[x]+=(uint8_t)((a+b)/2);break;
                case 4:cur[x]+=paeth(a,b,c);break; default:break;
            }
        }
        memcpy(prev,cur,(size_t)st);
        uint8_t* o2=out+y*w*4;
        for(int x=0;x<w;x++){
            o2[x*4  ]=cur[x*ch];
            o2[x*4+1]=cur[x*ch+1];
            o2[x*4+2]=cur[x*ch+2];
            o2[x*4+3]=(ch==4)?cur[x*ch+3]:255;
        }
    }
    free(raw);free(prev);
    *px=out;*W=w;*H=h;
    LI("PNG OK %dx%d",w,h);
    return true;
}

/* Asset'ten texture yükle — hem düz hem "assets/" ön ekiyle dene */
static GLuint loadTex(AAssetManager* am,const char* path,
                      int* tw,int* th){
    if(!am){ LW("am=null"); return 0; }

    /* Yol denemesi 1: doğrudan */
    AAsset* a=AAssetManager_open(am,path,AASSET_MODE_BUFFER);
    if(!a){
        /* Yol denemesi 2: assets/ öneki */
        char alt[128]="assets/";
        strncat(alt,path,120);
        a=AAssetManager_open(am,alt,AASSET_MODE_BUFFER);
        if(a) LI("Asset bulundu (alt yol): %s",alt);
        else { LW("Asset bulunamadi: %s",path); return 0; }
    } else LI("Asset bulundu: %s",path);

    const uint8_t* buf=(const uint8_t*)AAsset_getBuffer(a);
    int sz=(int)AAsset_getLength(a);
    LI("Asset boyutu: %d byte",sz);
    uint8_t* px=nullptr; int w=0,h=0;
    bool ok=pngDecode(buf,sz,&px,&w,&h);
    AAsset_close(a);
    if(!ok){ LW("PNG decode basarisiz: %s",path); return 0; }

    GLuint tex=0;
    glGenTextures(1,&tex);
    glBindTexture(GL_TEXTURE_2D,tex);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,
                 GL_RGBA,GL_UNSIGNED_BYTE,px);
    free(px);
    if(tw)*tw=w; if(th)*th=h;
    LI("Tex %s id=%u %dx%d",path,tex,w,h);
    return tex;
}

/* ════════════════════════════════════════════════════════
 * ÇİZİM
 * ════════════════════════════════════════════════════════ */
struct G { float sw,sh; GLuint cp,tp; };

static void dRect(const G& g,float x,float y,float w,float h,
                  const float* c){
    float v[]={x,y,x+w,y,x,y+h,x+w,y+h};
    glUseProgram(g.cp);
    glUniform2f(glGetUniformLocation(g.cp,"uRes"),g.sw,g.sh);
    glUniform4fv(glGetUniformLocation(g.cp,"uColor"),1,c);
    GLuint ap=(GLuint)glGetAttribLocation(g.cp,"aPos");
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
}

static void dTex(const G& g,GLuint t,
                 float x,float y,float w,float h,float a=1.f){
    if(!t)return;
    float v[]={x,y,0,0, x+w,y,1,0, x,y+h,0,1, x+w,y+h,1,1};
    glUseProgram(g.tp);
    glUniform2f(glGetUniformLocation(g.tp,"uRes"),g.sw,g.sh);
    glUniform1i(glGetUniformLocation(g.tp,"uTex"),0);
    glUniform1f(glGetUniformLocation(g.tp,"uA"),a);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D,t);
    GLuint ap=(GLuint)glGetAttribLocation(g.tp,"aPos");
    GLuint au=(GLuint)glGetAttribLocation(g.tp,"aUV");
    glEnableVertexAttribArray(ap);
    glEnableVertexAttribArray(au);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,
                          4*sizeof(float),(void*)0);
    glVertexAttribPointer(au,2,GL_FLOAT,GL_FALSE,
                          4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
    glDisableVertexAttribArray(au);
}

static void dChar(const G& g,unsigned char c,
                  float x,float y,float ps,const float* col){
    int idx=(int)c-32;
    if(idx<0||idx>=59)idx=0;
    for(int r=0;r<7;r++){
        uint8_t bits=F57[idx][r];
        for(int cc=0;cc<5;cc++)
            if(bits&(0x10>>cc))
                dRect(g,x+(float)cc*ps,y+(float)r*ps,ps,ps,col);
    }
}

static float tW(const char* t,float ps){
    int n=0;
    const unsigned char* p=(const unsigned char*)t;
    while(*p){ if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++;}else p++; n++; }
    return (float)n*6.f*ps;
}

static void dText(const G& g,const char* t,
                  float x,float y,float ps,const float* col){
    float cx=x;
    const unsigned char* p=(const unsigned char*)t;
    while(*p){
        if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++; cx+=6.f*ps; continue;}
        dChar(g,(unsigned char)*p,cx,y,ps,col);
        cx+=6.f*ps; p++;
    }
}

static void dTxtC(const G& g,const char* t,
                  float cx,float y,float ps,const float* col){
    dText(g,t,cx-tW(t,ps)*.5f,y,ps,col);
}

/* ════════════════════════════════════════════════════════
 * BUTON
 * ════════════════════════════════════════════════════════ */
struct Btn{
    float x,y,w,h;
    bool en,pr;
    const char* lbl;
};
static bool hit(const Btn& b,float tx,float ty){
    return b.en&&tx>=b.x&&tx<=b.x+b.w&&ty>=b.y&&ty<=b.y+b.h;
}
static void dBtn(const G& g,const Btn& b,float ps){
    float bw2=g.sw*.005f;
    /* kenarlık */
    float bc[4]={b.en?GOLD[0]:GREY[0],b.en?GOLD[1]:GREY[1],
                 b.en?GOLD[2]:GREY[2],b.en?1.f:.30f};
    dRect(g,b.x-bw2,b.y-bw2,b.w+2*bw2,b.h+2*bw2,bc);
    /* gövde */
    const float* fi=b.pr?PRES:DARK;
    float fc[4]={fi[0],fi[1],fi[2],.94f};
    dRect(g,b.x,b.y,b.w,b.h,fc);
    /* köşe noktaları */
    float cs=g.sw*.010f;
    dRect(g,b.x,         b.y,         cs,cs,bc);
    dRect(g,b.x+b.w-cs,  b.y,         cs,cs,bc);
    dRect(g,b.x,         b.y+b.h-cs,  cs,cs,bc);
    dRect(g,b.x+b.w-cs,  b.y+b.h-cs,  cs,cs,bc);
    /* etiket */
    float lc[4]={b.en?GOLD[0]:GREY[0],b.en?GOLD[1]:GREY[1],
                 b.en?GOLD[2]:GREY[2],b.en?1.f:.4f};
    dText(g,b.lbl,
          b.x+(b.w-tW(b.lbl,ps))*.5f,
          b.y+(b.h-7.f*ps)*.5f,
          ps,lc);
}

/* ════════════════════════════════════════════════════════
 * UYGULAMA
 * ════════════════════════════════════════════════════════ */
enum Scr{ S_MENU,S_SETTINGS,S_BOOT,S_CREDITS };

struct App{
    /* EGL */
    EGLDisplay  dpy=EGL_NO_DISPLAY;
    EGLSurface  suf=EGL_NO_SURFACE;
    EGLContext  ctx=EGL_NO_CONTEXT;
    int32_t     W=0,H=0;
    bool        ready=false;
    /* GL */
    GLuint      cp=0,tp=0;
    GLuint      texBG=0,texLogo=0;
    int         bgW=0,bgH=0,lgW=0,lgH=0;
    /* */
    AAssetManager* am=nullptr;
    Scr         scr=S_MENU;
    bool        hasSave=false;
    bool        sndOn=true;
    int         fps=60;
    long        bootT=0;
    long        lastF=0;
    /* butonlar */
    Btn         mb[4];
    Btn         sb[4];
    Btn         cb[1];
    /* label buffers */
    char        sesLbl[32];
    char        fpsTxt[32];
};

static long nowMs(){
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000L+ts.tv_nsec/1000000L;
}

/* scan-lines */
static void scanLines(const G& g){
    float c[4]={0,0,0,.12f};
    for(float y=0;y<g.sh;y+=4.f) dRect(g,0,y,g.sw,1.5f,c);
}

/* ═══════ RENDER ANA MENÜ ═══════ */
static void rMenu(App& a){
    G g={float(a.W),float(a.H),a.cp,a.tp};
    float sw=g.sw,sh=g.sh;

    glViewport(0,0,a.W,a.H);
    /* Eğer asset yoksa altın fallback */
    if(!a.texBG && !a.texLogo){
        glClearColor(GOLD[0]*0.3f,GOLD[1]*0.3f,GOLD[2]*0.3f,1.f);
        LI("FALLBACK: altin ekran");
    } else {
        glClearColor(BG[0],BG[1],BG[2],1.f);
    }
    glClear(GL_COLOR_BUFFER_BIT);

    if(a.texBG) dTex(g,a.texBG,0,0,sw,sh,.80f);
    float ov[4]={BG[0],BG[1],BG[2],.50f};
    dRect(g,0,0,sw,sh,ov);
    scanLines(g);

    /* Üst/alt şeritler */
    dRect(g,0,sh*.010f,sw,sh*.004f,GOLD);
    dRect(g,0,sh*.975f,sw,sh*.004f,GOLD);

    /* Logo */
    if(a.texLogo&&a.lgW>0){
        float ar=(float)a.lgW/a.lgH;
        float lw=sw*.50f,lh=lw/ar;
        if(lh>sh*.18f){lh=sh*.18f;lw=lh*ar;}
        dTex(g,a.texLogo,(sw-lw)*.5f,sh*.04f,lw,lh);
    } else {
        float ps=sw*.022f;
        dTxtC(g,"MUHUR",sw*.5f,sh*.07f,ps,GOLD);
    }

    /* Motto */
    float mps=sw*.014f;
    dTxtC(g,"KADERIN, MUHRUNUN UCUNDA.",
          sw*.5f,sh*.26f,mps,GDIM);

    /* Ayraç */
    dRect(g,sw*.08f,sh*.31f,sw*.84f,sh*.003f,GDIM);

    /* Butonlar */
    float bw=sw*.72f,bh=sh*.074f;
    float bx=(sw-bw)*.5f;
    float gap=sh*.020f,sy=sh*.355f;
    float ps=sw*.024f;
    const char* lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool  en[4]={true,a.hasSave,true,true};
    for(int i=0;i<4;i++){
        a.mb[i].x=bx; a.mb[i].y=sy+(float)i*(bh+gap);
        a.mb[i].w=bw; a.mb[i].h=bh;
        a.mb[i].en=en[i]; a.mb[i].lbl=lb[i];
        dBtn(g,a.mb[i],ps);
    }

    /* Alt bilgi */
    float dps=sw*.012f;
    dTxtC(g,"BONECASTOFFICIAL",sw*.5f,sh*.94f,dps,GDIM);
    dText(g,"V0.1",sw*.04f,sh*.955f,dps,GDIM);

    eglSwapBuffers(a.dpy,a.suf);
}

/* ═══════ RENDER BOOT ═══════ */
static void rBoot(App& a){
    G g={float(a.W),float(a.H),a.cp,a.tp};
    float sw=g.sw,sh=g.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
    long el=nowMs()-a.bootT;
    int  dots=(int)(el/400)%4;
    char ln[48]="SİSTEM BAŞLATILIYOR";
    for(int i=0;i<dots;i++) strcat(ln,".");
    float ps=sw*.020f;
    dTxtC(g,ln,sw*.5f,sh*.42f,ps,GOLD);
    /* bar */
    float prog=fminf((float)el/2000.f,1.f);
    float bx=sw*.15f,by=sh*.53f,bw=sw*.70f,bh=sh*.018f;
    dRect(g,bx,by,bw,bh,DARK);
    dRect(g,bx,by,bw*prog,bh,GOLD);
    scanLines(g);
    eglSwapBuffers(a.dpy,a.suf);
    if(el>2200) a.scr=S_MENU;
}

/* ═══════ RENDER AYARLAR ═══════ */
static void rSettings(App& a){
    G g={float(a.W),float(a.H),a.cp,a.tp};
    float sw=g.sw,sh=g.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    if(a.texBG) dTex(g,a.texBG,0,0,sw,sh,.30f);
    float ov[4]={BG[0],BG[1],BG[2],.70f}; dRect(g,0,0,sw,sh,ov);
    scanLines(g);

    float ps=sw*.026f;
    dTxtC(g,"AYARLAR",sw*.5f,sh*.06f,ps,GOLD);
    dRect(g,sw*.10f,sh*.145f,sw*.80f,sh*.003f,GDIM);

    float bw=sw*.70f,bh=sh*.075f,bx=(sw-bw)*.5f,bp=sw*.022f;

    /* Ses */
    snprintf(a.sesLbl,sizeof(a.sesLbl),"SES: %s",a.sndOn?"ACIK":"KAPALI");
    a.sb[0]={bx,sh*.22f,bw,bh,true,a.sb[0].pr,a.sesLbl};
    dBtn(g,a.sb[0],bp);

    /* FPS 30 */
    if(a.fps==30){ float hl[4]={GOLD[0],GOLD[1],GOLD[2],.15f};
                   dRect(g,bx,sh*.33f,bw,bh,hl); }
    a.sb[1]={bx,sh*.33f,bw,bh,true,a.sb[1].pr,"FPS: 30"};
    dBtn(g,a.sb[1],bp);

    /* FPS 60 */
    if(a.fps==60){ float hl[4]={GOLD[0],GOLD[1],GOLD[2],.15f};
                   dRect(g,bx,sh*.44f,bw,bh,hl); }
    a.sb[2]={bx,sh*.44f,bw,bh,true,a.sb[2].pr,"FPS: 60"};
    dBtn(g,a.sb[2],bp);

    /* Geri */
    a.sb[3]={bx,sh*.62f,bw,bh,true,a.sb[3].pr,"GERİ"};
    dBtn(g,a.sb[3],bp);

    snprintf(a.fpsTxt,sizeof(a.fpsTxt),"HEDEF: %d FPS",a.fps);
    float dps=sw*.013f;
    dTxtC(g,a.fpsTxt,sw*.5f,sh*.73f,dps,GDIM);
    eglSwapBuffers(a.dpy,a.suf);
}

/* ═══════ RENDER CREDITS ═══════ */
static void rCredits(App& a){
    G g={float(a.W),float(a.H),a.cp,a.tp};
    float sw=g.sw,sh=g.sh;
    glViewport(0,0,a.W,a.H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    if(a.texBG) dTex(g,a.texBG,0,0,sw,sh,.22f);
    float ov[4]={BG[0],BG[1],BG[2],.78f}; dRect(g,0,0,sw,sh,ov);
    scanLines(g);
    float ps=sw*.026f,dp=sw*.020f,dps=sw*.013f;
    dTxtC(g,"GELİSTİRİCİLER",sw*.5f,sh*.06f,ps,GOLD);
    dRect(g,sw*.10f,sh*.145f,sw*.80f,sh*.003f,GDIM);
    dTxtC(g,"BONECASTOFFICIAL",sw*.5f,sh*.35f,dp,GOLD);
    dTxtC(g,"OYUN TASARIMI & GELISTIRME",sw*.5f,sh*.46f,dps,GDIM);
    dTxtC(g,"MUHUR 2025",sw*.5f,sh*.52f,dps,GDIM);
    float bw=sw*.55f,bh=sh*.075f,bx=(sw-bw)*.5f;
    a.cb[0]={bx,sh*.70f,bw,bh,true,a.cb[0].pr,"GERİ"};
    dBtn(g,a.cb[0],sw*.022f);
    eglSwapBuffers(a.dpy,a.suf);
}

/* ════════════════════════════════════════════════════════
 * DOKUNMA
 * ════════════════════════════════════════════════════════ */
static void onTap(App& a,float tx,float ty,android_app* ap){
    switch(a.scr){
        case S_MENU:
            if     (hit(a.mb[0],tx,ty)){a.scr=S_BOOT;a.bootT=nowMs();}
            else if(hit(a.mb[1],tx,ty)&&a.hasSave) LI("DEVAM ET");
            else if(hit(a.mb[2],tx,ty)) a.scr=S_SETTINGS;
            else if(hit(a.mb[3],tx,ty)) ANativeActivity_finish(ap->activity);
            break;
        case S_SETTINGS:
            if     (hit(a.sb[0],tx,ty)) a.sndOn=!a.sndOn;
            else if(hit(a.sb[1],tx,ty)) a.fps=30;
            else if(hit(a.sb[2],tx,ty)) a.fps=60;
            else if(hit(a.sb[3],tx,ty)) a.scr=S_MENU;
            break;
        case S_CREDITS:
            if(hit(a.cb[0],tx,ty)) a.scr=S_MENU;
            break;
        case S_BOOT:
            a.scr=S_MENU; break;
    }
}

/* ════════════════════════════════════════════════════════
 * ANDROID CALLBACKS
 * ════════════════════════════════════════════════════════ */
static void onCmd(android_app* aapp,int32_t cmd){
    App& a=*(App*)aapp->userData;
    switch(cmd){

    case APP_CMD_INIT_WINDOW:{
        LI("APP_CMD_INIT_WINDOW");
        if(!aapp->window){ LE("window=null!"); break; }

        /* ── EGL ── */
        a.dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(a.dpy==EGL_NO_DISPLAY){ LE("eglGetDisplay fail"); break; }
        if(!eglInitialize(a.dpy,nullptr,nullptr)){LE("eglInit fail");break;}
        LI("EGL initialized");

        const EGLint att[]={
            EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,
            EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,
            EGL_NONE};
        EGLConfig cfg; EGLint nc=0;
        if(!eglChooseConfig(a.dpy,att,&cfg,1,&nc)||nc==0){
            LE("eglChooseConfig fail"); break; }
        LI("EGL config OK");

        a.suf=eglCreateWindowSurface(a.dpy,cfg,aapp->window,nullptr);
        if(a.suf==EGL_NO_SURFACE){ LE("eglCreateWindowSurface fail"); break; }
        LI("EGL surface OK");

        const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
        a.ctx=eglCreateContext(a.dpy,cfg,EGL_NO_CONTEXT,ca);
        if(a.ctx==EGL_NO_CONTEXT){ LE("eglCreateContext fail"); break; }
        LI("EGL context OK");

        if(eglMakeCurrent(a.dpy,a.suf,a.suf,a.ctx)==EGL_FALSE){
            LE("eglMakeCurrent fail 0x%x",eglGetError()); break; }
        LI("EGL make current OK");

        eglQuerySurface(a.dpy,a.suf,EGL_WIDTH, &a.W);
        eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
        LI("Surface size %dx%d",a.W,a.H);

        /* ── GL ── */
        a.cp=mkPr(VS,FS);
        a.tp=mkPr(VS2,FS2);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);

        /* ── Assets ── */
        a.am=aapp->activity->assetManager;
        LI("AssetManager ptr=%p",a.am);
        a.texBG  =loadTex(a.am,"menu_bg.png",&a.bgW,&a.bgH);
        a.texLogo=loadTex(a.am,"logo.png",   &a.lgW,&a.lgH);

        /* Sıfırla */
        memset(a.mb,0,sizeof(a.mb));
        memset(a.sb,0,sizeof(a.sb));
        memset(a.cb,0,sizeof(a.cb));
        a.lastF=nowMs();
        a.ready=true;
        LI("READY");
        break;
    }

    case APP_CMD_TERM_WINDOW:
        LI("APP_CMD_TERM_WINDOW");
        a.ready=false;
        if(a.texBG)  {glDeleteTextures(1,&a.texBG);  a.texBG=0;}
        if(a.texLogo){glDeleteTextures(1,&a.texLogo);a.texLogo=0;}
        if(a.cp){glDeleteProgram(a.cp);a.cp=0;}
        if(a.tp){glDeleteProgram(a.tp);a.tp=0;}
        if(a.dpy!=EGL_NO_DISPLAY){
            eglMakeCurrent(a.dpy,EGL_NO_SURFACE,
                           EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if(a.ctx!=EGL_NO_CONTEXT) eglDestroyContext(a.dpy,a.ctx);
            if(a.suf!=EGL_NO_SURFACE) eglDestroySurface(a.dpy,a.suf);
            eglTerminate(a.dpy);
        }
        a.dpy=EGL_NO_DISPLAY;
        a.ctx=EGL_NO_CONTEXT;
        a.suf=EGL_NO_SURFACE;
        break;

    case APP_CMD_WINDOW_RESIZED:
    case APP_CMD_CONFIG_CHANGED:
        if(a.dpy!=EGL_NO_DISPLAY){
            eglQuerySurface(a.dpy,a.suf,EGL_WIDTH, &a.W);
            eglQuerySurface(a.dpy,a.suf,EGL_HEIGHT,&a.H);
            LI("Resize %dx%d",a.W,a.H);
        }
        break;

    default: break;
    }
}

static int32_t onInp(android_app* aapp,AInputEvent* evt){
    App& a=*(App*)aapp->userData;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION) return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float tx=AMotionEvent_getX(evt,0),ty=AMotionEvent_getY(evt,0);
    auto clr=[&](){
        for(auto& b:a.mb)b.pr=false;
        for(auto& b:a.sb)b.pr=false;
        for(auto& b:a.cb)b.pr=false;
    };
    if(act==AMOTION_EVENT_ACTION_DOWN){
        for(auto& b:a.mb)if(hit(b,tx,ty))b.pr=true;
        for(auto& b:a.sb)if(hit(b,tx,ty))b.pr=true;
        for(auto& b:a.cb)if(hit(b,tx,ty))b.pr=true;
    } else if(act==AMOTION_EVENT_ACTION_UP){
        clr(); onTap(a,tx,ty,aapp);
    } else if(act==AMOTION_EVENT_ACTION_CANCEL){ clr(); }
    return 1;
}

/* ════════════════════════════════════════════════════════
 * GİRİŞ NOKTASI
 * ════════════════════════════════════════════════════════ */
void android_main(android_app* aapp){
    App app;
    aapp->userData    =&app;
    aapp->onAppCmd    =onCmd;
    aapp->onInputEvent=onInp;
    LI("android_main basliyor");

    while(!aapp->destroyRequested){
        int ev=0; android_poll_source* src=nullptr;
        int timeout=app.ready?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src)src->process(aapp,src);
            if(aapp->destroyRequested)break;
        }
        if(!app.ready){ continue; }

        /* FPS kısıtlama */
        long now=nowMs();
        long fm=(app.fps==30)?33L:16L;
        if(now-app.lastF<fm){ usleep(1000); continue; }
        app.lastF=now;

        switch(app.scr){
            case S_MENU:     rMenu(app);     break;
            case S_SETTINGS: rSettings(app); break;
            case S_BOOT:     rBoot(app);     break;
            case S_CREDITS:  rCredits(app);  break;
        }
    }
    LI("android_main bitiyor");
}

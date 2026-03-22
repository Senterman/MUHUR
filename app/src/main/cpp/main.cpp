/**
 * MÜHÜR — main.cpp
 * Sinematik giriş (6 sahne, daktilo efekti, skip bar)
 * + Papers Please pikselli ana menü
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

/* ════════════════════════════════════════════════════════
 * RENKLER
 * ════════════════════════════════════════════════════════ */
static const float BG[4]   = {0.051f,0.039f,0.020f,1.f};
static const float GOLD[4] = {0.831f,0.659f,0.325f,1.f};
static const float GDIM[4] = {0.420f,0.330f,0.160f,1.f};
static const float DARK[4] = {0.090f,0.070f,0.035f,1.f};
static const float PRES[4] = {0.180f,0.140f,0.070f,1.f};
static const float GREY[4] = {0.260f,0.240f,0.220f,1.f};
static const float BLK[4]  = {0.000f,0.000f,0.000f,1.f};
static const float WHT[4]  = {0.900f,0.870f,0.780f,1.f};

/* ════════════════════════════════════════════════════════
 * HİKAYE SAHNELERİ
 * ════════════════════════════════════════════════════════ */
struct Scene {
    const char* asset;   /* assets/ klasöründeki dosya adı */
    const char* text;    /* daktilo ile yazılacak metin    */
};

static const Scene SCENES[6] = {
    {
        "story_bg_1.png",
        "YIL 1999. PARALEL TÜRKİYE'DE GÜNLER\n"
        "BİRBİRİNE KARIŞTIKÇA HALK UNUTUYOR:\n"
        "NEYE İNANDIĞINI, KİME OY VERDİĞİNİ,\n"
        "HANGİ BAYRAĞIN ALTINDA YAŞADIĞINI."
    },
    {
        "story_bg_2.png",
        "YENİ BİR DÜZEN İLAN EDİLDİ.\n"
        "BÜYÜK BİNA'NIN KAPILARI AÇILDI.\n"
        "İÇERİDE KOLTUKLARI BEKLEYEN\n"
        "DÖRT FARKLI EL VARDI."
    },
    {
        "story_bg_3.png",
        "BÜROKRASİNİN MASASINDA\n"
        "HER KARAR BİR DAMGAYLA ONAYLANIYOR.\n"
        "SEN O DAMGAYI ELİNDE TUTUYORSUN.\n"
        "PEKİ... KİMİN ADINA?"
    },
    {
        "story_bg_4.png",
        "HALK SORMUYOR, HALK BEKLIYOR.\n"
        "EKMEK İSTİYOR, ADALET İSTİYOR,\n"
        "BAZEN DE SADECE BİR CEVAP.\n"
        "SEN NE VERECEKSİN?"
    },
    {
        "story_bg_5.png",
        "AMA GÖLGEDE BAŞKA SESLEr VAR.\n"
        "FISILTILAR DUVARLARI AŞIYOR.\n"
        "MUHALEFETİN ELİNDE NE VAR?\n"
        "SADECE UMUT — VE SENİN OYUN."
    },
    {
        "story_bg_6.png",
        "İŞTE O MÜHÜR.\n"
        "HER BASIŞINDA BİR KADER DEĞİŞİYOR.\n"
        "HALK, DİN, EKONOMİ, ORDU —\n"
        "DÖRDÜ DE SENİN ELİNDE.\n"
        "HADİ BAŞLAYALIM."
    }
};

/* ════════════════════════════════════════════════════════
 * SHADER
 * ════════════════════════════════════════════════════════ */
static const char* VS =
    "attribute vec2 p; uniform vec2 r;"
    "void main(){"
    "vec2 n=(p/r)*2.0-1.0;"
    "gl_Position=vec4(n.x,-n.y,0.0,1.0);}";

static const char* FS =
    "precision mediump float;"
    "uniform vec4 c;"
    "void main(){gl_FragColor=c;}";

static const char* VS_T =
    "attribute vec2 p; attribute vec2 u; uniform vec2 r; varying vec2 v;"
    "void main(){"
    "vec2 n=(p/r)*2.0-1.0;"
    "gl_Position=vec4(n.x,-n.y,0.0,1.0); v=u;}";

static const char* FS_T =
    "precision mediump float;"
    "uniform sampler2D t; uniform float a; varying vec2 v;"
    "void main(){vec4 c=texture2D(t,v); gl_FragColor=vec4(c.rgb,c.a*a);}";

/* ════════════════════════════════════════════════════════
 * GLOBAL DURUM
 * ════════════════════════════════════════════════════════ */
static EGLDisplay g_dpy  = EGL_NO_DISPLAY;
static EGLSurface g_suf  = EGL_NO_SURFACE;
static EGLContext g_ctx  = EGL_NO_CONTEXT;
static int32_t    g_W    = 0, g_H = 0;
static bool       g_ready = false;
static GLuint     g_cp   = 0, g_tp = 0;
static AAssetManager* g_am = nullptr;

/* Textures */
static GLuint g_scTex[6] = {};   /* sahne arka planları  */
static GLuint g_menuBg   = 0;    /* menü arka planı      */
static int    g_scW[6]   = {}, g_scH[6] = {};

/* Ekran durumu */
enum AppState {
    ST_CINEMA,   /* sinematik giriş */
    ST_MENU,     /* ana menü        */
    ST_SETTINGS, /* ayarlar         */
    ST_BOOT      /* yeni oyun boot  */
};
static AppState g_state = ST_CINEMA;

/* Sinematik durum */
static int   g_scene     = 0;    /* 0-5 */
static float g_typePos   = 0;    /* kaç karakter görünüyor */
static long  g_sceneStart= 0;
static bool  g_skipHeld  = false;
static long  g_skipStart = 0;
static float g_skipBar   = 0;    /* 0-1 */
static bool  g_textDone  = false;

/* Menü */
static float g_bx[4], g_by[4], g_bw[4], g_bh[4];
static bool  g_bp[4] = {};
static float g_sx[3], g_sy[3], g_sw2[3], g_sh2[3];
static int   g_fps   = 60;
static bool  g_sndOn = true;
static long  g_bootStart = 0;
static long  g_lastFrame = 0;

/* ════════════════════════════════════════════════════════
 * ZAMAN
 * ════════════════════════════════════════════════════════ */
static long nowMs() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/* ════════════════════════════════════════════════════════
 * SHADER
 * ════════════════════════════════════════════════════════ */
static GLuint mkSh(GLenum t, const char* s) {
    GLuint h = glCreateShader(t);
    glShaderSource(h, 1, &s, nullptr); glCompileShader(h);
    GLint ok = 0; glGetShaderiv(h, GL_COMPILE_STATUS, &ok);
    if (!ok) { char b[256]={};glGetShaderInfoLog(h,256,nullptr,b);LE("SH:%s",b); }
    return h;
}
static GLuint mkPr(const char* v, const char* f) {
    GLuint p = glCreateProgram();
    GLuint vs = mkSh(GL_VERTEX_SHADER, v);
    GLuint fs = mkSh(GL_FRAGMENT_SHADER, f);
    glAttachShader(p, vs); glAttachShader(p, fs);
    glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    return p;
}

/* ════════════════════════════════════════════════════════
 * PNG DECODE (stored-block)
 * ════════════════════════════════════════════════════════ */
static inline uint32_t u32be(const uint8_t* p) {
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|
           ((uint32_t)p[2]<<8)|(uint32_t)p[3];
}
static uint8_t paeth(uint8_t a,uint8_t b,uint8_t c){
    int pa=abs((int)b-c),pb=abs((int)a-c),pc=abs((int)a+(int)b-2*(int)c);
    return(pa<=pb&&pa<=pc)?a:(pb<=pc)?b:c;
}
static bool inflate0(const uint8_t* s,int sn,uint8_t* d,int dn){
    if(sn<2)return false;
    int p=2,o=0;
    while(p<sn-4&&o<dn){
        bool fin=(s[p]&1)!=0; int bt=(s[p]>>1)&3; p++;
        if(bt==0){
            if(p&1)p++;
            if(p+4>sn)return false;
            uint16_t len=(uint16_t)s[p]|(uint16_t)((uint16_t)s[p+1]<<8); p+=4;
            if(p+len>sn||o+(int)len>dn)return false;
            memcpy(d+o,s+p,len); o+=len; p+=len;
        } else { return false; }
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
            if(bd!=8||ch==0){free(idat);return false;}
        } else if(!strcmp(tp,"IDAT")){
            if(il+cl>ic){ic=il+cl+65536;idat=(uint8_t*)realloc(idat,(size_t)ic);}
            memcpy(idat+il,data+pos,(size_t)cl); il+=cl;
        } else if(!strcmp(tp,"IEND")) end=true;
        pos+=cl+4;
    }
    if(!end||!idat||w==0||h==0){free(idat);return false;}
    int st=w*ch,rs=h*(st+1);
    uint8_t* raw=(uint8_t*)malloc((size_t)rs);
    if(!raw){free(idat);return false;}
    if(!inflate0(idat,il,raw,rs)){free(idat);free(raw);return false;}
    free(idat);
    uint8_t* out=(uint8_t*)malloc((size_t)(w*h*4));
    uint8_t* prev=(uint8_t*)calloc((size_t)st,1);
    if(!out||!prev){free(raw);free(out);free(prev);return false;}
    for(int y=0;y<h;y++){
        uint8_t* row=raw+y*(st+1); uint8_t ft=row[0],*cur=row+1;
        for(int x=0;x<st;x++){
            uint8_t a=(x>=ch)?cur[x-ch]:0,b=prev[x],c2=(x>=ch)?prev[x-ch]:0;
            switch(ft){case 1:cur[x]+=a;break;case 2:cur[x]+=b;break;
                case 3:cur[x]+=(uint8_t)((a+b)/2);break;
                case 4:cur[x]+=paeth(a,b,c2);break;default:break;}
        }
        memcpy(prev,cur,(size_t)st);
        uint8_t* o2=out+y*w*4;
        for(int x=0;x<w;x++){
            o2[x*4]=cur[x*ch]; o2[x*4+1]=cur[x*ch+1];
            o2[x*4+2]=cur[x*ch+2];
            o2[x*4+3]=(ch==4)?cur[x*ch+3]:255;
        }
    }
    free(raw);free(prev);
    *px=out;*W=w;*H=h;return true;
}

static GLuint loadTex(const char* name,int* tw,int* th){
    if(!g_am)return 0;
    AAsset* a=AAssetManager_open(g_am,name,AASSET_MODE_BUFFER);
    if(!a){LI("Asset yok: %s",name);return 0;}
    const uint8_t* buf=(const uint8_t*)AAsset_getBuffer(a);
    int sz=(int)AAsset_getLength(a);
    uint8_t* px=nullptr; int w=0,h=0;
    bool ok=pngDecode(buf,sz,&px,&w,&h);
    AAsset_close(a);
    if(!ok){LI("PNG fail: %s",name);return 0;}
    GLuint tex=0; glGenTextures(1,&tex);
    glBindTexture(GL_TEXTURE_2D,tex);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,px);
    free(px);
    if(tw)*tw=w; if(th)*th=h;
    LI("Tex OK: %s %dx%d id=%u",name,w,h,tex);
    return tex;
}

/* ════════════════════════════════════════════════════════
 * ÇİZİM
 * ════════════════════════════════════════════════════════ */
static void R(float x,float y,float w,float h,const float* col){
    float v[]={x,y,x+w,y,x,y+h,x+w,y+h};
    glUseProgram(g_cp);
    glUniform2f(glGetUniformLocation(g_cp,"r"),(float)g_W,(float)g_H);
    glUniform4fv(glGetUniformLocation(g_cp,"c"),1,col);
    GLuint ap=(GLuint)glGetAttribLocation(g_cp,"p");
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
}

static void T(GLuint tex,float x,float y,float w,float h,float alpha){
    if(!tex)return;
    float v[]={x,y,0,0, x+w,y,1,0, x,y+h,0,1, x+w,y+h,1,1};
    glUseProgram(g_tp);
    glUniform2f(glGetUniformLocation(g_tp,"r"),(float)g_W,(float)g_H);
    glUniform1i(glGetUniformLocation(g_tp,"t"),0);
    glUniform1f(glGetUniformLocation(g_tp,"a"),alpha);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,tex);
    GLuint ap=(GLuint)glGetAttribLocation(g_tp,"p");
    GLuint au=(GLuint)glGetAttribLocation(g_tp,"u");
    glEnableVertexAttribArray(ap); glEnableVertexAttribArray(au);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)0);
    glVertexAttribPointer(au,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap); glDisableVertexAttribArray(au);
}

/* ════════════════════════════════════════════════════════
 * PIXEL FONT  (5×9 segment stili, statik switch)
 * ════════════════════════════════════════════════════════ */
static float gPS = 0;

static void SH(float ox,float oy,float x,float y,float w,const float* c){
    R(ox+x*gPS,oy+y*gPS,w*gPS,gPS,c);
}
static void SV(float ox,float oy,float x,float y,float h,const float* c){
    R(ox+x*gPS,oy+y*gPS,gPS,h*gPS,c);
}
static void DOT(float ox,float oy,float x,float y,const float* c){
    R(ox+x*gPS,oy+y*gPS,gPS,gPS,c);
}

static void drawChar(char ch,float ox,float oy,const float* c){
    switch(ch){
    case 'A':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,4,c);SV(ox,oy,4,1,4,c);
             SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,4,c);SV(ox,oy,4,5,4,c);break;
    case 'B':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);
             SH(ox,oy,0,4,4,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);
             SH(ox,oy,0,8,4,c);break;
    case 'C':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'D':SH(ox,oy,0,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
             SH(ox,oy,0,8,3,c);break;
    case 'E':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);
             SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case 'F':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,4,c);
             SV(ox,oy,0,5,4,c);break;
    case 'G':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SH(ox,oy,2,4,3,c);
             SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case 'H':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SH(ox,oy,1,4,3,c);break;
    case 'I':SH(ox,oy,1,0,3,c);SV(ox,oy,2,1,7,c);SH(ox,oy,1,8,3,c);break;
    case 'J':SH(ox,oy,1,0,3,c);SV(ox,oy,3,1,6,c);SV(ox,oy,0,6,2,c);
             SH(ox,oy,1,8,2,c);break;
    case 'K':SV(ox,oy,0,0,9,c);SH(ox,oy,1,4,3,c);SV(ox,oy,3,0,4,c);
             SV(ox,oy,3,5,4,c);break;
    case 'L':SV(ox,oy,0,0,8,c);SH(ox,oy,1,8,4,c);break;
    case 'M':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);
             DOT(ox,oy,1,1,c);DOT(ox,oy,2,2,c);DOT(ox,oy,3,1,c);break;
    case 'N':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);
             DOT(ox,oy,1,1,c);DOT(ox,oy,2,3,c);DOT(ox,oy,3,5,c);break;
    case 'O':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
             SH(ox,oy,1,8,3,c);break;
    case 'P':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);
             SH(ox,oy,0,4,4,c);break;
    case 'R':SH(ox,oy,0,0,4,c);SV(ox,oy,0,1,8,c);SV(ox,oy,4,1,3,c);
             SH(ox,oy,0,4,4,c);SV(ox,oy,3,5,4,c);break;
    case 'S':SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,3,c);SH(ox,oy,1,4,3,c);
             SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,4,c);break;
    case 'T':SH(ox,oy,0,0,5,c);SV(ox,oy,2,1,8,c);break;
    case 'U':SV(ox,oy,0,0,8,c);SV(ox,oy,4,0,8,c);SH(ox,oy,1,8,3,c);break;
    case 'V':SV(ox,oy,0,0,6,c);SV(ox,oy,4,0,6,c);
             DOT(ox,oy,1,6,c);DOT(ox,oy,3,6,c);DOT(ox,oy,2,7,c);break;
    case 'W':SV(ox,oy,0,0,9,c);SV(ox,oy,4,0,9,c);SV(ox,oy,2,5,4,c);break;
    case 'Y':SV(ox,oy,0,0,4,c);SV(ox,oy,4,0,4,c);SH(ox,oy,1,4,3,c);
             SV(ox,oy,2,5,4,c);break;
    case 'Z':SH(ox,oy,0,0,5,c);DOT(ox,oy,3,2,c);DOT(ox,oy,2,4,c);
             DOT(ox,oy,1,6,c);SH(ox,oy,0,8,5,c);break;
    case '0':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,7,c);SV(ox,oy,4,1,7,c);
             SH(ox,oy,1,8,3,c);break;
    case '1':SV(ox,oy,2,0,9,c);break;
    case '2':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);
             SV(ox,oy,0,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '3':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,3,c);SH(ox,oy,0,4,5,c);
             SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '4':SV(ox,oy,0,0,5,c);SV(ox,oy,4,0,9,c);SH(ox,oy,0,4,5,c);break;
    case '5':SH(ox,oy,0,0,5,c);SV(ox,oy,0,1,3,c);SH(ox,oy,0,4,5,c);
             SV(ox,oy,4,5,3,c);SH(ox,oy,0,8,5,c);break;
    case '6':SH(ox,oy,1,0,4,c);SV(ox,oy,0,1,7,c);SH(ox,oy,1,4,4,c);
             SV(ox,oy,4,5,3,c);SH(ox,oy,1,8,3,c);break;
    case '7':SH(ox,oy,0,0,5,c);SV(ox,oy,4,1,4,c);SV(ox,oy,2,5,4,c);break;
    case '8':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,3,c);
             SH(ox,oy,1,4,3,c);SV(ox,oy,0,5,3,c);SV(ox,oy,4,5,3,c);
             SH(ox,oy,1,8,3,c);break;
    case '9':SH(ox,oy,1,0,3,c);SV(ox,oy,0,1,3,c);SV(ox,oy,4,1,7,c);
             SH(ox,oy,1,4,3,c);SH(ox,oy,1,8,3,c);break;
    case ':':DOT(ox,oy,2,2,c);DOT(ox,oy,2,6,c);break;
    case '-':SH(ox,oy,1,4,3,c);break;
    case '.':DOT(ox,oy,2,8,c);break;
    case '!':SV(ox,oy,2,0,6,c);DOT(ox,oy,2,8,c);break;
    case ',':DOT(ox,oy,2,8,c);DOT(ox,oy,1,9,c);break;
    case '\'':DOT(ox,oy,2,0,c);DOT(ox,oy,2,1,c);break;
    default: break;
    }
}

static int charCount(const char* s){
    int n=0; const unsigned char* p=(const unsigned char*)s;
    while(*p){ if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++;}else p++; n++; }
    return n;
}

static void drawStr(const char* s,float x,float y,float ps,const float* col){
    gPS=ps; float cx=x;
    const unsigned char* p=(const unsigned char*)s;
    while(*p){
        if(*p=='\n'){ y+=12*ps; cx=x; p++; continue; }
        if(*p>=0x80){p++;if(*p&&(*p&0xC0)==0x80)p++; cx+=7*ps; continue;}
        char ch=(char)*p;
        if(ch>='a'&&ch<='z')ch=ch-'a'+'A';
        drawChar(ch,cx,y,col);
        cx+=7*ps; p++;
    }
}
static void drawStrC(const char* s,float cy,float ps,const float* col){
    int n=0; const char* p=s;
    while(*p&&*p!='\n'){ n++; p++; }
    float w=(float)n*7*ps;
    drawStr(s,(float)g_W*.5f-w*.5f,cy,ps,col);
}

/* ════════════════════════════════════════════════════════
 * SİNEMATİK RENDER
 * ════════════════════════════════════════════════════════ */
static void renderCinema(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);

    /* Arka plan */
    glClearColor(BLK[0],BLK[1],BLK[2],1); glClear(GL_COLOR_BUFFER_BIT);
    int si=g_scene;
    if(g_scTex[si]) T(g_scTex[si],0,0,sw,sh,1.f);
    /* Karartma overlay */
    float ov[4]={0,0,0,.52f}; R(0,0,sw,sh,ov);

    /* Tarama çizgileri */
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) R(0,y,sw,1.5f,sc);

    /* Alt metin kutusu */
    float bpy=sh*.68f, bph=sh*.24f;
    float boxBg[4]={0,0,0,.72f};
    R(0,bpy-sh*.01f,sw,bph+sh*.02f,boxBg);
    R(0,bpy-sh*.012f,sw,sh*.003f,GOLD);

    /* Daktilo metni — sadece g_typePos kadar karakter göster */
    const char* fullText = SCENES[si].text;
    int total = charCount(fullText);
    int show  = (int)g_typePos;
    if(show > total) show = total;

    /* Geçici buffer */
    char buf[512] = {};
    int cnt=0; const unsigned char* src=(const unsigned char*)fullText;
    unsigned char* dst=(unsigned char*)buf;
    while(*src && cnt<show){
        if(*src=='\n'){ *dst++='\n'; src++; cnt++; continue; }
        if(*src>=0x80){
            *dst++=*src++;
            if(*src&&(*src&0xC0)==0x80)*dst++=*src++;
        } else { *dst++=*src++; }
        cnt++;
    }

    float tps=sw*.009f;
    drawStr(buf,sw*.06f,bpy+sh*.02f,tps,WHT);

    /* İmleç yanıp sönme */
    if(!g_textDone && (nowMs()/400)%2==0)
        R(sw*.06f+(float)show*7*tps,bpy+sh*.02f,tps,9*tps,GOLD);

    /* Sahne numarası */
    char sn[8]; snprintf(sn,8,"%d/%d",si+1,6);
    float snps=sw*.007f;
    drawStr(sn,sw*.90f,sh*.015f,snps,GDIM);

    /* "SKIP" butonu — sağ üst */
    float skw=sw*.18f,skh=sh*.055f;
    float skx=sw-skw-sw*.04f, sky=sh*.018f;
    float skbg[4]={0,0,0,.60f};
    R(skx,sky,skw,skh,skbg);
    R(skx,sky+skh-sh*.003f,skw,sh*.003f,g_skipBar>0?GOLD:GDIM);
    drawStrC("SKIP",sky+(skh-9*snps)*.5f,snps,g_skipBar>0?GOLD:GDIM);

    /* Skip dolum barı (yatay) */
    if(g_skipBar>0)
        R(skx,sky+skh-sh*.003f,skw*g_skipBar,sh*.003f,GOLD);

    /* "DEVAM" / "İLERLE" — sadece metin bitince — sağ alt */
    if(g_textDone){
        float nxw=sw*.28f,nxh=sh*.060f;
        float nxx=sw-nxw-sw*.04f, nxy=sh*.905f;
        R(nxx-sw*.004f,nxy-sh*.003f,nxw+sw*.008f,nxh+sh*.006f,GOLD);
        float nxbg[4]={BG[0],BG[1],BG[2],1.f}; R(nxx,nxy,nxw,nxh,nxbg);
        drawStrC("DEVAM",nxy+(nxh-9*snps)*.5f,snps,GOLD);
    }

    eglSwapBuffers(g_dpy,g_suf);
}

/* ════════════════════════════════════════════════════════
 * BUTON ÇİZ
 * ════════════════════════════════════════════════════════ */
static void drawBtn(float x,float y,float w,float h,
                    const char* lbl,bool en,bool pr,float ps){
    float bw=(float)g_W*.005f;
    const float* gc=en?GOLD:GREY;
    float bc[4]={gc[0],gc[1],gc[2],en?1.f:.35f};
    R(x-bw,y-bw,w+2*bw,h+2*bw,bc);
    R(x,y,w,h,pr?PRES:DARK);
    float cs=(float)g_W*.010f;
    R(x,y,cs,cs,bc); R(x+w-cs,y,cs,cs,bc);
    R(x,y+h-cs,cs,cs,bc); R(x+w-cs,y+h-cs,cs,cs,bc);
    float lc[4]={gc[0],gc[1],gc[2],en?1.f:.4f};
    float tw=(float)charCount(lbl)*7*ps;
    drawStr(lbl,x+(w-tw)*.5f,y+(h-9*ps)*.5f,ps,lc);
}

/* ════════════════════════════════════════════════════════
 * ANA MENÜ RENDER
 * ════════════════════════════════════════════════════════ */
static void renderMenu(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);

    /* Arka plan: son sahne görseli (g_scTex[5] veya menuBg) */
    GLuint bg=g_menuBg?g_menuBg:(g_scTex[5]?g_scTex[5]:0);
    if(bg) T(bg,0,0,sw,sh,.65f);
    float ov[4]={BG[0],BG[1],BG[2],.55f}; R(0,0,sw,sh,ov);

    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) R(0,y,sw,1.5f,sc);

    R(0,sh*.010f,sw,sh*.004f,GOLD);
    R(0,sh*.974f,sw,sh*.004f,GOLD);

    /* Başlık */
    float ps=sw*.011f;
    drawStrC("MUHUR",sh*.055f,ps,GOLD);

    /* Motto */
    R(sw*.08f,sh*.155f,sw*.84f,sh*.002f,GDIM);
    drawStrC("KADERIN MUHRUNUN UCUNDA",sh*.165f,sw*.007f,GDIM);
    R(sw*.08f,sh*.220f,sw*.84f,sh*.002f,GOLD);

    /* Butonlar */
    float bw=sw*.72f,bh=sh*.080f;
    float bx=(sw-bw)*.5f,gap=sh*.022f,sy=sh*.250f,bp=sw*.013f;
    const char* lb[4]={"YENİ OYUN","DEVAM ET","AYARLAR","CIKIS"};
    bool en[4]={true,false,true,true};
    for(int i=0;i<4;i++){
        float fy=sy+(float)i*(bh+gap);
        g_bx[i]=bx; g_by[i]=fy; g_bw[i]=bw; g_bh[i]=bh;
        drawBtn(bx,fy,bw,bh,lb[i],en[i],g_bp[i],bp);
    }

    drawStrC("BONECASTOFFICIAL",sh*.935f,sw*.007f,GDIM);
    drawStr("V0.1",sw*.04f,sh*.953f,sw*.007f,GDIM);
}

/* ════════════════════════════════════════════════════════
 * AYARLAR
 * ════════════════════════════════════════════════════════ */
static void renderSettings(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(BG[0],BG[1],BG[2],1); glClear(GL_COLOR_BUFFER_BIT);
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) R(0,y,sw,1.5f,sc);
    R(0,sh*.010f,sw,sh*.004f,GOLD);
    float ps=sw*.011f;
    drawStrC("AYARLAR",sh*.055f,ps,GOLD);
    R(sw*.10f,sh*.155f,sw*.80f,sh*.003f,GDIM);
    float bw=sw*.70f,bh=sh*.080f,bx=(sw-bw)*.5f,bp=sw*.013f;
    /* SES */
    char sesLbl[32]; snprintf(sesLbl,32,"SES %s",g_sndOn?"ACIK":"KAPALI");
    g_sx[0]=bx;g_sy[0]=sh*.22f;g_sw2[0]=bw;g_sh2[0]=bh;
    drawBtn(bx,sh*.22f,bw,bh,sesLbl,true,false,bp);
    /* FPS60 */
    if(g_fps==60){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.18f};R(bx,sh*.33f,bw,bh,hl);}
    g_sx[1]=bx;g_sy[1]=sh*.33f;g_sw2[1]=bw;g_sh2[1]=bh;
    drawBtn(bx,sh*.33f,bw,bh,"FPS 60",true,false,bp);
    /* FPS30 */
    if(g_fps==30){float hl[4]={GOLD[0],GOLD[1],GOLD[2],.18f};R(bx,sh*.44f,bw,bh,hl);}
    g_sx[2]=bx;g_sy[2]=sh*.44f;g_sw2[2]=bw;g_sh2[2]=bh;
    drawBtn(bx,sh*.44f,bw,bh,"FPS 30",true,false,bp);
    /* GERİ */
    float geri_x=bx,geri_y=sh*.62f;
    drawBtn(geri_x,geri_y,bw,bh,"GERI",true,false,bp);
    /* geri hit alanı — sb[3] olarak sakla */
    g_sx[2]=bx; /* override: geri için sx[2] */
    /* Not: geri btn sb[3] değil ayrı kontrol */
    char ft[32]; snprintf(ft,32,"HEDEF %d FPS",g_fps);
    drawStrC(ft,sh*.74f,sw*.007f,GDIM);
}

/* ════════════════════════════════════════════════════════
 * BOOT
 * ════════════════════════════════════════════════════════ */
static void renderBoot(){
    float sw=(float)g_W, sh=(float)g_H;
    glViewport(0,0,g_W,g_H);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
    long el=nowMs()-g_bootStart;
    float prog=fminf((float)el/2000.f,1.f);
    float bx=sw*.15f,by=sh*.52f,bw2=sw*.70f,bh2=sh*.018f;
    R(bx,by,bw2,bh2,DARK); R(bx,by,bw2*prog,bh2,GOLD);
    drawStrC("SISTEM BASLATILIYOR",sh*.42f,sw*.009f,GOLD);
    float sc[4]={0,0,0,.10f};
    for(float y=0;y<sh;y+=4.f) R(0,y,sw,1.5f,sc);
    if(el>2200) g_state=ST_MENU;
}

/* ════════════════════════════════════════════════════════
 * SİNEMATİK GÜNCELLEME
 * ════════════════════════════════════════════════════════ */
static void updateCinema(){
    const char* fullText=SCENES[g_scene].text;
    int total=charCount(fullText);

    /* Daktilo hızı: ~18 karakter/saniye */
    long elapsed=nowMs()-g_sceneStart;
    float target=(float)elapsed/55.f; /* ms per char */
    if(target>(float)total){ target=(float)total; g_textDone=true; }
    else g_textDone=false;
    g_typePos=target;

    /* Skip barı güncelle */
    if(g_skipHeld){
        long held=nowMs()-g_skipStart;
        g_skipBar=fminf((float)held/3000.f,1.f);
        if(g_skipBar>=1.f){
            /* Tüm sinematiği atla */
            g_scene=6;
        }
    } else {
        g_skipBar*=0.85f; /* yavaşça sıfırla */
    }
}

static void nextScene(){
    g_scene++;
    if(g_scene>=6){
        g_state=ST_MENU;
        g_scene=0;
    } else {
        g_sceneStart=nowMs();
        g_typePos=0;
        g_textDone=false;
    }
}

/* ════════════════════════════════════════════════════════
 * DOKUNMA
 * ════════════════════════════════════════════════════════ */
static bool hitR(float bx,float by,float bw,float bh,float tx,float ty){
    return tx>=bx&&tx<=bx+bw&&ty>=by&&ty<=by+bh;
}
static bool hitBtn(int i,float tx,float ty){
    return hitR(g_bx[i],g_by[i],g_bw[i],g_bh[i],tx,ty);
}

static void onDown(float tx,float ty,android_app* aapp){
    (void)aapp;
    if(g_state==ST_CINEMA){
        float sw=(float)g_W, sh=(float)g_H;
        float skw=sw*.18f,skh=sh*.055f;
        float skx=sw-skw-sw*.04f, sky=sh*.018f;
        if(hitR(skx,sky,skw,skh,tx,ty)){
            g_skipHeld=true; g_skipStart=nowMs();
        }
    }
}

static void onUp(float tx,float ty,android_app* aapp){
    if(g_state==ST_CINEMA){
        float sw=(float)g_W, sh=(float)g_H;
        float skw=sw*.18f,skh=sh*.055f;
        float skx=sw-skw-sw*.04f, sky=sh*.018f;
        if(hitR(skx,sky,skw,skh,tx,ty)&&!g_skipHeld){
            /* tek tık skip göstermez, sadece hold */
        }
        g_skipHeld=false;
        /* DEVAM butonu */
        if(g_textDone){
            float nxw=sw*.28f,nxh=sh*.060f;
            float nxx=sw-nxw-sw*.04f, nxy=sh*.905f;
            if(hitR(nxx,nxy,nxw,nxh,tx,ty)) nextScene();
        }
        /* Ekrana tıklayınca da devam (metin bitmişse) */
        if(g_textDone) nextScene();
        return;
    }
    if(g_state==ST_MENU){
        memset(g_bp,0,sizeof(g_bp));
        if(hitBtn(0,tx,ty)){ g_state=ST_BOOT; g_bootStart=nowMs(); }
        else if(hitBtn(2,tx,ty)) g_state=ST_SETTINGS;
        else if(hitBtn(3,tx,ty)) ANativeActivity_finish(aapp->activity);
        return;
    }
    if(g_state==ST_SETTINGS){
        float bw=(float)g_W*.70f,bh=(float)g_H*.080f;
        float bx=((float)g_W-bw)*.5f;
        if(hitR(g_sx[0],g_sy[0],g_sw2[0],g_sh2[0],tx,ty)) g_sndOn=!g_sndOn;
        else if(hitR(g_sx[1],g_sy[1],g_sw2[1],g_sh2[1],tx,ty)) g_fps=60;
        else if(hitR(g_sx[2],g_sy[2],g_sw2[2],g_sh2[2],tx,ty)) g_fps=30;
        /* Geri butonu */
        else if(hitR(bx,(float)g_H*.62f,bw,bh,tx,ty)) g_state=ST_MENU;
        return;
    }
}

/* ════════════════════════════════════════════════════════
 * ANDROID CALLBACKS
 * ════════════════════════════════════════════════════════ */
static void onCmd(android_app* aapp,int32_t cmd){
    switch(cmd){
    case APP_CMD_INIT_WINDOW:{
        LI("=== INIT_WINDOW ===");
        if(!aapp->window){LE("win null");break;}

        g_dpy=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if(g_dpy==EGL_NO_DISPLAY){LE("getDisp fail");break;}
        EGLint ma=0,mi=0;
        if(!eglInitialize(g_dpy,&ma,&mi)){LE("eglInit fail");break;}
        LI("EGL v%d.%d",ma,mi);

        const EGLint att[]={
            EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
        EGLConfig cfg; EGLint nc=0;
        if(!eglChooseConfig(g_dpy,att,&cfg,1,&nc)||nc==0){LE("cfg fail");break;}

        g_suf=eglCreateWindowSurface(g_dpy,cfg,aapp->window,nullptr);
        if(g_suf==EGL_NO_SURFACE){LE("surf fail 0x%x",eglGetError());break;}

        const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
        g_ctx=eglCreateContext(g_dpy,cfg,EGL_NO_CONTEXT,ca);
        if(g_ctx==EGL_NO_CONTEXT){LE("ctx fail 0x%x",eglGetError());break;}

        if(eglMakeCurrent(g_dpy,g_suf,g_suf,g_ctx)==EGL_FALSE){
            LE("makeCur fail 0x%x",eglGetError());break;}

        eglQuerySurface(g_dpy,g_suf,EGL_WIDTH, &g_W);
        eglQuerySurface(g_dpy,g_suf,EGL_HEIGHT,&g_H);
        LI("surf %dx%d",g_W,g_H);

        /* Aşama 1: renk */
        glViewport(0,0,g_W,g_H);
        glClearColor(BG[0],BG[1],BG[2],1);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(g_dpy,g_suf);
        LI("ASAMA1 OK");

        /* Shader */
        g_cp=mkPr(VS,FS); g_tp=mkPr(VS_T,FS_T);
        if(!g_cp||!g_tp){LE("prog fail");break;}
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        LI("ASAMA2 shader OK");

        /* Assets */
        g_am=aapp->activity->assetManager;
        LI("AM=%p",g_am);
        for(int i=0;i<6;i++)
            g_scTex[i]=loadTex(SCENES[i].asset,&g_scW[i],&g_scH[i]);
        g_menuBg=loadTex("menu_bg.png",nullptr,nullptr);

        memset(g_bp,0,sizeof(g_bp));
        g_state=ST_CINEMA;
        g_scene=0;
        g_sceneStart=nowMs();
        g_typePos=0;
        g_textDone=false;
        g_skipHeld=false;
        g_skipBar=0;
        g_lastFrame=nowMs();
        g_ready=true;
        LI("=== HAZIR ===");
        break;
    }
    case APP_CMD_TERM_WINDOW:
        LI("TERM_WINDOW");
        g_ready=false;
        for(int i=0;i<6;i++){if(g_scTex[i]){glDeleteTextures(1,&g_scTex[i]);g_scTex[i]=0;}}
        if(g_menuBg){glDeleteTextures(1,&g_menuBg);g_menuBg=0;}
        if(g_cp){glDeleteProgram(g_cp);g_cp=0;}
        if(g_tp){glDeleteProgram(g_tp);g_tp=0;}
        if(g_dpy!=EGL_NO_DISPLAY){
            eglMakeCurrent(g_dpy,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if(g_ctx!=EGL_NO_CONTEXT)eglDestroyContext(g_dpy,g_ctx);
            if(g_suf!=EGL_NO_SURFACE)eglDestroySurface(g_dpy,g_suf);
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
    default:break;
    }
}

static int32_t onInp(android_app* aapp,AInputEvent* evt){
    if(!g_ready)return 0;
    if(AInputEvent_getType(evt)!=AINPUT_EVENT_TYPE_MOTION)return 0;
    int32_t act=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
    float tx=AMotionEvent_getX(evt,0),ty=AMotionEvent_getY(evt,0);
    if(act==AMOTION_EVENT_ACTION_DOWN){
        onDown(tx,ty,aapp);
        if(g_state==ST_MENU)
            for(int i=0;i<4;i++) g_bp[i]=hitBtn(i,tx,ty);
    } else if(act==AMOTION_EVENT_ACTION_UP){
        memset(g_bp,0,sizeof(g_bp));
        onUp(tx,ty,aapp);
        if(g_state==ST_CINEMA) g_skipHeld=false;
    } else if(act==AMOTION_EVENT_ACTION_CANCEL){
        memset(g_bp,0,sizeof(g_bp));
        g_skipHeld=false;
    }
    return 1;
}

/* ════════════════════════════════════════════════════════
 * GİRİŞ NOKTASI
 * ════════════════════════════════════════════════════════ */
void android_main(android_app* aapp){
    LI("android_main START");
    aapp->userData=nullptr;
    aapp->onAppCmd=onCmd;
    aapp->onInputEvent=onInp;

    while(!aapp->destroyRequested){
        int ev=0; android_poll_source* src=nullptr;
        int timeout=g_ready?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src)src->process(aapp,src);
            if(aapp->destroyRequested)break;
        }
        if(!g_ready)continue;

        long now=nowMs();
        long fm=(g_fps==30)?33L:16L;
        if(now-g_lastFrame<fm){usleep(2000);continue;}
        g_lastFrame=now;

        if(g_state==ST_CINEMA){ updateCinema(); renderCinema(); }
        else if(g_state==ST_MENU)    renderMenu();
        else if(g_state==ST_SETTINGS)renderSettings();
        else if(g_state==ST_BOOT)    renderBoot();

        eglSwapBuffers(g_dpy,g_suf);
    }
    LI("android_main END");
}

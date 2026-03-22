/**
 * MÜHÜR — main.cpp
 * Ana Menü: Arka plan, logo, motto, 5 buton, dokunma yönetimi
 * Saf C++ · Android Native Activity · OpenGL ES 2.0
 */

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <cmath>
#include <cstring>
#include <cstdlib>

#define TAG  "MUHUR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════
// RENK PALETİ
// ═══════════════════════════════════════════════════════════
#define COL_BG_R      0.051f  // #0d0a05
#define COL_BG_G      0.039f
#define COL_BG_B      0.020f

#define COL_GOLD_R    0.831f  // #d4a853
#define COL_GOLD_G    0.659f
#define COL_GOLD_B    0.325f

#define COL_GOLD2_R   0.600f  // koyu altın (disabled)
#define COL_GOLD2_G   0.460f
#define COL_GOLD2_B   0.200f

#define COL_BTN_R     0.090f  // buton arka planı
#define COL_BTN_G     0.070f
#define COL_BTN_B     0.040f

#define COL_BTN_HL_R  0.140f  // hover/basılı
#define COL_BTN_HL_G  0.110f
#define COL_BTN_HL_B  0.060f

// ═══════════════════════════════════════════════════════════
// SHADER
// ═══════════════════════════════════════════════════════════
static const char* VERT_COL = R"(
    attribute vec2 aPos;
    uniform   vec2 uRes;
    void main(){
        vec2 n = (aPos / uRes) * 2.0 - 1.0;
        gl_Position = vec4(n.x, -n.y, 0.0, 1.0);
    }
)";

static const char* FRAG_COL = R"(
    precision mediump float;
    uniform vec4 uColor;
    void main(){ gl_FragColor = uColor; }
)";

// Texture shader
static const char* VERT_TEX = R"(
    attribute vec2 aPos;
    attribute vec2 aUV;
    uniform   vec2 uRes;
    varying   vec2 vUV;
    void main(){
        vec2 n = (aPos / uRes) * 2.0 - 1.0;
        gl_Position = vec4(n.x, -n.y, 0.0, 1.0);
        vUV = aUV;
    }
)";

static const char* FRAG_TEX = R"(
    precision mediump float;
    uniform sampler2D uTex;
    uniform float     uAlpha;
    varying vec2 vUV;
    void main(){
        vec4 c = texture2D(uTex, vUV);
        gl_FragColor = vec4(c.rgb, c.a * uAlpha);
    }
)";

// ═══════════════════════════════════════════════════════════
// SHADER YARDIMCILARI
// ═══════════════════════════════════════════════════════════
static GLuint compileShader(GLenum type, const char* src){
    GLuint s = glCreateShader(type);
    glShaderSource(s,1,&src,nullptr);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
    if(!ok){ char b[512]; glGetShaderInfoLog(s,512,nullptr,b); LOGE("Shader: %s",b); }
    return s;
}
static GLuint linkProgram(const char* v, const char* f){
    GLuint p = glCreateProgram();
    GLuint vs = compileShader(GL_VERTEX_SHADER,   v);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, f);
    glAttachShader(p,vs); glAttachShader(p,fs);
    glLinkProgram(p);
    glDeleteShader(vs); glDeleteShader(fs);
    return p;
}

// ═══════════════════════════════════════════════════════════
// MİNİMAL PNG DECODER  (stb_image olmadan, sadece stored/deflate-none)
// Gerçek build için stb_image.h önerilir — burası placeholder olarak çalışır.
// ═══════════════════════════════════════════════════════════
static inline uint32_t u32be(const uint8_t* p){
    return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|((uint32_t)p[2]<<8)|(uint32_t)p[3];
}

// Paeth predictor
static inline uint8_t paeth(uint8_t a, uint8_t b, uint8_t c){
    int pa=abs((int)b-c), pb=abs((int)a-c), pc=abs((int)a+(int)b-2*(int)c);
    return (pa<=pb&&pa<=pc)?a:(pb<=pc)?b:c;
}

// zlib stored-block inflate (deflate PNG'ler için stb_image kullanın)
static bool zInflate(const uint8_t* src, int sLen, uint8_t* dst, int dLen){
    if(sLen<2) return false;
    int pos=2, out=0; // skip CMF+FLG
    while(pos<sLen-4 && out<dLen){
        uint8_t bfinal = src[pos]&1;
        uint8_t btype  = (src[pos]>>1)&3;
        pos++;
        if(btype==0){
            if(pos&1) pos++; // byte align
            if(pos+4>sLen) return false;
            uint16_t len=(uint16_t)src[pos]|(uint16_t)(src[pos+1]<<8); pos+=4;
            if(pos+len>sLen||out+len>dLen) return false;
            memcpy(dst+out, src+pos, len); out+=len; pos+=len;
        } else {
            LOGE("PNG: deflate bloklar desteklenmiyor. assets/logo.png ve menu_bg.png icin"
                 " PNG kaydetme ayarinda 'compression=none' veya stb_image kullanin.");
            return false;
        }
        if(bfinal) break;
    }
    return out==dLen;
}

static bool decodePNG(const uint8_t* data, int size,
                      uint8_t** outPx, int* outW, int* outH){
    static const uint8_t SIG[8]={137,80,78,71,13,10,26,10};
    if(size<8||memcmp(data,SIG,8)!=0) return false;

    int pos=8, w=0, h=0, ch=0;
    uint8_t* idat=nullptr; int idatLen=0, idatCap=0;
    bool gotIHDR=false, gotIEND=false;

    while(pos+12<=size && !gotIEND){
        int  cLen=(int)u32be(data+pos); pos+=4;
        char cType[5]; memcpy(cType,data+pos,4); cType[4]=0; pos+=4;

        if(!strcmp(cType,"IHDR")){
            w=(int)u32be(data+pos); h=(int)u32be(data+pos+4);
            int bd=data[pos+8], ct=data[pos+9];
            ch=(ct==6)?4:(ct==2)?3:0;
            if(bd!=8||ch==0){ LOGE("PNG: sadece RGB/RGBA 8bit desteklenir"); return false; }
            gotIHDR=true;
        } else if(!strcmp(cType,"IDAT")){
            if(idatLen+cLen>idatCap){
                idatCap=idatLen+cLen+65536;
                idat=(uint8_t*)realloc(idat,idatCap);
            }
            memcpy(idat+idatLen, data+pos, cLen); idatLen+=cLen;
        } else if(!strcmp(cType,"IEND")){
            gotIEND=true;
        }
        pos+=cLen+4;
    }
    if(!gotIHDR||!gotIEND||!idat){ free(idat); return false; }

    int stride=w*ch;
    int rawSz=h*(stride+1);
    uint8_t* raw=(uint8_t*)malloc(rawSz);
    if(!zInflate(idat,idatLen,raw,rawSz)){
        free(idat); free(raw); return false;
    }
    free(idat);

    uint8_t* px=(uint8_t*)malloc(w*h*4);
    uint8_t* prev=(uint8_t*)calloc(stride,1);
    for(int y=0;y<h;y++){
        uint8_t* row=raw+y*(stride+1);
        uint8_t  ft=row[0];
        uint8_t* cur=row+1;
        for(int x=0;x<stride;x++){
            uint8_t a=(x>=ch)?cur[x-ch]:0, b=prev[x], c=(x>=ch)?prev[x-ch]:0;
            switch(ft){
                case 1: cur[x]+=a; break;
                case 2: cur[x]+=b; break;
                case 3: cur[x]+=(uint8_t)((a+b)/2); break;
                case 4: cur[x]+=paeth(a,b,c); break;
            }
        }
        memcpy(prev,cur,stride);
        uint8_t* out=px+y*w*4;
        for(int x=0;x<w;x++){
            out[x*4+0]=cur[x*ch+0];
            out[x*4+1]=cur[x*ch+1];
            out[x*4+2]=cur[x*ch+2];
            out[x*4+3]=(ch==4)?cur[x*ch+3]:255;
        }
    }
    free(raw); free(prev);
    *outPx=px; *outW=w; *outH=h;
    return true;
}

// ═══════════════════════════════════════════════════════════
// TEXTURE YÜKLE
// ═══════════════════════════════════════════════════════════
static GLuint loadTexture(AAssetManager* am, const char* path, int* tw, int* th){
    AAsset* a=AAssetManager_open(am,path,AASSET_MODE_BUFFER);
    if(!a){ LOGE("Asset bulunamadi: %s",path); return 0; }
    const uint8_t* buf=(const uint8_t*)AAsset_getBuffer(a);
    int sz=(int)AAsset_getLength(a);
    uint8_t* px=nullptr; int w=0,h=0;
    bool ok=decodePNG(buf,sz,&px,&w,&h);
    AAsset_close(a);
    if(!ok){ LOGE("PNG decode hatasi: %s",path); return 0; }
    GLuint tex; glGenTextures(1,&tex);
    glBindTexture(GL_TEXTURE_2D,tex);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,px);
    free(px);
    if(tw) *tw=w; if(th) *th=h;
    LOGI("Texture: %s (%dx%d) id=%u",path,w,h,tex);
    return tex;
}

// ═══════════════════════════════════════════════════════════
// ÇİZİM YARDIMCILARI
// ═══════════════════════════════════════════════════════════
static void drawRect(GLuint prog, float x, float y, float w, float h,
                     float r, float g, float b, float a,
                     float sw, float sh){
    float v[]={x,y, x+w,y, x,y+h, x+w,y+h};
    glUseProgram(prog);
    GLint ap=glGetAttribLocation(prog,"aPos");
    glUniform2f(glGetUniformLocation(prog,"uRes"),sw,sh);
    glUniform4f(glGetUniformLocation(prog,"uColor"),r,g,b,a);
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
}

static void drawTexture(GLuint prog, GLuint tex,
                        float x, float y, float w, float h,
                        float alpha, float sw, float sh){
    float v[]={
        x,   y,   0,0,
        x+w, y,   1,0,
        x,   y+h, 0,1,
        x+w, y+h, 1,1
    };
    glUseProgram(prog);
    GLint ap=glGetAttribLocation(prog,"aPos");
    GLint au=glGetAttribLocation(prog,"aUV");
    glUniform2f(glGetUniformLocation(prog,"uRes"),sw,sh);
    glUniform1i(glGetUniformLocation(prog,"uTex"),0);
    glUniform1f(glGetUniformLocation(prog,"uAlpha"),alpha);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D,tex);
    glEnableVertexAttribArray(ap);
    glEnableVertexAttribArray(au);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)0);
    glVertexAttribPointer(au,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(ap);
    glDisableVertexAttribArray(au);
}

static void drawCircle(GLuint prog,float cx,float cy,float R,
                       float r,float g,float b,float sw,float sh){
    const int S=64;
    float v[(S+2)*2];
    v[0]=cx; v[1]=cy;
    for(int i=0;i<=S;i++){
        float a=2.0f*3.14159f*i/S;
        v[(i+1)*2]  =cx+cosf(a)*R;
        v[(i+1)*2+1]=cy+sinf(a)*R;
    }
    glUseProgram(prog);
    GLint ap=glGetAttribLocation(prog,"aPos");
    glUniform2f(glGetUniformLocation(prog,"uRes"),sw,sh);
    glUniform4f(glGetUniformLocation(prog,"uColor"),r,g,b,1.0f);
    glEnableVertexAttribArray(ap);
    glVertexAttribPointer(ap,2,GL_FLOAT,GL_FALSE,0,v);
    glDrawArrays(GL_TRIANGLE_FAN,0,S+2);
    glDisableVertexAttribArray(ap);
}

// ═══════════════════════════════════════════════════════════
// MENÜ DURUMU
// ═══════════════════════════════════════════════════════════
enum Screen { SCR_MENU, SCR_SETTINGS, SCR_CREDITS };

struct Button {
    float x, y, w, h;
    const char* label;
    bool enabled;
    bool pressed;
};

struct GameState {
    bool  hasSave    = false;
    bool  soundOn    = true;
    Screen screen    = SCR_MENU;
};

// ═══════════════════════════════════════════════════════════
// UYGULAMA YAPISI
// ═══════════════════════════════════════════════════════════
struct App {
    // EGL
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    int32_t    W=0, H=0;
    bool       ready=false;

    // Shaderlar
    GLuint colProg=0, texProg=0;

    // Textureler
    GLuint texBG=0,  texLogo=0;
    int    bgW=0,bgH=0, logoW=0,logoH=0;

    // Asset manager
    AAssetManager* am=nullptr;

    // Oyun durumu
    GameState gs;

    // Menü butonları (koordinatlar render sırasında hesaplanır)
    Button btns[5];
    int    btnCount=0;

    // Ayarlar ekranı butonları
    Button sBtns[2]; // [0]=Ses, [1]=Geri
};

// ═══════════════════════════════════════════════════════════
// EGL
// ═══════════════════════════════════════════════════════════
static bool eglInit(App& app, ANativeWindow* win){
    app.display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(app.display,nullptr,nullptr);
    const EGLint att[]={
        EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,
        EGL_NONE
    };
    EGLConfig cfg; EGLint n;
    eglChooseConfig(app.display,att,&cfg,1,&n);
    app.surface=eglCreateWindowSurface(app.display,cfg,win,nullptr);
    const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
    app.context=eglCreateContext(app.display,cfg,EGL_NO_CONTEXT,ca);
    eglMakeCurrent(app.display,app.surface,app.surface,app.context);
    eglQuerySurface(app.display,app.surface,EGL_WIDTH, &app.W);
    eglQuerySurface(app.display,app.surface,EGL_HEIGHT,&app.H);
    LOGI("EGL %dx%d",app.W,app.H);
    return true;
}
static void eglTerm(App& app){
    if(app.display!=EGL_NO_DISPLAY){
        eglMakeCurrent(app.display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
        if(app.context!=EGL_NO_CONTEXT) eglDestroyContext(app.display,app.context);
        if(app.surface!=EGL_NO_SURFACE) eglDestroySurface(app.display,app.surface);
        eglTerminate(app.display);
    }
    app.display=EGL_NO_DISPLAY; app.context=EGL_NO_CONTEXT; app.surface=EGL_NO_SURFACE;
}

// ═══════════════════════════════════════════════════════════
// GL BAŞLAT
// ═══════════════════════════════════════════════════════════
static void glInit(App& app){
    app.colProg=linkProgram(VERT_COL,FRAG_COL);
    app.texProg=linkProgram(VERT_TEX,FRAG_TEX);
    // Textureler
    app.texBG  =loadTexture(app.am,"menu_bg.png", &app.bgW,   &app.bgH);
    app.texLogo=loadTexture(app.am,"logo.png",     &app.logoW, &app.logoH);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
}

// ═══════════════════════════════════════════════════════════
// BUTON HIT-TEST
// ═══════════════════════════════════════════════════════════
static bool hitBtn(const Button& b, float tx, float ty){
    return b.enabled && tx>=b.x && tx<=b.x+b.w && ty>=b.y && ty<=b.y+b.h;
}

// ═══════════════════════════════════════════════════════════
// BUTON ÇİZ
// ═══════════════════════════════════════════════════════════
static void drawButton(App& app, const Button& b, float sw, float sh){
    float br = b.pressed  ? COL_BTN_HL_R : COL_BTN_R;
    float bg = b.pressed  ? COL_BTN_HL_G : COL_BTN_G;
    float bb = b.pressed  ? COL_BTN_HL_B : COL_BTN_B;

    float gr = b.enabled  ? COL_GOLD_R : COL_GOLD2_R;
    float gg = b.enabled  ? COL_GOLD_G : COL_GOLD2_G;
    float gb = b.enabled  ? COL_GOLD_B : COL_GOLD2_B;

    float bord = 2.0f;

    // Altın kenarlık
    drawRect(app.colProg,
             b.x-bord, b.y-bord, b.w+bord*2, b.h+bord*2,
             gr, gg, gb, b.enabled?0.9f:0.4f, sw, sh);

    // Buton gövdesi
    drawRect(app.colProg,
             b.x, b.y, b.w, b.h,
             br, bg, bb, 0.95f, sw, sh);

    // Etiket yerine ince altın şerit (metin render olmadan görsel ipucu)
    float lh = b.h * 0.08f;
    drawRect(app.colProg,
             b.x + b.w*0.1f, b.y + b.h*0.5f - lh*0.5f,
             b.w * 0.8f, lh,
             gr, gg, gb, b.enabled?0.7f:0.3f, sw, sh);
}

// ═══════════════════════════════════════════════════════════
// ANA MENÜ RENDER
// ═══════════════════════════════════════════════════════════
static void renderMenu(App& app){
    float sw=(float)app.W, sh=(float)app.H;

    glViewport(0,0,app.W,app.H);
    glClearColor(COL_BG_R,COL_BG_G,COL_BG_B,1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // ── Arka plan ────────────────────────────────────────
    if(app.texBG){
        drawTexture(app.texProg,app.texBG, 0,0,sw,sh, 1.0f,sw,sh);
        // Üzerine koyu overlay (okunabilirlik)
        drawRect(app.colProg,0,0,sw,sh, COL_BG_R,COL_BG_G,COL_BG_B,0.55f,sw,sh);
    }

    // ── Üst altın şerit ──────────────────────────────────
    drawRect(app.colProg,0,sh*0.01f,sw,sh*0.004f,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,1.0f,sw,sh);

    // ── Logo ─────────────────────────────────────────────
    float logoAreaH = sh * 0.22f;
    float logoAreaY = sh * 0.04f;

    if(app.texLogo && app.logoW>0){
        float ar = (float)app.logoW/(float)app.logoH;
        float lh = logoAreaH;
        float lw = lh * ar;
        if(lw > sw*0.7f){ lw=sw*0.7f; lh=lw/ar; }
        float lx = (sw-lw)*0.5f;
        float ly = logoAreaY + (logoAreaH-lh)*0.5f;
        drawTexture(app.texProg,app.texLogo,lx,ly,lw,lh,1.0f,sw,sh);
    } else {
        // Logo yoksa mühür amblemi
        float cx=sw*0.5f, cy=logoAreaY+logoAreaH*0.5f, R=sw*0.10f;
        drawCircle(app.colProg,cx,cy,R,       COL_GOLD_R, COL_GOLD_G, COL_GOLD_B, sw,sh);
        drawCircle(app.colProg,cx,cy,R*0.78f, COL_BG_R,   COL_BG_G,   COL_BG_B,   sw,sh);
        drawCircle(app.colProg,cx,cy,R*0.22f, COL_GOLD_R, COL_GOLD_G, COL_GOLD_B, sw,sh);
    }

    // ── Motto çizgisi (logo altı) ─────────────────────────
    // "Kaderin, mührünün ucunda." — metin yoksa dekoratif çizgi
    float mottoY = logoAreaY + logoAreaH + sh*0.010f;
    drawRect(app.colProg,
             sw*0.25f, mottoY, sw*0.50f, sh*0.002f,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,0.6f,sw,sh);
    // Motto nokta işareti (merkez altın nokta)
    float dotR = sw*0.012f;
    drawCircle(app.colProg,sw*0.5f,mottoY+sh*0.014f,dotR,
               COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,sw,sh);

    // ── Menü Butonları ────────────────────────────────────
    float btnW  = sw * 0.72f;
    float btnH  = sh * 0.068f;
    float btnX  = (sw - btnW) * 0.5f;
    float gap   = sh * 0.018f;
    float startY= sh * 0.38f;

    const char* labels[] = {
        "YENİ OYUN", "DEVAM ET", "AYARLAR", "GELİŞTİRİCİLER", "ÇIK"
    };
    bool enabled[] = {
        true, app.gs.hasSave, true, true, true
    };

    app.btnCount=5;
    for(int i=0;i<5;i++){
        app.btns[i].x       = btnX;
        app.btns[i].y       = startY + i*(btnH+gap);
        app.btns[i].w       = btnW;
        app.btns[i].h       = btnH;
        app.btns[i].label   = labels[i];
        app.btns[i].enabled = enabled[i];
        drawButton(app, app.btns[i], sw, sh);
    }

    // ── Alt çizgi ─────────────────────────────────────────
    drawRect(app.colProg,0,sh*0.98f,sw,sh*0.004f,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,1.0f,sw,sh);

    eglSwapBuffers(app.display,app.surface);
}

// ═══════════════════════════════════════════════════════════
// AYARLAR EKRANI RENDER
// ═══════════════════════════════════════════════════════════
static void renderSettings(App& app){
    float sw=(float)app.W, sh=(float)app.H;

    glViewport(0,0,app.W,app.H);
    glClearColor(COL_BG_R,COL_BG_G,COL_BG_B,1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    if(app.texBG){
        drawTexture(app.texProg,app.texBG,0,0,sw,sh,1.0f,sw,sh);
        drawRect(app.colProg,0,0,sw,sh,COL_BG_R,COL_BG_G,COL_BG_B,0.75f,sw,sh);
    }

    // Panel
    float px=sw*0.08f, py=sh*0.20f, pw=sw*0.84f, ph=sh*0.60f;
    drawRect(app.colProg,px,py,pw,ph, COL_BTN_R,COL_BTN_G,COL_BTN_B,0.95f,sw,sh);
    drawRect(app.colProg,px,py,pw,sh*0.003f, COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,1.0f,sw,sh);
    drawRect(app.colProg,px,py+ph,pw,sh*0.003f, COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,1.0f,sw,sh);

    // Ses butonu
    float bw=sw*0.55f, bh=sh*0.07f;
    float bx=(sw-bw)*0.5f, by=py+ph*0.25f;
    app.sBtns[0]={bx,by,bw,bh,"SES",true,false};

    // Ses durumu göstergesi
    float ind = app.gs.soundOn ? COL_GOLD_R : 0.3f;
    float indG= app.gs.soundOn ? COL_GOLD_G : 0.3f;
    float indB= app.gs.soundOn ? COL_GOLD_B : 0.3f;
    drawRect(app.colProg,bx-2,by-2,bw+4,bh+4, ind,indG,indB,0.9f,sw,sh);
    drawRect(app.colProg,bx,by,bw,bh, COL_BTN_R,COL_BTN_G,COL_BTN_B,0.95f,sw,sh);

    // ON/OFF çizgisi
    float filled = app.gs.soundOn ? bw*0.8f : bw*0.2f;
    drawRect(app.colProg,bx+bw*0.1f,by+bh*0.46f,filled,bh*0.08f,
             ind,indG,indB,0.8f,sw,sh);

    // Geri butonu
    float gbx=(sw-bw)*0.5f, gby=py+ph*0.65f;
    app.sBtns[1]={gbx,gby,bw,bh,"GERİ",true,false};
    drawRect(app.colProg,gbx-2,gby-2,bw+4,bh+4,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,0.7f,sw,sh);
    drawRect(app.colProg,gbx,gby,bw,bh,
             COL_BTN_R,COL_BTN_G,COL_BTN_B,0.95f,sw,sh);
    drawRect(app.colProg,gbx+bw*0.1f,gby+bh*0.46f,bw*0.8f,bh*0.08f,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,0.6f,sw,sh);

    eglSwapBuffers(app.display,app.surface);
}

// ═══════════════════════════════════════════════════════════
// GELİŞTİRİCİLER EKRANI
// ═══════════════════════════════════════════════════════════
static void renderCredits(App& app){
    float sw=(float)app.W, sh=(float)app.H;
    glViewport(0,0,app.W,app.H);
    glClearColor(COL_BG_R,COL_BG_G,COL_BG_B,1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if(app.texBG){
        drawTexture(app.texProg,app.texBG,0,0,sw,sh,1.0f,sw,sh);
        drawRect(app.colProg,0,0,sw,sh,COL_BG_R,COL_BG_G,COL_BG_B,0.80f,sw,sh);
    }
    // Dekoratif çizgiler
    for(int i=0;i<4;i++){
        float y=sh*(0.30f+i*0.10f);
        drawRect(app.colProg,sw*0.15f,y,sw*0.70f,sh*0.002f,
                 COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,0.4f-(i*0.08f),sw,sh);
    }
    // Geri (ekranın alt kısmı)
    float bw=sw*0.5f,bh=sh*0.07f;
    float bx=(sw-bw)*0.5f,by=sh*0.82f;
    app.sBtns[1]={bx,by,bw,bh,"GERİ",true,false};
    drawRect(app.colProg,bx-2,by-2,bw+4,bh+4,
             COL_GOLD_R,COL_GOLD_G,COL_GOLD_B,0.7f,sw,sh);
    drawRect(app.colProg,bx,by,bw,bh,
             COL_BTN_R,COL_BTN_G,COL_BTN_B,0.95f,sw,sh);
    eglSwapBuffers(app.display,app.surface);
}

// ═══════════════════════════════════════════════════════════
// DOKUNMA YÖNETİMİ
// ═══════════════════════════════════════════════════════════
static void handleTouch(App& app, float tx, float ty, android_app* aapp){
    if(app.gs.screen==SCR_MENU){
        for(int i=0;i<app.btnCount;i++){
            if(hitBtn(app.btns[i],tx,ty)){
                LOGI("Buton basıldı: %s",app.btns[i].label);
                switch(i){
                    case 0: // Yeni Oyun
                        LOGI(">>> YENİ OYUN — sinematik giriş tetiklendi");
                        // TODO: sinematik modüle geç
                        break;
                    case 1: // Devam Et
                        if(app.gs.hasSave) LOGI(">>> DEVAM ET");
                        break;
                    case 2: // Ayarlar
                        app.gs.screen=SCR_SETTINGS;
                        break;
                    case 3: // Geliştiriciler
                        app.gs.screen=SCR_CREDITS;
                        break;
                    case 4: // Çık
                        ANativeActivity_finish(aapp->activity);
                        break;
                }
                return;
            }
        }
    } else if(app.gs.screen==SCR_SETTINGS){
        if(hitBtn(app.sBtns[0],tx,ty)){
            app.gs.soundOn=!app.gs.soundOn;
            LOGI("Ses: %s",app.gs.soundOn?"AÇIK":"KAPALI");
        } else if(hitBtn(app.sBtns[1],tx,ty)){
            app.gs.screen=SCR_MENU;
        }
    } else if(app.gs.screen==SCR_CREDITS){
        if(hitBtn(app.sBtns[1],tx,ty))
            app.gs.screen=SCR_MENU;
    }
}

// ═══════════════════════════════════════════════════════════
// ANDROID CALLBACK'LER
// ═══════════════════════════════════════════════════════════
static void onCmd(android_app* aapp, int32_t cmd){
    App& app=*(App*)aapp->userData;
    switch(cmd){
        case APP_CMD_INIT_WINDOW:
            eglInit(app,aapp->window);
            app.am=aapp->activity->assetManager;
            glInit(app);
            app.ready=true;
            LOGI("Pencere hazır");
            break;
        case APP_CMD_TERM_WINDOW:
            app.ready=false;
            if(app.texBG)   glDeleteTextures(1,&app.texBG);
            if(app.texLogo) glDeleteTextures(1,&app.texLogo);
            if(app.colProg) glDeleteProgram(app.colProg);
            if(app.texProg) glDeleteProgram(app.texProg);
            eglTerm(app);
            break;
        case APP_CMD_WINDOW_RESIZED:
        case APP_CMD_CONFIG_CHANGED:
            eglQuerySurface(app.display,app.surface,EGL_WIDTH, &app.W);
            eglQuerySurface(app.display,app.surface,EGL_HEIGHT,&app.H);
            break;
        default: break;
    }
}

static int32_t onInput(android_app* aapp, AInputEvent* evt){
    App& app=*(App*)aapp->userData;
    if(AInputEvent_getType(evt)==AINPUT_EVENT_TYPE_MOTION){
        int32_t action=AMotionEvent_getAction(evt)&AMOTION_EVENT_ACTION_MASK;
        float tx=AMotionEvent_getX(evt,0);
        float ty=AMotionEvent_getY(evt,0);
        if(action==AMOTION_EVENT_ACTION_DOWN){
            // Basılı görünüm
            if(app.gs.screen==SCR_MENU)
                for(int i=0;i<app.btnCount;i++)
                    if(hitBtn(app.btns[i],tx,ty)) app.btns[i].pressed=true;
        } else if(action==AMOTION_EVENT_ACTION_UP){
            for(int i=0;i<app.btnCount;i++) app.btns[i].pressed=false;
            handleTouch(app,tx,ty,aapp);
        } else if(action==AMOTION_EVENT_ACTION_CANCEL){
            for(int i=0;i<app.btnCount;i++) app.btns[i].pressed=false;
        }
        return 1;
    }
    return 0;
}

// ═══════════════════════════════════════════════════════════
// GİRİŞ NOKTASI
// ═══════════════════════════════════════════════════════════
void android_main(android_app* aapp){
    App app;
    aapp->userData    =&app;
    aapp->onAppCmd    =onCmd;
    aapp->onInputEvent=onInput;
    LOGI("MÜHÜR basliyor — 'Kaderin, mührünün ucunda.'");

    while(!aapp->destroyRequested){
        int ev; android_poll_source* src;
        int timeout=app.ready?0:-1;
        while(ALooper_pollOnce(timeout,nullptr,&ev,(void**)&src)>=0){
            if(src) src->process(aapp,src);
            if(aapp->destroyRequested) break;
        }
        if(app.ready){
            switch(app.gs.screen){
                case SCR_MENU:     renderMenu(app);     break;
                case SCR_SETTINGS: renderSettings(app); break;
                case SCR_CREDITS:  renderCredits(app);  break;
            }
        }
    }
    LOGI("MÜHÜR kapaniyor");
}

package com.muhur.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/**
 * MÜHÜR — GameView
 * GLSurfaceView alt sınıfı.
 * EGL yapılandırması: Alpha=0 (Casper cihazında siyah ekran önlemi)
 * Touch olayları queueEvent ile renderer thread'ine iletilir.
 */
public class GameView extends GLSurfaceView {

    private final GameRenderer mRenderer;

    public GameView(Context context) {
        super(context);

        // OpenGL ES 2.0
        setEGLContextClientVersion(2);

        /*
         * KRITIK: setEGLConfigChooser(8,8,8, 0,0,0)
         *   R=8  G=8  B=8  → 24-bit renk
         *   A=0            → Alpha YOK (opaque surface)
         *   Depth=0        → Derinlik tamponu yok (2D oyun)
         *   Stencil=0      → Stencil tamponu yok
         *
         *   Alpha=0 olmadan Casper VIA X30'da yüzey şeffaf kalıyor
         *   ve sistem arka planı (amber/siyah) görünüyor.
         */
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);

        // Renderer oluştur ve ata
        mRenderer = new GameRenderer(context);
        setRenderer(mRenderer);

        // Sürekli render modu — onDrawFrame her frame çağrılır
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int action = event.getActionMasked();
        final long time = event.getEventTime();

        /*
         * KRITIK: Touch olayları mutlaka queueEvent ile iletilmeli.
         * Direkt renderer metodunu çağırmak thread-safe değil.
         * queueEvent, komutu GL thread kuyruğuna ekler.
         */
        queueEvent(() -> mRenderer.onTouch(action, x, y, time));

        return true; // olayı tüket
    }
}

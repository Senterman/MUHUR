package com.muhur.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/**
 * MÜHÜR — GameView  (Revizyon 3)
 *
 * Değişiklik:
 *   - Constructor artık dışarıdan bir GameRenderer alır.
 *     GameActivity, Renderer'ı MusicController ile birlikte oluşturur
 *     ve buraya geçirir. GameView kendi Renderer oluşturmuyor.
 *
 * Korunanlar:
 *   - setEGLConfigChooser(8,8,8,0,0,0) — Casper VIA X30 zorunluluğu
 *   - queueEvent touch yönlendirmesi — thread safety
 */
public class GameView extends GLSurfaceView {

    private final GameRenderer mRenderer;

    public GameView(Context context, GameRenderer renderer) {
        super(context);

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

        mRenderer = renderer;
        setRenderer(mRenderer);

        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x      = event.getX();
        final float y      = event.getY();
        final int action   = event.getActionMasked();
        final long time    = event.getEventTime();

        /*
         * KRITIK: Touch olayları mutlaka queueEvent ile iletilmeli.
         * Direkt renderer metodunu çağırmak thread-safe değil.
         */
        queueEvent(() -> mRenderer.onTouch(action, x, y, time));

        return true;
    }
}

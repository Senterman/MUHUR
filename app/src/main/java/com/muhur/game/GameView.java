package com.muhur.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/**
 * MÜHÜR — GameView (Revizyon 9)
 * Değişmedi — Rev3'ten aynı.
 */
public class GameView extends GLSurfaceView {

    private final GameRenderer mRenderer;

    public GameView(Context context, GameRenderer renderer) {
        super(context);
        setEGLContextClientVersion(2);
        // KRİTİK: Casper VIA X30 için alpha=0 zorunlu
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        mRenderer = renderer;
        setRenderer(mRenderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x    = event.getX();
        final float y    = event.getY();
        final int action = event.getActionMasked();
        final long time  = event.getEventTime();
        // KRİTİK: Thread safety için queueEvent
        queueEvent(() -> mRenderer.onTouch(action, x, y, time));
        return true;
    }
}

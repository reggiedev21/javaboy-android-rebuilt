package javaboy.android;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import javaboy.core.GraphicsChip;

/** Affiche le tampon 160×144 du cœur dans une SurfaceView, mis à l'échelle en conservant le
 *  ratio 10:9 (entier quand possible, pour des pixels nets). */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private final Bitmap bitmap = Bitmap.createBitmap(GraphicsChip.WIDTH, GraphicsChip.HEIGHT, Bitmap.Config.ARGB_8888);
    private final Paint paint = new Paint();
    private final Rect dst = new Rect();
    private volatile boolean surfaceReady = false;
    private volatile boolean smooth = false;

    public GameView(Context c, AttributeSet a) {
        super(c, a);
        getHolder().addCallback(this);
        paint.setFilterBitmap(false);
    }

    public void setSmoothScaling(boolean on) { smooth = on; paint.setFilterBitmap(on); }

    /** Appelé depuis le thread d'émulation avec le tampon du cœur (copié immédiatement). */
    public void draw(int[] pixels) {
        bitmap.setPixels(pixels, 0, GraphicsChip.WIDTH, 0, 0, GraphicsChip.WIDTH, GraphicsChip.HEIGHT);
        if (!surfaceReady) return;
        Canvas canvas = getHolder().lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(0xFF101418);
            canvas.drawBitmap(bitmap, null, dst, paint);
        } finally {
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void computeDst(int w, int h) {
        int scale = Math.max(1, Math.min(w / GraphicsChip.WIDTH, h / GraphicsChip.HEIGHT));
        int dw = GraphicsChip.WIDTH * scale, dh = GraphicsChip.HEIGHT * scale;
        // en portrait, l'écran est placé en haut pour laisser la place à la manette
        int top = (h > w) ? Math.min(h / 12, (h - dh) / 2) : (h - dh) / 2;
        dst.set((w - dw) / 2, top, (w - dw) / 2 + dw, top + dh);
    }

    /** Zone occupée par l'écran Game Boy (la manette évite cette zone). */
    public Rect screenRect() { return new Rect(dst); }

    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { computeDst(w, h); surfaceReady = true; }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; }
}

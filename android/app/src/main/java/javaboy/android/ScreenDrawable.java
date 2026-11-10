package javaboy.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import javaboy.core.GraphicsChip;

import java.util.function.Consumer;

/** Image 160×144 du cœur, dessinée par-dessus l'écran LCD d'une coque (premier plan de la vue
 *  {@code ecraninterne}), centrée et au ratio 10:9. Le fond de la vue reste visible autour.
 *
 *  <p>{@link #accept} est appelé par le thread d'émulation ; le bitmap n'est touché que par
 *  le thread principal, dans {@link #draw}. */
final class ScreenDrawable extends Drawable implements Consumer<int[]> {
    private static final int W = GraphicsChip.WIDTH, H = GraphicsChip.HEIGHT;

    private final View host;
    private final int[] pending = new int[W * H];
    private boolean dirty;                       // protégé par le verrou de `pending`
    private boolean hasFrame;
    private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Paint paint = new Paint();
    private final Rect dst = new Rect();
    private boolean smooth;

    ScreenDrawable(View host) {
        this.host = host;
        paint.setFilterBitmap(false);
    }

    /** Net (échelle entière, pixels carrés) ou lissé (toute la place disponible). */
    void setSmoothScaling(boolean on) { smooth = on; paint.setFilterBitmap(on); host.invalidate(); }

    @Override public void accept(int[] pixels) {
        synchronized (pending) {
            System.arraycopy(pixels, 0, pending, 0, pending.length);
            dirty = true;
        }
        host.postInvalidateOnAnimation();
    }

    @Override public void draw(Canvas canvas) {
        synchronized (pending) {
            if (dirty) {
                bitmap.setPixels(pending, 0, W, 0, 0, W, H);
                dirty = false;
                hasFrame = true;
            }
        }
        if (!hasFrame) return;
        Rect b = getBounds();
        float f = Math.min(b.width() / (float) W, b.height() / (float) H);
        int dw = Math.round(W * f), dh = Math.round(H * f);
        int left = b.left + (b.width() - dw) / 2, top = b.top + (b.height() - dh) / 2;
        dst.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(bitmap, null, dst, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

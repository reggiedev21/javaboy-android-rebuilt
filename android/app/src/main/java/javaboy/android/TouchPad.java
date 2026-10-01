package javaboy.android;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import javaboy.core.Joypad;

import java.util.EnumSet;
import java.util.function.BiConsumer;

/** Manette tactile.
 *
 *  <ul>
 *   <li>Multitouch : chaque pointeur est résolu indépendamment (courir + sauter, diagonales).</li>
 *   <li>Croix directionnelle à 8 directions avec zone morte centrale et diagonales tolérantes
 *       (angle de 60° par direction).</li>
 *   <li>Zones de toucher plus larges que le dessin des boutons (cible ≥ 48 dp).</li>
 *   <li>Retour haptique court à l'appui.</li>
 *   <li>Transparence réglable ; disposition recalculée à chaque redimensionnement
 *       (portrait : sous l'écran, paysage : de part et d'autre).</li>
 *  </ul>
 *  La vue ne connaît pas l'émulateur : elle publie des changements d'état via un listener. */
public final class TouchPad extends View {
    private BiConsumer<Joypad.Button, Boolean> listener = (b, p) -> { };
    private final EnumSet<Joypad.Button> pressed = EnumSet.noneOf(Joypad.Button.class);
    private final EnumSet<Joypad.Button> scratch = EnumSet.noneOf(Joypad.Button.class);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dpad = new RectF(), aBtn = new RectF(), bBtn = new RectF(), start = new RectF(), select = new RectF();
    private float dpadDeadZone;
    private int opacity = 140;
    private boolean haptics = true;
    private Rect screenRect = new Rect();
    private final float density;

    public TouchPad(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(14 * density);
        text.setFakeBoldText(true);
    }

    public void setListener(BiConsumer<Joypad.Button, Boolean> l) { listener = l; }
    public void setOpacity(int alpha0to255) { opacity = alpha0to255; invalidate(); }
    public void setHaptics(boolean on) { haptics = on; }
    /** Zone de l'écran Game Boy à éviter (fournie par GameView). */
    public void setScreenRect(Rect r) { screenRect = r; layoutButtons(getWidth(), getHeight()); invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutButtons(w, h); }

    private void layoutButtons(int w, int h) {
        if (w == 0 || h == 0) return;
        boolean portrait = h > w;
        float margin = 16 * density;
        float dpadSize = Math.min(w, h) * (portrait ? 0.40f : 0.42f);
        float btn = Math.max(48 * density, dpadSize * 0.36f);
        float top = portrait ? Math.max(screenRect.bottom + margin, h * 0.50f) : margin;
        float zoneH = h - top - margin;
        float cy = top + zoneH * 0.45f;

        dpad.set(margin, cy - dpadSize / 2, margin + dpadSize, cy + dpadSize / 2);
        dpadDeadZone = dpadSize * 0.12f;

        float ax = w - margin - btn, bx = ax - btn * 1.15f;
        aBtn.set(ax, cy - btn * 0.85f, ax + btn, cy + btn * 0.15f);
        bBtn.set(bx, cy - btn * 0.35f, bx + btn, cy + btn * 0.65f);

        float pillW = btn * 1.3f, pillH = btn * 0.5f;
        float py = Math.min(h - margin - pillH, cy + dpadSize / 2 + margin);
        select.set(w / 2f - pillW - 8 * density, py, w / 2f - 8 * density, py + pillH);
        start.set(w / 2f + 8 * density, py, w / 2f + 8 * density + pillW, py + pillH);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        scratch.clear();
        int action = ev.getActionMasked();
        int skip = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL)
                ? ev.getActionIndex() : -1;
        if (action != MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (i == skip) continue;
                resolve(ev.getX(i), ev.getY(i), scratch);
            }
        }
        boolean anyNew = false;
        for (Joypad.Button b : Joypad.Button.values()) {
            boolean now = scratch.contains(b), before = pressed.contains(b);
            if (now != before) {
                listener.accept(b, now);
                if (now) anyNew = true;
            }
        }
        pressed.clear();
        pressed.addAll(scratch);
        if (anyNew && haptics) vibrate();
        invalidate();
        return true;
    }

    /** Traduit un point de contact en boutons (zones de toucher élargies de 25 %). */
    private void resolve(float x, float y, EnumSet<Joypad.Button> out) {
        if (hit(dpad, x, y, 0.15f)) {
            float dx = x - dpad.centerX(), dy = y - dpad.centerY();
            if (Math.hypot(dx, dy) < dpadDeadZone) return;
            double angle = Math.toDegrees(Math.atan2(-dy, dx)); // 0° = droite, 90° = haut
            if (angle < 0) angle += 360;
            // secteurs de 60° centrés sur les axes, avec recouvrement pour les diagonales
            if (angle < 60 || angle > 300) out.add(Joypad.Button.RIGHT);
            if (angle > 30 && angle < 150) out.add(Joypad.Button.UP);
            if (angle > 120 && angle < 240) out.add(Joypad.Button.LEFT);
            if (angle > 210 && angle < 330) out.add(Joypad.Button.DOWN);
            return;
        }
        if (hit(aBtn, x, y, 0.25f)) out.add(Joypad.Button.A);
        if (hit(bBtn, x, y, 0.25f)) out.add(Joypad.Button.B);
        if (hit(start, x, y, 0.25f)) out.add(Joypad.Button.START);
        if (hit(select, x, y, 0.25f)) out.add(Joypad.Button.SELECT);
    }

    private static boolean hit(RectF r, float x, float y, float grow) {
        float gx = r.width() * grow, gy = r.height() * grow;
        return x >= r.left - gx && x <= r.right + gx && y >= r.top - gy && y <= r.bottom + gy;
    }

    private void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) { v.vibrate(VibrationEffect.createOneShot(12, 80)); return; }
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override protected void onDraw(Canvas c) {
        if (dpad.isEmpty()) return;
        // croix
        float cx = dpad.centerX(), cy = dpad.centerY(), arm = dpad.width() / 3f, half = arm / 2f;
        drawKey(c, new RectF(cx - half, dpad.top, cx + half, dpad.bottom), pressed.contains(Joypad.Button.UP) || pressed.contains(Joypad.Button.DOWN), "");
        drawKey(c, new RectF(dpad.left, cy - half, dpad.right, cy + half), pressed.contains(Joypad.Button.LEFT) || pressed.contains(Joypad.Button.RIGHT), "");
        drawArrow(c, cx, dpad.top + arm * 0.4f, 0, pressed.contains(Joypad.Button.UP));
        drawArrow(c, cx, dpad.bottom - arm * 0.4f, 180, pressed.contains(Joypad.Button.DOWN));
        drawArrow(c, dpad.left + arm * 0.4f, cy, 270, pressed.contains(Joypad.Button.LEFT));
        drawArrow(c, dpad.right - arm * 0.4f, cy, 90, pressed.contains(Joypad.Button.RIGHT));
        // boutons ronds
        drawRound(c, aBtn, pressed.contains(Joypad.Button.A), "A");
        drawRound(c, bBtn, pressed.contains(Joypad.Button.B), "B");
        // start / select
        drawKey(c, start, pressed.contains(Joypad.Button.START), "START");
        drawKey(c, select, pressed.contains(Joypad.Button.SELECT), "SELECT");
    }

    private void paintsFor(boolean down) {
        fill.setColor(down ? 0xFF8BAC0F : 0xFFDDDDDD);
        fill.setAlpha(down ? Math.min(255, opacity + 80) : opacity);
        stroke.setColor(0xFF000000); stroke.setAlpha(opacity);
        text.setColor(0xFF000000); text.setAlpha(Math.min(255, opacity + 60));
    }

    private void drawKey(Canvas c, RectF r, boolean down, String label) {
        paintsFor(down);
        float rad = 8 * density;
        c.drawRoundRect(r, rad, rad, fill);
        c.drawRoundRect(r, rad, rad, stroke);
        if (!label.isEmpty()) c.drawText(label, r.centerX(), r.centerY() + text.getTextSize() / 3, text);
    }

    private void drawRound(Canvas c, RectF r, boolean down, String label) {
        paintsFor(down);
        c.drawOval(r, fill);
        c.drawOval(r, stroke);
        c.drawText(label, r.centerX(), r.centerY() + text.getTextSize() / 3, text);
    }

    private void drawArrow(Canvas c, float x, float y, float deg, boolean down) {
        paintsFor(down);
        Path p = new Path();
        float s = 6 * density;
        p.moveTo(0, -s); p.lineTo(s, s * 0.6f); p.lineTo(-s, s * 0.6f); p.close();
        c.save(); c.translate(x, y); c.rotate(deg);
        c.drawPath(p, text);
        c.restore();
    }
}

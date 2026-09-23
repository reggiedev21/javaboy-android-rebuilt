package javaboy.core;

/** État de la manette. L'hôte appelle {@link #press}/{@link #release} ; le cœur lit
 *  {@link #isPressed}. Les écritures sont volatiles car l'hôte et le thread d'émulation
 *  ne sont pas le même. */
public final class Joypad {
    public enum Button { UP, DOWN, LEFT, RIGHT, A, B, START, SELECT }

    private volatile int state; // un bit par bouton, ordre de l'enum

    public void press(Button b)   { state |= (1 << b.ordinal()); }
    public void release(Button b) { state &= ~(1 << b.ordinal()); }
    public void set(Button b, boolean pressed) { if (pressed) press(b); else release(b); }
    public boolean isPressed(Button b) { return (state & (1 << b.ordinal())) != 0; }
    public void releaseAll() { state = 0; }
}

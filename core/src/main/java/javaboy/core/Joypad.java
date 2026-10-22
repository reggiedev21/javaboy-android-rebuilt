package javaboy.core;

import java.util.concurrent.atomic.AtomicInteger;

/** État de la manette, partagé entre l'hôte et le thread d'émulation.
 *
 *  <p>L'hôte appelle {@link #press}/{@link #release} depuis n'importe quel thread (UI, manette
 *  Bluetooth…) : les mises à jour sont atomiques, sans verrou.
 *
 *  <p>Le cœur ne lit pas cet état directement : au début de chaque image il en prend un
 *  instantané ({@link #poll}), qui reste fixe pendant toute l'image. Un appui plus court
 *  qu'une image (tap rapide) est mémorisé et vu pendant au moins une image : il n'est jamais
 *  perdu. Une image se comporte donc de façon déterministe vis-à-vis des entrées. */
public final class Joypad {
    public enum Button { UP, DOWN, LEFT, RIGHT, A, B, START, SELECT }

    /** État réel tenu par l'hôte : un bit par bouton, ordre de l'enum. */
    private final AtomicInteger state = new AtomicInteger();
    /** Boutons enfoncés depuis le dernier {@link #poll}, même s'ils ont été relâchés depuis. */
    private final AtomicInteger pressedSincePoll = new AtomicInteger();
    /** Instantané de l'image courante : lu et écrit uniquement par le thread d'émulation. */
    private int frameState;

    private static int bit(Button b) { return 1 << b.ordinal(); }

    // ------------------------------------------------------------ côté hôte (tout thread)

    public void press(Button b) {
        int m = bit(b);
        state.getAndAccumulate(m, (s, x) -> s | x);
        pressedSincePoll.getAndAccumulate(m, (s, x) -> s | x);
    }

    public void release(Button b) { state.getAndAccumulate(~bit(b), (s, x) -> s & x); }

    public void set(Button b, boolean pressed) { if (pressed) press(b); else release(b); }

    /** État tel que l'hôte l'a fixé (pas forcément encore vu par le jeu). */
    public boolean isPressed(Button b) { return (state.get() & bit(b)) != 0; }

    /** Relâche tout, y compris les appuis pas encore vus (perte de focus, pause). */
    public void releaseAll() { state.set(0); pressedSincePoll.set(0); }

    // ------------------------------------------------------------ côté cœur (thread d'émulation)

    /** Fige l'état pour l'image qui commence.
     *  @return les boutons nouvellement enfoncés par rapport à l'image précédente
     *          (sert à lever l'interruption joypad). */
    int poll() {
        int latched = pressedSincePoll.getAndSet(0);
        int s = state.get() | latched;
        int newlyPressed = s & ~frameState;
        frameState = s;
        return newlyPressed;
    }

    /** Bouton enfoncé dans l'instantané de l'image courante (lu par {@link IoHandler}). */
    boolean isPressedInFrame(Button b) { return (frameState & bit(b)) != 0; }
}

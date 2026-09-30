package javaboy.android;

import javaboy.core.Emulator;

/** Boucle d'émulation dans un thread dédié : une image toutes les 16,74 ms, pause/reprise
 *  sans perdre l'état (cycle de vie Android). */
public final class EmulationThread extends Thread {
    private final Emulator emulator;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private volatile int speedPercent = 100;
    private volatile int fps;

    public EmulationThread(Emulator emulator) {
        super("emulation");
        this.emulator = emulator;
        setDaemon(true);
    }

    public void setPaused(boolean p) { paused = p; synchronized (this) { notifyAll(); } }
    public void setSpeedPercent(int percent) { speedPercent = Math.max(25, Math.min(400, percent)); }
    public int getFps() { return fps; }
    public void shutdown() { running = false; emulator.stop(); setPaused(false); interrupt(); }

    @Override public void run() {
        long next = System.nanoTime();
        int frames = 0;
        long lastFps = System.currentTimeMillis();
        while (running) {
            if (paused) {
                synchronized (this) { try { while (paused && running) wait(); } catch (InterruptedException e) { return; } }
                next = System.nanoTime();
                continue;
            }
            emulator.runFrame();
            frames++;
            long frameNanos = 1_000_000_000L * 100 / (60L * speedPercent);
            next += frameNanos;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); } catch (InterruptedException e) { return; }
            } else if (wait < -100_000_000L) {
                next = System.nanoTime(); // en retard de plus de 100 ms : on ne rattrape pas
            }
            long now = System.currentTimeMillis();
            if (now - lastFps >= 1000) { fps = frames; frames = 0; lastFps = now; }
        }
    }
}

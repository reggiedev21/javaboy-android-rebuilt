package javaboy.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Accès à l'émulateur depuis plusieurs threads, comme le fait l'app Android
 *  (thread d'émulation + thread UI + éventuellement manette Bluetooth). */
class ThreadingTest {

    /** DI ; IE = joypad ; LCD on ; boucle : sélectionne les boutons (P15), lit FF00, l'écrit en C000. */
    private static byte[] joypadReaderRom() {
        return new TestRom()
                .emit(0xF3)                       // DI
                .ldA(0x10).ldhA(0xFF)             // IE = interruption joypad
                .ldA(0x91).ldhA(0x40)             // LCD on
                .ldA(0x10).ldhA(0x00)             // boucle : sélection des boutons (bit 5 = 0)
                .emit(0xF0, 0x00)                 // LDH A,(FF00)
                .emit(0xEA, 0x00, 0xC0)           // LD (C000),A
                .emit(0x18, 0xF5)                 // JR boucle (-11)
                .build();
    }

    private static Emulator newEmu(byte[] rom) {
        Emulator e = new Emulator(TestRom.cartridge(rom));
        e.reset();
        return e;
    }

    @Test void shortTapBetweenTwoFramesIsNotLost() {
        Emulator e = newEmu(joypadReaderRom());
        e.runFrame();
        assertEquals(1, e.getCpu().rd(0xC000) & 1, "A relâché au départ");

        e.setButton(Joypad.Button.A, true);
        e.setButton(Joypad.Button.A, false);   // tap plus court qu'une image
        e.runFrame();
        assertEquals(0, e.getCpu().rd(0xC000) & 1, "le tap doit être vu pendant l'image suivante");

        e.runFrame();
        assertEquals(1, e.getCpu().rd(0xC000) & 1, "puis relâché à l'image d'après");
    }

    @Test void buttonStateIsFrozenDuringAFrame() {
        Emulator e = newEmu(joypadReaderRom());
        e.setButton(Joypad.Button.START, true);
        e.runFrame();
        assertEquals(0, e.getCpu().rd(0xC000) & 0x08, "Start enfoncé");
        e.setButton(Joypad.Button.START, false);
        assertEquals(0, e.getCpu().rd(0xC000) & 0x08, "rien ne change avant l'image suivante");
        e.runFrame();
        assertEquals(0x08, e.getCpu().rd(0xC000) & 0x08, "Start relâché");
    }

    @Test void joypadInterruptIsRaisedByTheEmulationThreadOnly() {
        Emulator e = newEmu(joypadReaderRom());
        e.runFrame();
        byte[] io = e.getCpu().ioHandler.registers;
        io[0x0F] &= ~0x10;

        e.setButton(Joypad.Button.B, true);     // thread « hôte »
        assertEquals(0, io[0x0F] & 0x10, "setButton ne doit pas écrire IF depuis l'hôte");
        e.runFrame();                           // thread d'émulation
        assertEquals(0x10, io[0x0F] & 0x10, "IF.joypad levé au début de l'image");

        io[0x0F] &= ~0x10;
        e.runFrame();                           // bouton toujours tenu : pas de nouveau front
        assertEquals(0, io[0x0F] & 0x10, "pas d'interruption sans nouvel appui");
    }

    /** Sans verrou, saveState() pris pendant une image capture un LY quelconque ; avec le
     *  verrou, il est toujours pris entre deux images (LY revenu à 0). */
    @Test void concurrentSaveStatesAreAlwaysTakenBetweenFrames() throws Exception {
        Emulator e = newEmu(new TestRom().ldA(0x91).ldhA(0x40).ldHL(0xC000).emit(0x34).emit(0x18, 0xFD).build());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread emuThread = new Thread(() -> {
            try { while (running.get()) e.runFrame(); } catch (Throwable t) { failure.set(t); }
        }, "emulation");
        Thread padThread = new Thread(() -> {
            try {
                for (int i = 0; running.get(); i++) e.setButton(Joypad.Button.values()[i & 7], (i & 8) == 0);
            } catch (Throwable t) { failure.set(t); }
        }, "manette");
        emuThread.start();
        padThread.start();
        try {
            SaveState previous = null;
            for (int i = 0; i < 400; i++) {
                SaveState st = e.saveState();
                assertEquals(0, st.ioRegisters()[0x44], "LY du save state n° " + i);
                if (previous != null && i % 7 == 0) e.loadState(previous);
                if (i % 50 == 0) e.batteryRam();
                previous = st;
            }
        } finally {
            running.set(false);
            emuThread.join(2000);
            padThread.join(2000);
        }
        assertFalse(emuThread.isAlive(), "le thread d'émulation doit s'arrêter");
        if (failure.get() != null) throw new AssertionError("exception dans un thread", failure.get());
    }

    /** Le frame listener (affichage) tourne hors verrou : un affichage qui bloque n'empêche pas
     *  l'UI de prendre un save state. */
    @Test void frameListenerRunsOutsideTheLock() throws Exception {
        Emulator e = newEmu(joypadReaderRom());
        CountDownLatch inListener = new CountDownLatch(1);
        CountDownLatch savedFromUi = new CountDownLatch(1);
        e.setFrameListener(px -> {
            inListener.countDown();
            try { savedFromUi.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        Thread emuThread = new Thread(e::runFrame, "emulation");
        emuThread.start();
        assertTrue(inListener.await(2, TimeUnit.SECONDS), "le listener doit être appelé");

        Thread ui = new Thread(() -> { e.saveState(); savedFromUi.countDown(); }, "ui");
        ui.start();
        boolean saved = savedFromUi.await(1, TimeUnit.SECONDS);
        e.setFrameListener(null);
        emuThread.join(3000);
        ui.join(3000);
        assertTrue(saved, "saveState() ne doit pas attendre la fin de l'affichage");
    }
}

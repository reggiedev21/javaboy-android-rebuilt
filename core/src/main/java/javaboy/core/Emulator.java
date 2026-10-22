package javaboy.core;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Point d'entrée unique du cœur pour les hôtes (PC, Android, tests).
 *
 *  <pre>
 *  Emulator emu = new Emulator(cartridge, joypad, audioOutput, 44100);
 *  emu.setFrameListener(pixels -> view.draw(pixels));
 *  emu.reset();
 *  while (running) { emu.runFrame(); pace(); }
 *  </pre>
 *
 *  <h2>Threads</h2>
 *  Un seul thread appelle {@link #runFrame()} (le thread d'émulation). Les autres méthodes
 *  publiques peuvent être appelées depuis n'importe quel thread (UI Android, EDT Swing,
 *  manette Bluetooth) :
 *  <ul>
 *    <li>{@link #setButton} ne bloque jamais : l'appui est pris en compte au début de l'image
 *        suivante, et l'interruption joypad est levée sur le thread d'émulation ;</li>
 *    <li>{@link #saveState}, {@link #loadState}, {@link #reset}, {@link #batteryRam},
 *        {@link #loadBatteryRam}, {@link #setColourScheme}, {@link #setGbcAllowed} prennent un
 *        verrou et s'exécutent <b>entre deux images</b> : ils attendent au plus la fin de
 *        l'image en cours (quelques ms), jamais au milieu d'une instruction ;</li>
 *    <li>le {@link #setFrameListener frame listener} est appelé sur le thread d'émulation
 *        <b>hors verrou</b> : un affichage lent (vsync, lockCanvas) ne fait pas attendre l'UI.</li>
 *  </ul>
 */
public final class Emulator {
    public static final int DEFAULT_SAMPLE_RATE = 44100;

    /** Jeux de couleurs DMG (BG ×4, OBJ0 ×4, OBJ1 ×4) repris de l'original. */
    public static final String[] SCHEME_NAMES = {"Standard colours", "LCD shades", "Midnight garden", "Psychadelic"};
    public static final int[][] SCHEME_COLOURS = {
        {0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000,
         0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000,
         0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000},
        {0xFFFFFFC0, 0xFFC2C41E, 0xFF949600, 0xFF656600,
         0xFFFFFFC0, 0xFFC2C41E, 0xFF949600, 0xFF656600,
         0xFFFFFFC0, 0xFFC2C41E, 0xFF949600, 0xFF656600},
        {0xFFC0C0FF, 0xFF4040FF, 0xFF0000FF, 0xFF000080,
         0xFFC0FFC0, 0xFF00C000, 0xFF008000, 0xFF004000,
         0xFFC0FFC0, 0xFF00C000, 0xFF008000, 0xFF004000},
        {0xFFFFC0FF, 0xFF8080FF, 0xFFC000C0, 0xFF800080,
         0xFFFFFF40, 0xFFC0C000, 0xFFFF4040, 0xFF800000,
         0xFF80FFFF, 0xFF00C0C0, 0xFF008080, 0xFF004000}};

    private final Cartridge cartridge;
    private final Joypad joypad;
    private final Dmgcpu cpu;

    /** Protège tout l'état émulé : tenu pendant chaque image et par les accès de l'hôte.
     *  Verrou <b>équitable</b> : quand le thread d'émulation enchaîne les images sans pause
     *  (avance rapide, appareil lent), un thread UI en attente passe à la fin de l'image en
     *  cours au lieu d'être doublé indéfiniment. */
    private final ReentrantLock lock = new ReentrantLock(true);

    private void locked(Runnable r) {
        lock.lock();
        try { r.run(); } finally { lock.unlock(); }
    }

    private <T> T locked(Supplier<T> s) {
        lock.lock();
        try { return s.get(); } finally { lock.unlock(); }
    }

    /** Image terminée, copiée pour être livrée hors verrou (thread d'émulation uniquement). */
    private final int[] completedFrame = new int[GraphicsChip.WIDTH * GraphicsChip.HEIGHT];
    private boolean frameToDeliver;
    /** Vrai pendant runFrame() : l'image est livrée après la sortie du verrou. */
    private boolean deferFrames;
    private volatile Consumer<int[]> frameListener = null;

    public Emulator(Cartridge cartridge, Joypad joypad, AudioOutput audio, int sampleRate) {
        this.cartridge = cartridge;
        this.joypad = joypad == null ? new Joypad() : joypad;
        this.cpu = new Dmgcpu(cartridge, null, this.joypad, audio, sampleRate);
        cpu.graphicsChip.setFrameListener(this::onFrameCompleted);
    }

    public Emulator(Cartridge cartridge) {
        this(cartridge, new Joypad(), AudioOutput.NONE, DEFAULT_SAMPLE_RATE);
    }

    public Cartridge getCartridge() { return cartridge; }
    public Joypad getJoypad() { return joypad; }
    public Dmgcpu getCpu() { return cpu; }
    public GraphicsChip getGraphics() { return cpu.graphicsChip; }
    public SoundChip getSound() { return cpu.soundChip; }

    /** Destinataire des images 160×144 ARGB, appelé sur le thread d'émulation, hors verrou.
     *  Le tampon est réutilisé : le copier pendant l'appel. */
    public void setFrameListener(Consumer<int[]> listener) { frameListener = listener; }

    /** Active ou non les fonctions Game Boy Color (doit précéder reset()). */
    public void setGbcAllowed(boolean allowed) { locked(() -> { cpu.allowGbcFeatures = allowed; }); }
    public boolean isGbcMode() { return cpu.gbcFeatures; }

    public void setColourScheme(int index) { locked(() -> cpu.graphicsChip.setColourScheme(SCHEME_COLOURS[index])); }

    public void reset() { locked(cpu::reset); }

    /** Émule jusqu'à la fin de l'image courante (~1/60 s de temps Game Boy).
     *  À n'appeler que depuis le thread d'émulation. */
    public void runFrame() {
        lock.lock();
        try {
            pollJoypad();
            deferFrames = true;
            cpu.runFrame();
        } finally {
            deferFrames = false;
            lock.unlock();
        }
        deliverFrame();
    }

    /** Émule un nombre donné d'instructions (tests, débogage). Les images terminées pendant
     *  l'appel sont livrées immédiatement, une par une. */
    public void execute(int numInstr) {
        lock.lock();
        try {
            pollJoypad();
            cpu.execute(numInstr);
        } finally {
            lock.unlock();
        }
    }

    /** Demande à l'appel en cours de execute()/runFrame() de rendre la main (sans verrou). */
    public void stop() { cpu.terminateProcess(); }

    /** Fige l'état de la manette pour l'image qui commence ; lève l'interruption joypad si un
     *  bouton vient d'être enfoncé. Appelé sous verrou, sur le thread d'émulation. */
    private void pollJoypad() {
        if (joypad.poll() != 0) cpu.triggerInterruptIfEnabled(cpu.INT_P10);
    }

    /** Fin d'image signalée par la puce graphique (sous verrou). */
    private void onFrameCompleted(int[] frame) {
        Consumer<int[]> l = frameListener;
        if (l == null) return;
        if (deferFrames) {
            System.arraycopy(frame, 0, completedFrame, 0, completedFrame.length);
            frameToDeliver = true;
        } else {
            l.accept(frame);
        }
    }

    /** Livre l'image de runFrame() une fois le verrou relâché. */
    private void deliverFrame() {
        if (!frameToDeliver) return;
        frameToDeliver = false;
        Consumer<int[]> l = frameListener;
        if (l != null) l.accept(completedFrame);
    }

    /** Copie cohérente de la RAM de sauvegarde (à écrire dans le .sav), prise entre deux images. */
    public byte[] batteryRam() { return locked(cartridge::getBatteryRam); }

    /** Restaure la RAM de sauvegarde depuis un .sav, entre deux images. */
    public void loadBatteryRam(byte[] data) { locked(() -> cartridge.loadBatteryRam(data)); }

    /** Instantané immuable de l'état courant, pris entre deux images. */
    public SaveState saveState() {
        return locked(this::saveStateLocked);
    }

    private SaveState saveStateLocked() {
        Cartridge cart = cartridge;
        GraphicsChip g = cpu.graphicsChip;
        int[] pal = g.exportPalettes();
        SaveState.CpuRegisters regs = new SaveState.CpuRegisters(cpu.a, cpu.b, cpu.c, cpu.d, cpu.e, cpu.f, cpu.hl, cpu.sp, cpu.pc,
                cpu.interruptsEnabled, cpu.ieDelay, cpu.halted, cpu.instrCount,
                cpu.gbcFeatures, cpu.gbcRamBank, g.vidRamStart / 0x2000, cpu.timaEnabled, cpu.instrsPerTima,
                cpu.INSTRS_PER_HBLANK != cpu.BASE_INSTRS_PER_HBLANK, cpu.ioHandler.hdmaRunning);
        SaveState.MapperState mapper = new SaveState.MapperState(cart.currentBank, cart.ramBank, cart.ramEnabled, cart.mbc1LargeRamMode);
        return new SaveState(SaveState.VERSION, cart.getCartName(), regs, cpu.mainRam, g.videoRam, cpu.oam,
                cpu.ioHandler.registers, cart.ram, mapper, pal);
    }

    /** Restaure un instantané (doit provenir de la même ROM), entre deux images. */
    public void loadState(SaveState st) {
        locked(() -> loadStateLocked(st));
    }

    private void loadStateLocked(SaveState st) {
        if (!st.cartName().equals(cartridge.getCartName()))
            throw new IllegalArgumentException("Save state d'une autre ROM : " + st.cartName());
        SaveState.CpuRegisters r = st.cpu();
        cpu.a = r.a(); cpu.b = r.b(); cpu.c = r.c(); cpu.d = r.d(); cpu.e = r.e(); cpu.f = r.f();
        cpu.hl = r.hl(); cpu.sp = r.sp(); cpu.pc = r.pc();
        cpu.interruptsEnabled = r.ime(); cpu.ieDelay = r.ieDelay(); cpu.halted = r.halted(); cpu.instrCount = r.instrCount();
        cpu.gbcFeatures = r.gbc(); cpu.gbcRamBank = r.gbcRamBank(); cpu.timaEnabled = r.timaEnabled(); cpu.instrsPerTima = r.instrsPerTima();
        cpu.setDoubleSpeedCpu(r.doubleSpeed());
        cpu.ioHandler.hdmaRunning = r.hdmaRunning();
        System.arraycopy(st.mainRam(), 0, cpu.mainRam, 0, cpu.mainRam.length);
        GraphicsChip g = cpu.graphicsChip;
        System.arraycopy(st.videoRam(), 0, g.videoRam, 0, g.videoRam.length);
        g.vidRamStart = r.vramBank() * 0x2000;
        System.arraycopy(st.oam(), 0, cpu.oam, 0, cpu.oam.length);
        byte[] cartRam = st.cartRam();   // les save states antérieurs contiennent 64 Ko quelle que soit la cartouche
        System.arraycopy(cartRam, 0, cartridge.ram, 0, Math.min(cartRam.length, cartridge.ram.length));
        // Rejoue les registres d'E/S pour reconstruire palettes, son et timers
        byte[] io = st.ioRegisters();
        for (int reg = 0; reg < 0x100; reg++) {
            if (reg == 0x46 || reg == 0x55 || reg == 0x69 || reg == 0x6B || reg == 0x04 || reg == 0x02 || reg == 0x00) continue; // DMA, palettes GBC, DIV, série, joypad : copie brute
            cpu.ioHandler.ioWrite(reg, (short) (io[reg] & 0xFF));
        }
        System.arraycopy(io, 0, cpu.ioHandler.registers, 0, io.length);
        SaveState.MapperState m = st.mapper();
        cartridge.restoreMapper(m.currentBank(), m.ramBank(), m.ramEnabled(), m.mbc1LargeRamMode());
        g.importPalettes(st.paletteColours());
    }

    /** Vrai tant que le jeu (MBC5+RUMBLE) demande au moteur de vibrer : l'hôte le relit à
     *  chaque image, par exemple dans le frame listener, et pilote le vibreur. */
    public boolean isRumbling() { return cartridge.isRumbleActive(); }

    /** Signale un appui/relâchement. Utilisable depuis n'importe quel thread, ne bloque pas :
     *  le jeu le voit au début de l'image suivante (un tap plus court qu'une image n'est pas
     *  perdu), et l'interruption joypad est levée à ce moment-là, sur le thread d'émulation. */
    public void setButton(Joypad.Button b, boolean pressed) {
        joypad.set(b, pressed);
    }
}

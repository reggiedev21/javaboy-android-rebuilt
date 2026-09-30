package javaboy.core;

import java.util.function.Consumer;

/** Point d'entrée unique du cœur pour les hôtes (PC, Android, tests).
 *
 *  <pre>
 *  Emulator emu = new Emulator(cartridge, joypad, audioOutput, 44100);
 *  emu.setFrameListener(pixels -> view.draw(pixels));
 *  emu.reset();
 *  while (running) { emu.runFrame(); pace(); }
 *  </pre>
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

    public Emulator(Cartridge cartridge, Joypad joypad, AudioOutput audio, int sampleRate) {
        this.cartridge = cartridge;
        this.joypad = joypad == null ? new Joypad() : joypad;
        this.cpu = new Dmgcpu(cartridge, null, this.joypad, audio, sampleRate);
    }

    public Emulator(Cartridge cartridge) {
        this(cartridge, new Joypad(), AudioOutput.NONE, DEFAULT_SAMPLE_RATE);
    }

    public Cartridge getCartridge() { return cartridge; }
    public Joypad getJoypad() { return joypad; }
    public Dmgcpu getCpu() { return cpu; }
    public GraphicsChip getGraphics() { return cpu.graphicsChip; }
    public SoundChip getSound() { return cpu.soundChip; }

    /** Destinataire des images 160×144 ARGB (le tampon est réutilisé : copier pendant l'appel). */
    public void setFrameListener(Consumer<int[]> listener) { cpu.graphicsChip.setFrameListener(listener); }

    /** Active ou non les fonctions Game Boy Color (doit précéder reset()). */
    public void setGbcAllowed(boolean allowed) { cpu.allowGbcFeatures = allowed; }
    public boolean isGbcMode() { return cpu.gbcFeatures; }

    public void setColourScheme(int index) { cpu.graphicsChip.setColourScheme(SCHEME_COLOURS[index]); }

    public void reset() { cpu.reset(); }

    /** Émule jusqu'à la fin de l'image courante (~1/60 s de temps Game Boy). */
    public void runFrame() { cpu.runFrame(); }

    /** Émule un nombre donné d'instructions (tests, débogage). */
    public void execute(int numInstr) { cpu.execute(numInstr); }

    /** Demande à l'appel en cours de execute()/runFrame() de rendre la main. */
    public void stop() { cpu.terminateProcess(); }

    /** Instantané immuable de l'état courant. */
    public SaveState saveState() {
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

    /** Restaure un instantané (doit provenir de la même ROM). */
    public void loadState(SaveState st) {
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
        System.arraycopy(st.cartRam(), 0, cartridge.ram, 0, cartridge.ram.length);
        // Rejoue les registres d'E/S pour reconstruire palettes, son et timers
        byte[] io = st.ioRegisters();
        for (int reg = 0; reg < 0x100; reg++) {
            if (reg == 0x46 || reg == 0x55 || reg == 0x69 || reg == 0x6B || reg == 0x04 || reg == 0x02 || reg == 0x00) continue; // DMA, palettes GBC, DIV, série, joypad : copie brute
            cpu.ioHandler.ioWrite(reg, (short) (io[reg] & 0xFF));
        }
        System.arraycopy(io, 0, cpu.ioHandler.registers, 0, io.length);
        SaveState.MapperState m = st.mapper();
        cartridge.mapRom(m.currentBank()); cartridge.ramBank = m.ramBank(); cartridge.ramPageStart = m.ramBank() * 0x2000;
        cartridge.ramEnabled = m.ramEnabled(); cartridge.mbc1LargeRamMode = m.mbc1LargeRamMode();
        g.importPalettes(st.paletteColours());
    }

    /** Signale un appui/relâchement à la puce d'E/S (interruption joypad). */
    public void setButton(Joypad.Button b, boolean pressed) {
        joypad.set(b, pressed);
        if (pressed) cpu.triggerInterruptIfEnabled(cpu.INT_P10);
    }
}

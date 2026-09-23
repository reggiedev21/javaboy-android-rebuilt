package javaboy.core;

import java.util.ArrayList;
import java.util.List;

/** Fabrique de ROMs synthétiques (32 Ko, sans mapper) pour les tests : un mini-assembleur
 *  d'octets et un en-tête valide avec checksum. Aucune ROM externe n'est nécessaire. */
public final class TestRom {
    private final List<Integer> code = new ArrayList<>();

    public TestRom emit(int... bytes) { for (int b : bytes) code.add(b & 0xFF); return this; }

    // Quelques mnémoniques usuels
    public TestRom ldA(int n)        { return emit(0x3E, n); }
    public TestRom ldB(int n)        { return emit(0x06, n); }
    public TestRom ldHL(int nn)      { return emit(0x21, nn & 0xFF, nn >> 8); }
    public TestRom ldHLA()           { return emit(0x77); }        // LD (HL), A
    public TestRom ldHLIA()          { return emit(0x22); }        // LD (HL+), A
    public TestRom ldhA(int io)      { return emit(0xE0, io); }    // LDH (io), A
    public TestRom addA(int n)       { return emit(0xC6, n); }
    public TestRom decB()            { return emit(0x05); }
    public TestRom jrNZ(int rel)     { return emit(0x20, rel & 0xFF); }
    public TestRom halt()            { return emit(0x76); }
    /** Boucle infinie sur place (JR -2). */
    public TestRom loopForever()     { return emit(0x18, 0xFE); }

    /** Construit la ROM : le code est placé à 0x0150, l'entrée 0x0100 saute dessus. */
    public byte[] build() {
        byte[] rom = new byte[0x8000];
        rom[0x100] = 0x00;            // NOP
        rom[0x101] = (byte) 0xC3;     // JP 0x0150
        rom[0x102] = 0x50;
        rom[0x103] = 0x01;
        byte[] title = "TESTROM".getBytes();
        System.arraycopy(title, 0, rom, 0x134, title.length);
        rom[0x147] = 0;               // ROM only
        rom[0x148] = 0;               // 32 Ko
        rom[0x149] = 0;               // pas de RAM
        for (int i = 0; i < code.size(); i++) rom[0x150 + i] = (byte) (int) code.get(i);
        int total = 0;
        for (int r = 0; r < rom.length; r++) if (r != 0x14E && r != 0x14F) total = (total + (rom[r] & 0xFF)) & 0xFFFF;
        rom[0x14E] = (byte) (total >> 8);
        rom[0x14F] = (byte) total;
        return rom;
    }

    public static Cartridge cartridge(byte[] rom) {
        return switch (Cartridge.load(rom)) {
            case CartridgeResult.Ok ok -> ok.cartridge();
            case CartridgeResult.Error err -> throw new IllegalStateException(err.message());
        };
    }
}

package javaboy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests de la table d'opcodes : appels, pile, CB, DAA, HALT, interruptions. */
class CpuTableTest {
    private Emulator run(TestRom rom, int instr) {
        Emulator e = new Emulator(TestRom.cartridge(rom.build()));
        e.reset();
        e.execute(instr);
        return e;
    }

    @Test void callAndRet() {
        // CALL 0x0160 ; LD B,7 ; JR -2   |  0x0160: LD A,0x33 ; RET
        TestRom r = new TestRom().emit(0xCD, 0x60, 0x01).ldB(7).loopForever();
        while (r.size() < 0x10) r.emit(0);
        r.ldA(0x33).emit(0xC9);
        Emulator e = run(r, 6);
        assertEquals(0x33, e.getCpu().a);
        assertEquals(7, e.getCpu().b);
        assertEquals(0xFFFE, e.getCpu().sp);
    }

    @Test void pushPopAf() {
        // LD A,0x12 ; ADD A,0xFF (C,H) ; PUSH AF ; XOR A ; POP AF
        Emulator e = run(new TestRom().ldA(0x12).addA(0xFF).emit(0xF5).emit(0xAF).emit(0xF1).loopForever(), 7);
        assertEquals(0x11, e.getCpu().a);
        assertEquals(Alu.C | Alu.H, e.getCpu().f);
    }

    @Test void cbOnMemory() {
        // LD HL,0xC000 ; LD (HL),0x81 ; SET 1,(HL) ; RLC (HL) ; BIT 0,(HL)
        Emulator e = run(new TestRom().ldHL(0xC000).emit(0x36, 0x81).emit(0xCB, 0xCE).emit(0xCB, 0x06).emit(0xCB, 0x46).loopForever(), 7);
        assertEquals(0x07, e.getCpu().rd(0xC000)); // 0x83 rlc -> 0x07, C
        assertEquals(Alu.H | Alu.C, e.getCpu().f);  // BIT 0 : bit à 1 -> Z=0, H=1, C conservé
    }

    @Test void daaViaCpu() {
        Emulator e = run(new TestRom().ldA(0x45).addA(0x38).emit(0x27).loopForever(), 5);
        assertEquals(0x83, e.getCpu().a);
    }

    @Test void add16AndSpArithmetic() {
        // LD HL,0x0FFF ; LD BC,1 ; ADD HL,BC ; LD SP,0xFFF8 ; ADD SP,8 ; LD HL,SP-1
        Emulator e = run(new TestRom().ldHL(0x0FFF).emit(0x01, 0x01, 0x00).emit(0x09)
                .emit(0x31, 0xF8, 0xFF).emit(0xE8, 0x08).emit(0xF8, 0xFF).loopForever(), 8);
        assertEquals(0x0000, e.getCpu().sp);
        assertEquals(0xFFFF, e.getCpu().hl);
    }

    @Test void haltWakesOnVBlankAndDispatches() {
        // IE=VBLANK ; EI ; HALT ; LD B,1 ; JR -2   | vecteur 0x40 : LD C,9 ; RETI
        TestRom r = new TestRom().ldA(0x01).ldhA(0xFF).ldA(0x91).ldhA(0x40).emit(0xFB).halt().ldB(1).loopForever();
        byte[] rom = r.build();
        rom[0x40] = 0x0E; rom[0x41] = 0x09; rom[0x42] = (byte) 0xD9; // LD C,9 ; RETI
        // recalcul du checksum (TestRom l'a calculé avant nos écritures)
        int total = 0; for (int i = 0; i < rom.length; i++) if (i != 0x14E && i != 0x14F) total = (total + (rom[i] & 0xFF)) & 0xFFFF;
        rom[0x14E] = (byte) (total >> 8); rom[0x14F] = (byte) total;
        Emulator e = new Emulator(TestRom.cartridge(rom));
        e.reset();
        e.runFrame(); e.runFrame();
        assertEquals(9, e.getCpu().c);
        assertEquals(1, e.getCpu().b);
        assertFalse(e.getCpu().halted);
    }

    @Test void allOpcodesAreDefined() {
        Emulator e = run(new TestRom().loopForever(), 0);
        // exécute chaque opcode une fois en RAM pour vérifier qu'aucun ne lève d'exception
        for (int op = 0; op < 256; op++) {
            if (op == 0x76 || op == 0x10 || op == 0xCB) continue;
            Dmgcpu c = e.getCpu();
            c.pc = 0xC100; c.sp = 0xC800; c.hl = 0xC200;
            c.wr(0xC100, op); c.wr(0xC101, 0x00); c.wr(0xC102, 0xC3);
            c.halted = false;
            e.execute(1);
        }
    }
}

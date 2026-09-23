package javaboy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CpuTest {
    private Emulator run(TestRom rom, int instr) {
        Emulator e = new Emulator(TestRom.cartridge(rom.build()));
        e.reset();
        e.execute(instr);
        return e;
    }

    @Test
    void resetState() {
        Emulator e = run(new TestRom().loopForever(), 0);
        Dmgcpu c = e.getCpu();
        assertEquals(0x0100, c.pc);
        assertEquals(0xFFFE, c.sp);
        assertEquals(0x01, c.a);
        assertEquals(0xB0, c.f);
    }

    @Test
    void addSetsCarryAndHalfCarry() {
        // LD A,0xF8 ; ADD A,0x0A -> A=0x02, C=1, H=1, Z=0
        Emulator e = run(new TestRom().ldA(0xF8).addA(0x0A).loopForever(), 2 + 2);
        Dmgcpu c = e.getCpu();
        assertEquals(0x02, c.a);
        assertEquals(c.F_CARRY | c.F_HALFCARRY, c.f & 0xF0);
    }

    @Test
    void addZeroFlag() {
        Emulator e = run(new TestRom().ldA(0xFF).addA(0x01).loopForever(), 4);
        assertEquals(0, e.getCpu().a);
        assertTrue((e.getCpu().f & e.getCpu().F_ZERO) != 0);
    }

    @Test
    void loopWithDecJr() {
        // LD B,5 ; DEC B ; JR NZ,-3 ; puis boucle : B doit finir à 0
        Emulator e = run(new TestRom().ldB(5).decB().jrNZ(-3).loopForever(), 100);
        assertEquals(0, e.getCpu().b);
    }

    @Test
    void writeToWorkRam() {
        Emulator e = run(new TestRom().ldA(0x5A).ldHL(0xC123).ldHLA().loopForever(), 5);
        assertEquals(0x5A, e.getCpu().addressRead(0xC123) & 0xFF);
    }

    @Test
    void runFrameAdvancesLyThroughAFullFrame() {
        Emulator e = run(new TestRom().loopForever(), 0);
        int[] frames = {0};
        e.setFrameListener(f -> frames[0]++);
        e.runFrame();
        assertEquals(1, frames[0]);
        assertEquals(0, e.getCpu().ioHandler.registers[0x44]); // LY revenu à 0
    }
}

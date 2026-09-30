package javaboy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaveStateTest {
    private TestRom counterRom() {
        // LCD on ; boucle : INC (0xC000) ; JR -4   -> la RAM avance à chaque tour
        return new TestRom().ldA(0x91).ldhA(0x40).ldHL(0xC000).emit(0x34).emit(0x18, 0xFD);
    }

    @Test void roundTripRestoresExactState() throws Exception {
        Emulator e = new Emulator(TestRom.cartridge(counterRom().build()));
        e.reset();
        for (int i = 0; i < 3; i++) e.runFrame();
        SaveState st = e.saveState();
        int pcAtSave = e.getCpu().pc, ramAtSave = e.getCpu().rd(0xC000), countAtSave = e.getCpu().instrCount;

        for (int i = 0; i < 5; i++) e.runFrame();
        assertTrue(e.getCpu().instrCount > countAtSave);

        byte[] bytes = st.toBytes();
        SaveState back = SaveState.fromBytes(bytes);
        e.loadState(back);
        assertEquals(pcAtSave, e.getCpu().pc);
        assertEquals(ramAtSave, e.getCpu().rd(0xC000));
        assertEquals(countAtSave, e.getCpu().instrCount);
        assertEquals(0x91, e.getCpu().ioHandler.registers[0x40] & 0xFF);
    }

    @Test void recordIsImmutable() {
        Emulator e = new Emulator(TestRom.cartridge(counterRom().build()));
        e.reset();
        SaveState st = e.saveState();
        st.mainRam()[0] = 42;          // modifier la copie renvoyée...
        assertEquals(0, st.mainRam()[0]); // ...ne touche pas le record
    }

    @Test void refusesStateFromOtherRom() {
        Emulator e = new Emulator(TestRom.cartridge(counterRom().build()));
        e.reset();
        SaveState st = e.saveState();
        byte[] other = counterRom().build();
        other[0x134] = 'X';
        int total = 0; for (int i = 0; i < other.length; i++) if (i != 0x14E && i != 0x14F) total = (total + (other[i] & 0xFF)) & 0xFFFF;
        other[0x14E] = (byte) (total >> 8); other[0x14F] = (byte) total;
        Emulator e2 = new Emulator(TestRom.cartridge(other));
        assertThrows(IllegalArgumentException.class, () -> e2.loadState(st));
    }
}

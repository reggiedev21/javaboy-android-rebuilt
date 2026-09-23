package javaboy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartridgeTest {
    @Test
    void loadsValidRomWithChecksum() {
        byte[] rom = new TestRom().loopForever().build();
        CartridgeResult r = Cartridge.load(rom);
        assertInstanceOf(CartridgeResult.Ok.class, r);
        CartridgeResult.Ok ok = (CartridgeResult.Ok) r;
        assertTrue(ok.checksumValid());
        assertEquals("TESTROM", ok.cartridge().getCartName());
        assertEquals(2, ok.cartridge().getNumBanks());
        assertFalse(ok.cartridge().isGbc());
    }

    @Test
    void reportsInvalidChecksumWithoutFailing() {
        byte[] rom = new TestRom().loopForever().build();
        rom[0x14F] ^= 1;
        assertFalse(((CartridgeResult.Ok) Cartridge.load(rom)).checksumValid());
    }

    @Test
    void rejectsTooSmallFile() {
        assertInstanceOf(CartridgeResult.Error.class, Cartridge.load(new byte[10]));
    }

    @Test
    void rejectsUnknownSizeByte() {
        byte[] rom = new TestRom().loopForever().build();
        rom[0x148] = 0x42;
        assertInstanceOf(CartridgeResult.Error.class, Cartridge.load(rom));
    }

    @Test
    void mbc1BankSwitching() {
        byte[] rom = new byte[0x4000 * 4];
        rom[0x147] = 1; rom[0x148] = 1; // MBC1, 64 Ko
        rom[0x4000 * 3] = 0x42;
        Cartridge c = TestRom.cartridge(rom);
        c.reset();
        c.addressWrite(0x2000, 3);
        assertEquals(0x42, c.addressRead(0x4000));
        assertEquals(3, c.getCurrentBank());
    }
}

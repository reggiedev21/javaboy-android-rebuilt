package javaboy.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Mappers et RAM de cartouche, sur des ROMs synthétiques (sans CPU). */
class MapperTest {

    /** ROM de {@code banks} banques de 16 Ko ; le premier octet de chaque banque vaut son numéro. */
    static Cartridge cart(int type, int romSizeCode, int ramCode) {
        int banks = Cartridge.lookUpCartSize(romSizeCode);
        byte[] rom = new byte[0x4000 * banks];
        for (int b = 0; b < banks; b++) rom[0x4000 * b] = (byte) b;
        rom[0x147] = (byte) type;
        rom[0x148] = (byte) romSizeCode;
        rom[0x149] = (byte) ramCode;
        Cartridge c = TestRom.cartridge(rom);
        c.reset();
        return c;
    }

    static int rd(Cartridge c, int addr) { return c.addressRead(addr) & 0xFF; }

    // ------------------------------------------------------------ taille de RAM

    @Test void ramIsSizedFromHeader() {
        assertEquals(0x20000, cart(0x1B, 0, 4).getRamSize());   // 128 Ko
        assertEquals(0x10000, cart(0x1B, 0, 5).getRamSize());   //  64 Ko
        assertEquals(0x8000, cart(0x13, 0, 3).getRamSize());    //  32 Ko
        assertEquals(512, cart(0x06, 0, 0).getRamSize());       // MBC2 : 512 × 4 bits
        assertEquals(16, cart(0x1B, 0, 4).getNumRAMBanks());
        assertEquals(8, cart(0x1B, 0, 5).getNumRAMBanks());
    }

    @Test void ram128KbAllBanksAreDistinctAndSaved() {
        Cartridge c = cart(0x1B, 0, 4);
        c.addressWrite(0x0000, 0x0A);
        for (int bank = 0; bank < 16; bank++) { c.addressWrite(0x4000, bank); c.addressWrite(0xA000, 0x40 + bank); }
        for (int bank = 0; bank < 16; bank++) { c.addressWrite(0x4000, bank); assertEquals(0x40 + bank, rd(c, 0xA000)); }

        byte[] sav = c.getBatteryRam();
        assertEquals(0x20000, sav.length);
        Cartridge d = cart(0x1B, 0, 4);
        d.loadBatteryRam(sav);
        assertArrayEquals(sav, d.getBatteryRam());
    }

    @Test void ram64KbIsSaved() {
        Cartridge c = cart(0x1B, 0, 5);
        assertEquals(0x10000, c.getBatteryRamSize());
        c.addressWrite(0x0000, 0x0A);
        c.addressWrite(0x4000, 7);
        c.addressWrite(0xBFFF, 0x99);
        assertEquals(0x99, c.getBatteryRam()[7 * 0x2000 + 0x1FFF] & 0xFF);
    }

    @Test void noRamReadsFF() {
        Cartridge c = cart(0x01, 0, 0);  // MBC1 sans RAM
        c.addressWrite(0x0000, 0x0A);
        c.addressWrite(0xA000, 0x12);
        assertEquals(0xFF, rd(c, 0xA000));
    }

    // ------------------------------------------------------------ banques ROM

    @Test void romBankNumbersWrapAroundRomSize() {
        Cartridge c = cart(0x01, 1, 0);  // MBC1, 64 Ko = 4 banques
        c.addressWrite(0x2000, 6);       // banque 6 -> 6 mod 4 = 2
        assertEquals(2, rd(c, 0x4000));
    }

    @Test void mbc1UpperBitsSelectHighBanks() {
        Cartridge c = cart(0x01, 5, 0);  // MBC1, 1 Mo = 64 banques
        c.addressWrite(0x2000, 0x05);
        c.addressWrite(0x4000, 0x01);    // bits 5-6 = 1 -> banque 0x25
        assertEquals(0x25, rd(c, 0x4000));
    }

    @Test void mbc5NineBitRomBank() {
        Cartridge c = cart(0x19, 8, 0);  // MBC5, 8 Mo = 512 banques
        c.addressWrite(0x2000, 0x34);
        c.addressWrite(0x3000, 0x01);
        assertEquals(0x34, rd(c, 0x4000));  // banque 0x134 : premier octet = 0x134 & 0xFF
        assertEquals(0x134, c.getCurrentBank());
    }

    // ------------------------------------------------------------ activation de la RAM

    @Test void mbc5RamNeedsEnable() {
        Cartridge c = cart(0x1B, 0, 3);
        c.addressWrite(0xA000, 0x55);
        assertEquals(0xFF, rd(c, 0xA000), "RAM désactivée : lecture 0xFF");
        c.addressWrite(0x0000, 0x0A);
        assertTrue(rd(c, 0xA000) != 0x55, "l'écriture désactivée n'a rien écrit");
        c.addressWrite(0xA000, 0x55);
        assertEquals(0x55, rd(c, 0xA000));
        c.addressWrite(0x0000, 0x00);
        assertEquals(0xFF, rd(c, 0xA000));
    }

    @Test void mbc3RamNeedsEnable() {
        Cartridge c = cart(0x13, 0, 3);
        c.addressWrite(0x0000, 0x0A);
        c.addressWrite(0x4000, 0x02);
        c.addressWrite(0xA123, 0x77);
        assertEquals(0x77, rd(c, 0xA123));
        assertEquals(0x77, c.getBatteryRam()[2 * 0x2000 + 0x123] & 0xFF);
        c.addressWrite(0x0000, 0x00);
        assertEquals(0xFF, rd(c, 0xA123));
    }

    @Test void romPlusRamNeedsNoEnable() {
        Cartridge c = cart(0x09, 0, 2);
        c.addressWrite(0xA010, 0x42);
        assertEquals(0x42, rd(c, 0xA010));
    }

    // ------------------------------------------------------------ MBC2

    @Test void mbc2HasHalfByteRamMirrored() {
        Cartridge c = cart(0x06, 1, 0);
        c.addressWrite(0x0000, 0x0A);          // bit 8 = 0 : activation RAM
        c.addressWrite(0xA005, 0xAB);
        assertEquals(0xFB, rd(c, 0xA005), "4 bits stockés, nibble haut lu à 1");
        assertEquals(0xFB, rd(c, 0xA205), "miroir tous les 512 octets");
        assertEquals(512, c.getBatteryRam().length);
        assertEquals(0x0B, c.getBatteryRam()[5]);
    }

    @Test void mbc2BankSelectUsesAddressBit8() {
        Cartridge c = cart(0x06, 1, 0);
        c.addressWrite(0x2100, 3);             // bit 8 = 1 : banque ROM
        assertEquals(3, rd(c, 0x4000));
        c.addressWrite(0x2000, 2);             // bit 8 = 0 : registre d'activation, pas la banque
        assertEquals(3, rd(c, 0x4000));
    }

    // ------------------------------------------------------------ rumble

    @Test void mbc5RumbleBitDrivesMotorNotRamBank() {
        Cartridge c = cart(0x1E, 0, 3);
        assertTrue(c.hasRumble());
        c.addressWrite(0x0000, 0x0A);
        c.addressWrite(0x4000, 0x09);          // moteur + banque 1
        assertTrue(c.isRumbleActive());
        c.addressWrite(0xA000, 0x31);
        c.addressWrite(0x4000, 0x01);          // moteur coupé, même banque
        assertFalse(c.isRumbleActive());
        assertEquals(0x31, rd(c, 0xA000));
        assertEquals(0x31, c.getBatteryRam()[0x2000] & 0xFF);
    }

    // ------------------------------------------------------------ save states

    @Test void oldSaveStatesWith64KbCartRamStillLoad() {
        Emulator e = new Emulator(TestRom.cartridge(new TestRom().loopForever().build()));
        e.reset();
        SaveState st = e.saveState();
        SaveState old = new SaveState(st.version(), st.cartName(), st.cpu(), st.mainRam(), st.videoRam(), st.oam(),
                st.ioRegisters(), new byte[0x10000], st.mapper(), st.paletteColours());
        e.loadState(old);   // ne doit pas lever d'exception
        assertTrue(Arrays.equals(new byte[0x2000], Arrays.copyOf(e.getCartridge().ram, 0x2000)));
    }
}

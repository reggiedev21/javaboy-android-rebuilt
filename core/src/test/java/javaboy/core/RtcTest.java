package javaboy.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Horloge temps réel du MBC3, avec une horloge système simulée. */
class RtcTest {
    static final int S = 0x08, M = 0x09, H = 0x0A, DL = 0x0B, DH = 0x0C;

    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);

    private Cartridge rtcCart(int type) {
        Cartridge c = MapperTest.cart(type, 0, type == 0x10 ? 3 : 0);
        c.setClock(now::get);
        c.addressWrite(0x0000, 0x0A);   // active RAM + horloge
        return c;
    }

    private static void write(Cartridge c, int reg, int value) {
        c.addressWrite(0x4000, reg);
        c.addressWrite(0xA000, value);
    }

    private static int read(Cartridge c, int reg) {
        c.addressWrite(0x4000, reg);
        return c.addressRead(0xA000) & 0xFF;
    }

    private static void latch(Cartridge c) {
        c.addressWrite(0x6000, 0x00);
        c.addressWrite(0x6000, 0x01);
    }

    /** Met l'horloge à j jours h:m:s. */
    private static void set(Cartridge c, int days, int h, int m, int s) {
        write(c, S, s); write(c, M, m); write(c, H, h);
        write(c, DL, days & 0xFF); write(c, DH, (days >> 8) & 1);
    }

    private void tick(Cartridge c, long millis) { now.addAndGet(millis); c.update(); }

    @Test void readsReturnLatchedValuesUntilNextLatch() {
        Cartridge c = rtcCart(0x10);
        set(c, 0, 10, 20, 30);
        latch(c);
        assertEquals(30, read(c, S));
        tick(c, 5_000);
        assertEquals(30, read(c, S), "sans nouveau latch, la valeur lue ne bouge pas");
        latch(c);
        assertEquals(35, read(c, S));
    }

    @Test void latchNeedsZeroThenOne() {
        Cartridge c = rtcCart(0x10);
        set(c, 0, 0, 0, 0);
        latch(c);
        tick(c, 3_000);
        c.addressWrite(0x6000, 0x01);   // pas de 0 juste avant
        assertEquals(0, read(c, S));
    }

    @Test void secondsCarryIntoMinutesHoursAndDays() {
        Cartridge c = rtcCart(0x10);
        set(c, 41, 23, 59, 58);
        tick(c, 3_000);
        latch(c);
        assertEquals(1, read(c, S));
        assertEquals(0, read(c, M));
        assertEquals(0, read(c, H));
        assertEquals(42, read(c, DL));
    }

    @Test void subSecondTimeIsNotLostBetweenFrames() {
        Cartridge c = rtcCart(0x10);
        set(c, 0, 0, 0, 0);
        for (int frame = 0; frame < 600; frame++) tick(c, 16);   // 600 × 16 ms = 9,6 s
        latch(c);
        assertEquals(9, read(c, S));
    }

    @Test void dayCounterHas9BitsAndSetsCarryOnOverflow() {
        Cartridge c = rtcCart(0x10);
        set(c, 300, 0, 0, 0);
        latch(c);
        assertEquals(300 & 0xFF, read(c, DL));
        assertEquals(1, read(c, DH) & 1, "bit 8 du compteur de jours");

        set(c, 511, 23, 59, 59);
        tick(c, 1_000);
        latch(c);
        assertEquals(0, read(c, DL));
        assertEquals(0x80, read(c, DH) & 0x81, "retenue levée, compteur revenu à 0");
    }

    @Test void haltStopsTheClock() {
        Cartridge c = rtcCart(0x10);
        set(c, 0, 0, 0, 0);
        write(c, DH, 0x40);
        tick(c, 60_000);
        latch(c);
        assertEquals(0, read(c, S));
        assertEquals(0x40, read(c, DH) & 0x40);
        write(c, DH, 0x00);             // redémarre : le temps arrêté n'est pas rattrapé
        tick(c, 2_000);
        latch(c);
        assertEquals(2, read(c, S));
    }

    @Test void clockIsSavedInSavAndAdvancesWhileClosed() {
        Cartridge c = rtcCart(0x10);
        c.addressWrite(0x4000, 0x00);
        c.addressWrite(0xA000, 0x5A);   // un octet de RAM de jeu
        set(c, 3, 8, 0, 0);
        latch(c);

        byte[] sav = c.getBatteryRam();
        assertEquals(0x8000 + Cartridge.RTC_FOOTER_SIZE, sav.length);
        assertEquals(c.getBatteryRamSize(), sav.length);

        now.addAndGet(26L * 3600 * 1000 + 15 * 60 * 1000);   // émulateur fermé 1 j 2 h 15 min
        Cartridge d = MapperTest.cart(0x10, 0, 3);
        d.setClock(now::get);
        d.loadBatteryRam(sav);
        d.addressWrite(0x0000, 0x0A);
        d.addressWrite(0x4000, 0x00);
        assertEquals(0x5A, d.addressRead(0xA000) & 0xFF);
        latch(d);
        assertEquals(4, read(d, DL));
        assertEquals(10, read(d, H));
        assertEquals(15, read(d, M));
    }

    @Test void timerWithoutRamCartridgeSavesTheClock() {
        Cartridge c = rtcCart(0x0F);
        assertTrue(c.canSave());
        assertEquals(Cartridge.RTC_FOOTER_SIZE, c.getBatteryRamSize());
        set(c, 0, 12, 0, 0);
        byte[] sav = c.getBatteryRam();
        Cartridge d = MapperTest.cart(0x0F, 0, 0);
        d.setClock(now::get);
        d.loadBatteryRam(sav);
        d.addressWrite(0x0000, 0x0A);
        latch(d);
        assertEquals(12, read(d, H));
    }

    @Test void savWithoutClockFooterIsAccepted() {
        Cartridge c = rtcCart(0x10);
        byte[] legacy = new byte[0x8000];   // .sav écrit par une version précédente
        legacy[0] = 0x11;
        c.loadBatteryRam(legacy);
        c.addressWrite(0x4000, 0x00);
        assertEquals(0x11, c.addressRead(0xA000) & 0xFF);
    }

    @Test void clockRegistersNeedRamEnable() {
        Cartridge c = rtcCart(0x10);
        set(c, 0, 5, 0, 0);
        latch(c);
        c.addressWrite(0x0000, 0x00);
        assertEquals(0xFF, read(c, H));
    }

    @Test void invalidBankSelectDoesNotCrash() {
        Cartridge c = rtcCart(0x10);
        for (int v = 0x04; v <= 0x07; v++) {
            c.addressWrite(0x4000, v);
            c.addressRead(0xA000);
            c.addressWrite(0xA000, 1);
        }
        c.addressWrite(0x4000, 0x0D);
        c.addressRead(0xA000);
    }

    @Test void mbc3WithoutTimerIgnoresClockSelect() {
        Cartridge c = MapperTest.cart(0x13, 0, 3);
        c.addressWrite(0x0000, 0x0A);
        c.addressWrite(0x4000, 0x00);
        c.addressWrite(0xA000, 0x66);
        c.addressWrite(0x4000, 0x08);   // pas d'horloge : la banque RAM reste la 0
        assertEquals(0x66, c.addressRead(0xA000) & 0xFF);
        assertEquals(0x8000, c.getBatteryRam().length);
    }
}

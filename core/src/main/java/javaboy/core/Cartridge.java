package javaboy.core;

import java.util.Calendar;
import java.util.function.LongSupplier;

/** La cartouche : image ROM, RAM de sauvegarde et émulation des mappers
 *  (ROM only, ROM+RAM, MBC1, MBC2, MBC3 avec horloge temps réel, MBC5 avec rumble).
 *  Aucune entrée/sortie ici : la ROM arrive en {@code byte[]} et la RAM de sauvegarde
 *  est exposée en {@code byte[]} pour que l'hôte la persiste.
 *
 *  <p>Le {@code .sav} produit par {@link #getBatteryRam()} suit le format répandu
 *  (BGB, VBA-M, mGBA) : la RAM, puis pour les cartouches à horloge un pied de 48 octets
 *  (registres courants, registres latchés, horodatage Unix). */
public final class Cartridge {
    private static final int[][] ROM_SIZE_TABLE = {{0, 2}, {1, 4}, {2, 8}, {3, 16}, {4, 32},
            {5, 64}, {6, 128}, {7, 256}, {8, 512}, {0x52, 72}, {0x53, 80}, {0x54, 96}};

    public static final String[] CART_TYPE_TABLE = {
        "ROM Only", "ROM+MBC1", "ROM+MBC1+RAM", "ROM+MBC1+RAM+BATTERY", "Unknown",
        "ROM+MBC2", "ROM+MBC2+BATTERY", "Unknown", "ROM+RAM", "ROM+RAM+BATTERY", "Unknown",
        "Unsupported ROM+MMM01", "Unsupported ROM+MMM01+SRAM", "Unsupported ROM+MMM01+SRAM+BATTERY",
        "Unknown", "ROM+MBC3+TIMER+BATTERY", "ROM+MBC3+TIMER+RAM+BATTERY", "ROM+MBC3",
        "ROM+MBC3+RAM", "ROM+MBC3+RAM+BATTERY", "Unknown", "Unknown", "Unknown", "Unknown",
        "Unknown", "ROM+MBC5", "ROM+MBC5+RAM", "ROM+MBC5+RAM+BATTERY", "ROM+MBC5+RUMBLE",
        "ROM+MBC5+RUMBLE+RAM", "ROM+MBC5+RUMBLE+RAM+BATTERY"};

    /** Taille du pied RTC dans le .sav (horodatage sur 64 bits). */
    public static final int RTC_FOOTER_SIZE = 48;
    private static final int RTC_FOOTER_SIZE_OLD = 44; // ancienne variante, horodatage sur 32 bits

    /** Image ROM complète. */
    public final byte[] rom;
    /** RAM de la cartouche, dimensionnée d'après l'en-tête (au moins 8 Ko pour les accès). */
    public final byte[] ram;

    final int numBanks;
    final int cartType;
    /** Taille réelle de la RAM en octets (0 si aucune, 512 pour le MBC2). */
    private final int ramSize;
    private final int numRamBanks;
    int pageStart = 0x4000;
    int currentBank = 1;
    int savedBank = -1;
    int ramBank;
    int ramPageStart;
    boolean mbc1LargeRamMode = false;
    boolean ramEnabled;
    private volatile boolean rumble;

    private final String cartName;

    // ------------------------------------------------------------ horloge MBC3
    private int rtcSeconds, rtcMinutes, rtcHours, rtcDays; // rtcDays : 9 bits
    private boolean rtcHalt, rtcCarry;
    private final int[] rtcLatched = new int[5];          // S, M, H, DL, DH tels que lus par le jeu
    private int lastLatchWrite = -1;
    private long rtcLastMillis;
    private long rtcSubMillis;
    private LongSupplier clock = System::currentTimeMillis;

    /** Charge une ROM depuis ses octets. Retourne une erreur typée plutôt qu'une exception
     *  ou une boîte de dialogue. */
    public static CartridgeResult load(byte[] romBytes) {
        if (romBytes == null || romBytes.length < 0x150) {
            return new CartridgeResult.Error("Le fichier est trop petit pour être une ROM Game Boy.");
        }
        int banks = lookUpCartSize(romBytes[0x0148]);
        if (banks < 0) {
            return new CartridgeResult.Error("Taille de ROM inconnue dans l'en-tête (0x"
                    + Bits.hexByte(romBytes[0x0148]) + ").");
        }
        int type = romBytes[0x0147] & 0xFF;
        if (type >= CART_TYPE_TABLE.length || CART_TYPE_TABLE[type].startsWith("Un")) {
            return new CartridgeResult.Error("Type de cartouche non supporté : 0x" + Bits.hexByte(type));
        }
        if (romBytes.length < banks * 0x4000) {
            return new CartridgeResult.Error("ROM tronquée : " + romBytes.length + " octets pour "
                    + banks + " banques attendues.");
        }
        Cartridge c = new Cartridge(romBytes, banks, type);
        return new CartridgeResult.Ok(c, c.verifyChecksum());
    }

    private Cartridge(byte[] romBytes, int numBanks, int cartType) {
        this.numBanks = numBanks;
        this.cartType = cartType;
        this.rom = new byte[0x4000 * numBanks];
        System.arraycopy(romBytes, 0, rom, 0, rom.length);

        ramSize = isMbc2() ? 512 : ramSizeFromHeader(rom[0x149] & 0xFF);
        numRamBanks = Math.max(1, ramSize / 0x2000);
        ram = new byte[Math.max(ramSize, 0x2000)];

        StringBuilder s = new StringBuilder();
        for (int r = 0x0134; r < 0x0144; r++) {
            int ch = rom[r] & 0xFF;
            if (ch >= 32 && ch <= 127) s.append((char) ch);
        }
        cartName = s.toString().trim();

        // Sans .sav contenant l'horloge, on part de l'heure système (comportement historique
        // de JavaBoy) : les sauvegardes faites avec les versions précédentes restent cohérentes.
        Calendar now = Calendar.getInstance();
        rtcSeconds = now.get(Calendar.SECOND);
        rtcMinutes = now.get(Calendar.MINUTE);
        rtcHours = now.get(Calendar.HOUR_OF_DAY);
        rtcDays = now.get(Calendar.DAY_OF_YEAR) & 0x1FF;
        latchRtc();
        rtcLastMillis = clock.getAsLong();
    }

    /** Taille de RAM déclarée par l'octet 0x149, en octets (0 si code inconnu). */
    static int ramSizeFromHeader(int code) {
        return switch (code) {
            case 1 -> 0x800;      //   2 Ko
            case 2 -> 0x2000;     //   8 Ko
            case 3 -> 0x8000;     //  32 Ko
            case 4 -> 0x20000;    // 128 Ko
            case 5 -> 0x10000;    //  64 Ko
            default -> 0;
        };
    }

    public String getCartName() { return cartName; }
    public int getCartType() { return cartType; }
    public String getCartTypeName() { return CART_TYPE_TABLE[cartType]; }
    public int getNumBanks() { return numBanks; }
    public int getCurrentBank() { return currentBank; }
    public boolean isGbc() { return (rom[0x143] & 0x80) == 0x80; }

    private boolean isMbc1() { return cartType >= 1 && cartType <= 3; }
    private boolean isMbc2() { return cartType == 5 || cartType == 6; }
    private boolean isMbc3() { return cartType >= 0x0F && cartType <= 0x13; }
    private boolean isMbc5() { return cartType >= 0x19 && cartType <= 0x1E; }
    /** Cartouche avec horloge temps réel (MBC3+TIMER). */
    public boolean hasRtc() { return cartType == 0x0F || cartType == 0x10; }
    /** Cartouche avec moteur de vibration (MBC5+RUMBLE). */
    public boolean hasRumble() { return cartType >= 0x1C && cartType <= 0x1E; }

    /** Vrai tant que le jeu demande au moteur de vibrer (à relire à chaque image par l'hôte). */
    public boolean isRumbleActive() { return rumble; }

    // ------------------------------------------------------------ accès mémoire

    public final byte addressRead(int addr) {
        if (addr < 0x4000) return rom[addr];
        if (addr < 0x8000) return rom[pageStart + addr - 0x4000];
        // 0xA000-0xBFFF : RAM externe ou registres de l'horloge
        if (isMbc2()) {
            return ramEnabled ? (byte) ((ram[addr & 0x1FF] & 0x0F) | 0xF0) : (byte) 0xFF;
        }
        if (isMbc3() && ramBank >= 0x08) {
            return ramEnabled ? (byte) rtcLatched[ramBank - 0x08] : (byte) 0xFF;
        }
        if (ramSize == 0 || (needsRamEnable() && !ramEnabled)) return (byte) 0xFF;
        return ram[(addr - 0xA000 + ramPageStart) % ram.length];
    }

    /** ROM+RAM (0x08/0x09) n'a pas de registre d'activation : la RAM est toujours accessible. */
    private boolean needsRamEnable() { return cartType != 0x08 && cartType != 0x09; }

    public final void addressWrite(int addr, int data) {
        data &= 0xFF;
        if (addr >= 0xA000 && addr <= 0xBFFF) { writeExternalRam(addr, data); return; }
        if (addr >= 0x8000) return;

        if (isMbc1()) {
            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
            } else if (addr <= 0x3FFF) {
                int bankNo = data & 0x1F;
                if (bankNo == 0) bankNo = 1;
                mapRom((currentBank & 0x60) | bankNo);
            } else if (addr <= 0x5FFF) {
                if (mbc1LargeRamMode) selectRamBank(data & 0x03);
                else mapRom((currentBank & 0x1F) | ((data & 0x03) << 5));
            } else {
                mbc1LargeRamMode = (data & 1) == 1;
                if (!mbc1LargeRamMode) selectRamBank(0);
            }
        } else if (isMbc2()) {
            if (addr <= 0x3FFF) {
                if ((addr & 0x0100) == 0) {
                    ramEnabled = (data & 0x0F) == 0x0A;
                } else {
                    int bankNo = data & 0x0F;
                    if (bankNo == 0) bankNo = 1;
                    mapRom(bankNo);
                }
            }
        } else if (isMbc3()) {
            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
            } else if (addr <= 0x3FFF) {
                int bankNo = data & 0x7F;
                if (bankNo == 0) bankNo = 1;
                mapRom(bankNo);
            } else if (addr <= 0x5FFF) {
                if (data <= 0x03) selectRamBank(data);
                else if (hasRtc() && data >= 0x08 && data <= 0x0C) ramBank = data; // registre RTC
                // autres valeurs : ignorées par le MBC3
            } else {
                if (lastLatchWrite == 0x00 && data == 0x01) { updateRtc(); latchRtc(); }
                lastLatchWrite = data;
            }
        } else if (isMbc5()) {
            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
            } else if (addr <= 0x2FFF) {
                mapRom((currentBank & 0x100) | data);
            } else if (addr <= 0x3FFF) {
                mapRom((currentBank & 0x0FF) | ((data & 0x01) << 8));
            } else if (addr <= 0x5FFF) {
                if (hasRumble()) {
                    rumble = (data & 0x08) != 0;
                    selectRamBank(data & 0x07);
                } else {
                    selectRamBank(data & 0x0F);
                }
            }
        }
        // ROM only, ROM+RAM : aucun registre de mapper
    }

    private void writeExternalRam(int addr, int data) {
        if (isMbc2()) {
            if (ramEnabled) ram[addr & 0x1FF] = (byte) (data & 0x0F);
            return;
        }
        if (isMbc3() && ramBank >= 0x08) {
            if (ramEnabled) writeRtcRegister(ramBank - 0x08, data);
            return;
        }
        if (ramSize == 0 || (needsRamEnable() && !ramEnabled)) return;
        if (cartType == 0x00) return;
        ram[(addr - 0xA000 + ramPageStart) % ram.length] = (byte) data;
    }

    /** Sélectionne une banque de RAM ; les numéros trop grands bouclent comme sur le matériel. */
    private void selectRamBank(int bank) {
        ramBank = bank;
        ramPageStart = (bank % numRamBanks) * 0x2000;
    }

    /** Mappe une banque ROM à 0x4000 (les numéros au-delà de la taille de la ROM bouclent). */
    public void mapRom(int bankNo) {
        currentBank = bankNo;
        pageStart = 0x4000 * (bankNo % numBanks);
    }

    /** Restaure l'état du mapper (save states). */
    void restoreMapper(int bank, int ramBankNo, boolean enabled, boolean largeRamMode) {
        mapRom(bank);
        if (isMbc3() && ramBankNo >= 0x08) ramBank = ramBankNo;
        else selectRamBank(ramBankNo);
        ramEnabled = enabled;
        mbc1LargeRamMode = largeRamMode;
    }

    public void reset() {
        mapRom(1);
        selectRamBank(0);
        ramEnabled = false;
        mbc1LargeRamMode = false;
        rumble = false;
        lastLatchWrite = -1;
    }

    public void saveMapping() {
        if ((cartType != 0) && (savedBank == -1)) savedBank = currentBank;
    }

    public void restoreMapping() {
        if (savedBank != -1) {
            addressWrite(0x2000, savedBank);
            savedBank = -1;
        }
    }

    /** Écriture directe dans la ROM (débogueur uniquement). */
    public void debuggerAddressWrite(int addr, int data) {
        if (addr < 0x4000) {
            rom[addr] = (byte) data;
        } else {
            rom[pageStart + addr - 0x4000] = (byte) data;
        }
    }

    // ------------------------------------------------------------ horloge temps réel (MBC3)

    /** Horloge en millisecondes (tests). */
    void setClock(LongSupplier millis) {
        clock = millis;
        rtcLastMillis = millis.getAsLong();
        rtcSubMillis = 0;
    }

    /** Fait avancer l'horloge selon le temps écoulé. Appelé une fois par image par le CPU. */
    public void update() {
        if (!hasRtc()) return;
        updateRtc();
    }

    private void updateRtc() {
        long now = clock.getAsLong();
        long delta = now - rtcLastMillis;
        rtcLastMillis = now;
        if (delta <= 0 || rtcHalt) return;      // horloge arrêtée, ou heure système reculée
        rtcSubMillis += delta;
        long secs = rtcSubMillis / 1000;
        rtcSubMillis %= 1000;
        advanceRtc(secs);
    }

    /** Ajoute des secondes au compteur ; au-delà de 511 jours, le bit de retenue est levé. */
    private void advanceRtc(long secs) {
        if (secs <= 0) return;
        long total = rtcSeconds + 60L * (rtcMinutes + 60L * (rtcHours + 24L * rtcDays)) + secs;
        long days = total / 86400;
        if (days > 0x1FF) { rtcCarry = true; days &= 0x1FF; }
        long rest = total % 86400;
        rtcDays = (int) days;
        rtcHours = (int) (rest / 3600);
        rtcMinutes = (int) (rest / 60 % 60);
        rtcSeconds = (int) (rest % 60);
    }

    private int rtcDaysHigh() { return ((rtcDays >> 8) & 1) | (rtcHalt ? 0x40 : 0) | (rtcCarry ? 0x80 : 0); }

    private void latchRtc() {
        rtcLatched[0] = rtcSeconds;
        rtcLatched[1] = rtcMinutes;
        rtcLatched[2] = rtcHours;
        rtcLatched[3] = rtcDays & 0xFF;
        rtcLatched[4] = rtcDaysHigh();
    }

    private void writeRtcRegister(int reg, int data) {
        updateRtc();
        switch (reg) {
            case 0 -> { rtcSeconds = data & 0x3F; rtcSubMillis = 0; }
            case 1 -> rtcMinutes = data & 0x3F;
            case 2 -> rtcHours = data & 0x1F;
            case 3 -> rtcDays = (rtcDays & 0x100) | data;
            case 4 -> {
                rtcDays = (rtcDays & 0xFF) | ((data & 1) << 8);
                rtcHalt = (data & 0x40) != 0;
                rtcCarry = (data & 0x80) != 0;
            }
            default -> { return; }
        }
        rtcLatched[reg] = reg == 4 ? rtcDaysHigh() : (reg == 3 ? rtcDays & 0xFF : data & (reg == 2 ? 0x1F : 0x3F));
    }

    private void writeRtcFooter(byte[] out, int off) {
        updateRtc();
        int[] live = {rtcSeconds, rtcMinutes, rtcHours, rtcDays & 0xFF, rtcDaysHigh()};
        for (int i = 0; i < 5; i++) putLe(out, off + 4 * i, live[i], 4);
        for (int i = 0; i < 5; i++) putLe(out, off + 20 + 4 * i, rtcLatched[i], 4);
        putLe(out, off + 40, clock.getAsLong() / 1000, 8);
    }

    private void readRtcFooter(byte[] in, int off, int footerSize) {
        rtcSeconds = (int) getLe(in, off, 4) & 0x3F;
        rtcMinutes = (int) getLe(in, off + 4, 4) & 0x3F;
        rtcHours = (int) getLe(in, off + 8, 4) & 0x1F;
        int dh = (int) getLe(in, off + 16, 4);
        rtcDays = ((int) getLe(in, off + 12, 4) & 0xFF) | ((dh & 1) << 8);
        rtcHalt = (dh & 0x40) != 0;
        rtcCarry = (dh & 0x80) != 0;
        for (int i = 0; i < 5; i++) rtcLatched[i] = (int) getLe(in, off + 20 + 4 * i, 4) & 0xFF;
        long savedAt = getLe(in, off + 40, footerSize == RTC_FOOTER_SIZE ? 8 : 4);
        long nowMillis = clock.getAsLong();
        rtcLastMillis = nowMillis;
        rtcSubMillis = 0;
        // Le temps passé émulateur fermé compte, comme sur une vraie cartouche avec sa pile.
        if (!rtcHalt) advanceRtc(nowMillis / 1000 - savedAt);
    }

    private static void putLe(byte[] b, int off, long v, int n) {
        for (int i = 0; i < n; i++) b[off + i] = (byte) (v >>> (8 * i));
    }

    private static long getLe(byte[] b, int off, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v |= (b[off + i] & 0xFFL) << (8 * i);
        return v;
    }

    // ------------------------------------------------------------ sauvegarde (.sav)

    /** Nombre de banques de RAM de 8 Ko (1 pour les RAM de 2 Ko et le MBC2). */
    public int getNumRAMBanks() { return ramSize == 0 ? 0 : numRamBanks; }

    /** Taille de la RAM de la cartouche en octets (sans le pied RTC). */
    public int getRamSize() { return ramSize; }

    /** Taille du .sav en octets : la RAM, plus le pied RTC pour les cartouches à horloge
     *  (0 si la cartouche n'a pas de pile). */
    public int getBatteryRamSize() {
        if (!canSave()) return 0;
        return ramSize + (hasRtc() ? RTC_FOOTER_SIZE : 0);
    }

    public boolean canSave() {
        return (cartType == 3) || (cartType == 6) || (cartType == 9) || (cartType == 0x0F) || (cartType == 0x10)
                || (cartType == 0x13) || (cartType == 0x1B) || (cartType == 0x1E);
    }

    /** Contenu du .sav : copie de la RAM, puis l'horloge pour les cartouches MBC3+TIMER. */
    public byte[] getBatteryRam() {
        byte[] out = new byte[getBatteryRamSize()];
        if (out.length == 0) return out;
        System.arraycopy(ram, 0, out, 0, ramSize);
        if (hasRtc()) writeRtcFooter(out, ramSize);
        return out;
    }

    /** Restaure un .sav lu par l'hôte. Un .sav sans horloge (anciennes versions) est accepté :
     *  l'horloge garde alors l'heure système. */
    public void loadBatteryRam(byte[] data) {
        if (data == null) return;
        System.arraycopy(data, 0, ram, 0, Math.min(data.length, ramSize));
        if (!hasRtc()) return;
        int footer = data.length - ramSize;
        if (footer >= RTC_FOOTER_SIZE) readRtcFooter(data, ramSize, RTC_FOOTER_SIZE);
        else if (footer >= RTC_FOOTER_SIZE_OLD) readRtcFooter(data, ramSize, RTC_FOOTER_SIZE_OLD);
    }

    public boolean verifyChecksum() {
        int checkSum = (Bits.unsign(rom[0x14E]) << 8) + Bits.unsign(rom[0x14F]);
        int total = 0;
        for (int r = 0; r < rom.length; r++) {
            if ((r != 0x14E) && (r != 0x14F)) {
                total = (total + Bits.unsign(rom[r])) & 0x0000FFFF;
            }
        }
        return checkSum == total;
    }

    static int lookUpCartSize(int sizeByte) {
        for (int[] entry : ROM_SIZE_TABLE) {
            if (entry[0] == sizeByte) return entry[1];
        }
        return -1;
    }
}

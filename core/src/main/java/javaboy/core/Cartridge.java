package javaboy.core;

import java.util.Calendar;

/** La cartouche : image ROM, RAM de sauvegarde et émulation des mappers
 *  (ROM only, MBC1, MBC2, MBC3 avec horloge, MBC5).
 *  Aucune entrée/sortie ici : la ROM arrive en {@code byte[]} et la RAM de sauvegarde
 *  est exposée en {@code byte[]} pour que l'hôte la persiste. */
public final class Cartridge {
    private static final int[][] ROM_SIZE_TABLE = {{0, 2}, {1, 4}, {2, 8}, {3, 16}, {4, 32},
            {5, 64}, {6, 128}, {7, 256}, {0x52, 72}, {0x53, 80}, {0x54, 96}};

    public static final String[] CART_TYPE_TABLE = {
        "ROM Only", "ROM+MBC1", "ROM+MBC1+RAM", "ROM+MBC1+RAM+BATTERY", "Unknown",
        "ROM+MBC2", "ROM+MBC2+BATTERY", "Unknown", "ROM+RAM", "ROM+RAM+BATTERY", "Unknown",
        "Unsupported ROM+MMM01", "Unsupported ROM+MMM01+SRAM", "Unsupported ROM+MMM01+SRAM+BATTERY",
        "Unknown", "ROM+MBC3+TIMER+BATTERY", "ROM+MBC3+TIMER+RAM+BATTERY", "ROM+MBC3",
        "ROM+MBC3+RAM", "ROM+MBC3+RAM+BATTERY", "Unknown", "Unknown", "Unknown", "Unknown",
        "Unknown", "ROM+MBC5", "ROM+MBC5+RAM", "ROM+MBC5+RAM+BATTERY", "ROM+MBC5+RUMBLE",
        "ROM+MBC5+RUMBLE+RAM", "ROM+MBC5+RUMBLE+RAM+BATTERY"};

    private static final int SECONDS = 0, MINUTES = 1, HOURS = 2, DAYS_LO = 3, DAYS_HI = 4;

    /** Image ROM complète. */
    public final byte[] rom;
    /** RAM de la cartouche (jusqu'à 16 banques de 8 Ko). */
    public final byte[] ram = new byte[0x10000];

    final int numBanks;
    final int cartType;
    int pageStart = 0x4000;
    int currentBank = 1;
    int savedBank = -1;
    int ramBank;
    int ramPageStart;
    boolean mbc1LargeRamMode = false;
    boolean ramEnabled;

    private final String cartName;
    private final int[] RTCReg = new int[5];
    private long lastSecondIncrement;

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

        StringBuilder s = new StringBuilder();
        for (int r = 0x0134; r < 0x0144; r++) {
            int ch = rom[r] & 0xFF;
            if (ch >= 32 && ch <= 127) s.append((char) ch);
        }
        cartName = s.toString().trim();

        Calendar rightNow = Calendar.getInstance();
        int days = rightNow.get(Calendar.DAY_OF_YEAR);
        RTCReg[SECONDS] = rightNow.get(Calendar.SECOND);
        RTCReg[MINUTES] = rightNow.get(Calendar.MINUTE);
        RTCReg[HOURS] = rightNow.get(Calendar.HOUR_OF_DAY);
        RTCReg[DAYS_LO] = days & 0x00FF;
        RTCReg[DAYS_HI] = (days & 0x01FF) >> 8;
        lastSecondIncrement = System.currentTimeMillis();
    }

    public String getCartName() { return cartName; }
    public int getCartType() { return cartType; }
    public String getCartTypeName() { return CART_TYPE_TABLE[cartType]; }
    public int getNumBanks() { return numBanks; }
    public int getCurrentBank() { return currentBank; }
    public boolean isGbc() { return (rom[0x143] & 0x80) == 0x80; }

    /** Met à jour l'horloge temps réel (MBC3) à partir de l'heure système. */
    public void update() {
        long millisSinceLastUpdate = System.currentTimeMillis() - lastSecondIncrement;
        while (millisSinceLastUpdate > 1000) {
            millisSinceLastUpdate -= 1000;
            RTCReg[SECONDS]++;
            if (RTCReg[SECONDS] == 60) {
                RTCReg[MINUTES]++;
                RTCReg[SECONDS] = 0;
                if (RTCReg[MINUTES] == 60) {
                    RTCReg[HOURS]++;
                    RTCReg[MINUTES] = 0;
                    if (RTCReg[HOURS] == 24) {
                        if (RTCReg[DAYS_LO] == 255) {
                            RTCReg[DAYS_LO] = 0;
                            RTCReg[DAYS_HI] = 1;
                        } else {
                            RTCReg[DAYS_LO]++;
                        }
                        RTCReg[HOURS] = 0;
                    }
                }
            }
            lastSecondIncrement = System.currentTimeMillis();
        }
    }

    public final byte addressRead(int addr) {
      if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
       switch (cartType) {
        case 0x0F :
        case 0x10 :
        case 0x11 :
        case 0x12 :
        case 0x13 : {    /* MBC3 */
         if (ramBank >= 0x04) {
          return (byte) RTCReg[ramBank - 0x08];
         } else {
          return ram[addr - 0xA000 + ramPageStart];
         }
        }
        default : {
         return ram[addr - 0xA000 + ramPageStart];
        }
       }
      } if (addr < 0x4000) {
       return (byte) (rom[addr]);
      } else {
       return (byte) (rom[pageStart + addr - 0x4000]);
      }
    //  }
    }

    public final void addressWrite(int addr, int data) {
     int ramAddress = 0;
     switch (cartType) {
      case 0 : 
       break;
      case 1 : 
      case 2 :
      case 3 :
       if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
        if (ramEnabled) {
         ramAddress = addr - 0xA000 + ramPageStart;
         ram[ramAddress] = (byte) data;
        }
       } if ((addr >= 0x2000) && (addr <= 0x3FFF)) {
        int bankNo = data & 0x1F;
        if (bankNo == 0) bankNo = 1;
        mapRom((currentBank & 0x60) | bankNo);
       } else if ((addr >= 0x6000) && (addr <= 0x7FFF)) {
        if ((data & 1) == 1) {
         mbc1LargeRamMode = true;
    //      ram = new byte[0x8000];
        } else {
         mbc1LargeRamMode = false;
    //      ram = new byte[0x2000];
        }
       } else if (addr <= 0x1FFF) {
        if ((data & 0x0F) == 0x0A) {
         ramEnabled = true;
        } else {
         ramEnabled = false;
        }
       } else if ((addr <= 0x5FFF) && (addr >= 0x4000)) {
        if (mbc1LargeRamMode) {
         ramBank = (data & 0x03);
         ramPageStart = ramBank * 0x2000;
        } else {
         mapRom((currentBank & 0x1F) | ((data & 0x03) << 5));
        }
       }
       break;
      case 5 :
      case 6 :
       if ((addr >= 0x2000) && (addr <= 0x3FFF) && ((addr & 0x0100) != 0) ) {
        int bankNo = data & 0x1F;
        if (bankNo == 0) bankNo = 1;
        mapRom(bankNo);
       }
       if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
        if (ramEnabled) ram[addr - 0xA000 + ramPageStart] = (byte) data;
       }
       break;
      case 0x0F :
      case 0x10 :
      case 0x11 :
      case 0x12 :
      case 0x13 :    
       // Select ROM bank
       if ((addr >= 0x2000) && (addr <= 0x3FFF)) {
        int bankNo = data & 0x7F;
        if (bankNo == 0) bankNo = 1;
        mapRom(bankNo);
       } else if ((addr <= 0x5FFF) && (addr >= 0x4000)) {
       // Select RAM bank
        ramBank = data;
        if (ramBank < 0x04) {
         ramPageStart = ramBank * 0x2000;
        }
       } 
       if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
        // Let the game write to RAM
         if (ramBank <= 0x03) {
         ram[addr - 0xA000 + ramPageStart] = (byte) data;
        } else {
        // Write to realtime clock registers
        RTCReg[ramBank - 0x08] = data;
       }
      }
       break;
      case 0x19 :
      case 0x1A :
      case 0x1B :
      case 0x1C :
      case 0x1D :
      case 0x1E :
       if ((addr >= 0x2000) && (addr <= 0x2FFF)) {
        int bankNo = (currentBank & 0xFF00) | data;
        mapRom(bankNo);
       }
       if ((addr >= 0x3000) && (addr <= 0x3FFF)) {
        int bankNo = (currentBank & 0x00FF) | ((data & 0x01) << 8);
        mapRom(bankNo);
       }
       if ((addr >= 0x4000) && (addr <= 0x5FFF)) {
        ramBank = (data & 0x07);
        ramPageStart = ramBank * 0x2000;
       }
       if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
        ram[addr - 0xA000 + ramPageStart] = (byte) data;
       }
       break;
     }
    }

    /** Mappe une banque ROM à 0x4000. */
    public void mapRom(int bankNo) {
        currentBank = bankNo;
        pageStart = 0x4000 * bankNo;
    }

    public void reset() {
        mapRom(1);
        ramBank = 0;
        ramPageStart = 0;
        ramEnabled = false;
        mbc1LargeRamMode = false;
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

    public int getNumRAMBanks() {
        switch (rom[0x149]) {
            case 0: return 0;
            case 1:
            case 2: return 1;
            case 3: return 4;
            case 4: return 16;
            default: return 0;
        }
    }

    /** Taille en octets de la RAM à sauvegarder (0 si pas de pile). */
    public int getBatteryRamSize() {
        if (!canSave()) return 0;
        if (cartType == 6) return 512;
        return getNumRAMBanks() * 8192;
    }

    public boolean canSave() {
        return (cartType == 3) || (cartType == 9) || (cartType == 0x1B) || (cartType == 0x1E)
                || (cartType == 6) || (cartType == 0x10) || (cartType == 0x13);
    }

    /** Copie de la RAM de sauvegarde (à écrire dans un fichier .sav par l'hôte). */
    public byte[] getBatteryRam() {
        byte[] out = new byte[getBatteryRamSize()];
        System.arraycopy(ram, 0, out, 0, out.length);
        return out;
    }

    /** Restaure la RAM de sauvegarde depuis un .sav lu par l'hôte. */
    public void loadBatteryRam(byte[] data) {
        if (data == null) return;
        System.arraycopy(data, 0, ram, 0, Math.min(data.length, getBatteryRamSize()));
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

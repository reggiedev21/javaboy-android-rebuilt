package javaboy.core;

import java.nio.file.Path;
import java.util.List;

/** Inventaire des ROMs locales : type de cartouche, tailles, drapeau GBC, checksum, et
 *  avertissements sur les cas que le cœur ne gère pas encore.
 *
 *  <pre>java -cp build/test-classes javaboy.core.RomInfo [dossier]</pre>
 *  Sans argument, utilise le dossier décrit dans {@link RomFiles}. */
public final class RomInfo {
    private RomInfo() {}

    /** Taille de RAM déclarée par l'octet 0x149, en Ko (-1 : code inconnu). */
    static int declaredRamKb(int code) {
        return switch (code) { case 0 -> 0; case 1 -> 2; case 2 -> 8; case 3 -> 32; case 4 -> 128; case 5 -> 64; default -> -1; };
    }

    /** Lignes d'avertissement pour une cartouche (vide si rien à signaler). */
    static List<String> warnings(Cartridge c) {
        List<String> w = new java.util.ArrayList<>();
        int code = c.rom[0x149] & 0xFF;
        int kb = declaredRamKb(code);
        if (kb < 0) w.add("code RAM 0x149 inconnu : " + code);
        if (kb * 1024 > c.ram.length)
            w.add("RAM déclarée " + kb + " Ko > Cartridge.ram (" + c.ram.length / 1024 + " Ko)");
        if (c.canSave() && c.getBatteryRamSize() == 0) w.add("cartouche à pile mais .sav vide");
        return w;
    }

    /** Informations utiles à l'hôte (pas des problèmes). */
    static List<String> notes(Cartridge c) {
        List<String> n = new java.util.ArrayList<>();
        if (c.hasRtc()) n.add("horloge temps réel, sauvegardée dans le .sav (+" + Cartridge.RTC_FOOTER_SIZE + " octets)");
        if (c.hasRumble()) n.add("rumble : relier Emulator.isRumbling() au vibreur");
        return n;
    }

    public static void main(String[] args) throws Exception {
        Path dir = args.length > 0 ? Path.of(args[0]) : RomFiles.romDir();
        List<Path> roms = RomFiles.allRoms(dir);
        if (roms.isEmpty()) {
            System.out.println("Aucune ROM trouvée. Dossiers essayés : " + RomFiles.candidateDirs());
            return;
        }
        System.out.printf("%-34s %-30s %7s %7s %-4s %s%n", "Fichier", "Type", "ROM Ko", "RAM Ko", "GBC", "Checksum");
        int problems = 0;
        for (Path p : roms) {
            String name = p.getFileName().toString();
            try {
                switch (Cartridge.load(RomFiles.read(p))) {
                    case CartridgeResult.Ok ok -> {
                        Cartridge c = ok.cartridge();
                        int flag = c.rom[0x143] & 0xFF;
                        String gbc = flag == 0xC0 ? "seul" : flag == 0x80 ? "oui" : "non";
                        System.out.printf("%-34s %-30s %7d %7d %-4s %s%n", abbreviate(name, 34),
                                "0x" + Bits.hexByte(c.getCartType()) + " " + c.getCartTypeName(),
                                c.getNumBanks() * 16, Math.max(0, declaredRamKb(c.rom[0x149] & 0xFF)), gbc,
                                ok.checksumValid() ? "ok" : "INVALIDE");
                        for (String w : warnings(c)) { System.out.println("    ! " + w); problems++; }
                        for (String note : notes(c)) System.out.println("    · " + note);
                    }
                    case CartridgeResult.Error err -> {
                        System.out.printf("%-34s REFUSÉE : %s%n", abbreviate(name, 34), err.message());
                        problems++;
                    }
                }
            } catch (Exception e) {
                System.out.printf("%-34s ILLISIBLE : %s%n", abbreviate(name, 34), e);
                problems++;
            }
        }
        System.out.println(roms.size() + " ROM(s), " + problems + " point(s) à regarder.");
    }

    private static String abbreviate(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
}

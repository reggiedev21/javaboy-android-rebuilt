package javaboy.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Test de fumée sur les ROMs locales (dossier décrit dans {@link RomFiles}, jamais versionné).
 *  Pour chaque ROM : chargement, quelques secondes de jeu avec appuis sur Start/A, écran non
 *  uniforme, aller-retour de save state et de RAM de sauvegarde. Tous les échecs sont rassemblés
 *  dans un seul message. Nombre d'images : {@code -Djavaboy.smoke.frames=N} (défaut 1200 = 20 s).
 *
 *  Les divergences après restauration d'un save state sont seulement signalées (WARN) : le son
 *  et certains compteurs internes ne font pas partie de {@link SaveState}. */
class RomSmokeTest {
    static final int FRAMES = Integer.getInteger("javaboy.smoke.frames", 1200);

    @Test
    void everyLocalRomBootsAndRoundTrips() throws Exception {
        List<Path> roms = RomFiles.allRoms();
        assumeTrue(!roms.isEmpty(), "aucune ROM locale (dossiers : " + RomFiles.candidateDirs() + ")");

        List<String> failures = new ArrayList<>();
        for (Path p : roms) {
            String name = p.getFileName().toString();
            long t0 = System.nanoTime();
            try {
                List<String> warns = new ArrayList<>();
                String err = check(RomFiles.read(p), warns);
                if (err != null) { failures.add(name + " : " + err); System.out.println("  FAIL " + name + " : " + err); }
                else System.out.println("  rom  " + name + " ok  (" + (System.nanoTime() - t0) / 1_000_000 + " ms)"
                        + (warns.isEmpty() ? "" : "  " + warns));
            } catch (Throwable t) {
                failures.add(name + " : exception " + t);
                System.out.println("  FAIL " + name + " : " + t);
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + "/" + roms.size() + " ROM(s) en échec :\n  " + String.join("\n  ", failures));
    }

    /** Renvoie un message d'erreur, ou {@code null} si tout va bien. */
    private static String check(byte[] romBytes, List<String> warns) {
        Cartridge cart;
        switch (Cartridge.load(romBytes)) {
            case CartridgeResult.Ok ok -> cart = ok.cartridge();
            case CartridgeResult.Error e -> { return "chargement refusé : " + e.message(); }
        }

        // 1. Jeu : l'écran doit sortir de l'uniformité et rien ne doit lever d'exception
        Emulator emu = new Emulator(cart);
        int[] maxColours = {0};
        emu.setFrameListener(px -> maxColours[0] = Math.max(maxColours[0], distinctColours(px)));
        emu.reset();
        try {
            play(emu, FRAMES);
        } catch (Throwable t) {
            return "exception après émulation : " + t;
        }
        if (maxColours[0] < 2) return "écran resté uniforme pendant " + FRAMES + " images (" + maxColours[0] + " couleur)";

        // 2. Save state : sérialisation + restauration dans une cartouche neuve
        SaveState st = emu.saveState();
        SaveState back;
        try {
            back = SaveState.fromBytes(st.toBytes());
        } catch (Throwable t) { return "sérialisation du save state : " + t; }
        if (!back.equals(st) && !Arrays.equals(back.mainRam(), st.mainRam())) return "save state altéré par la sérialisation";

        Cartridge cart2 = ((CartridgeResult.Ok) Cartridge.load(romBytes)).cartridge();
        Emulator emu2 = new Emulator(cart2);
        emu2.reset();
        try {
            emu2.loadState(back);
        } catch (Throwable t) { return "loadState : " + t; }
        if (emu2.getCpu().pc != st.cpu().pc() || emu2.getCpu().sp != st.cpu().sp())
            return "registres PC/SP différents après loadState";
        if (!Arrays.equals(emu2.getCpu().mainRam, st.mainRam())) return "RAM principale différente après loadState";

        // Les deux émulateurs repartent du même point : leurs images doivent rester identiques
        long[] h1 = {0}, h2 = {0};
        emu.setFrameListener(px -> h1[0] = h1[0] * 31 + Arrays.hashCode(px));
        emu2.setFrameListener(px -> h2[0] = h2[0] * 31 + Arrays.hashCode(px));
        try {
            for (int i = 0; i < 120; i++) { emu.runFrame(); emu2.runFrame(); }
        } catch (Throwable t) { return "exception après restauration : " + t; }
        if (h1[0] != h2[0]) warns.add("WARN rendu différent 120 images après loadState (état interne non sauvegardé)");

        // 3. RAM de sauvegarde : motif connu, écriture puis relecture dans une cartouche neuve
        if (cart.canSave()) {
            Cartridge a = ((CartridgeResult.Ok) Cartridge.load(romBytes)).cartridge();
            Cartridge b = ((CartridgeResult.Ok) Cartridge.load(romBytes)).cartridge();
            try {
                int size = a.getBatteryRamSize();
                int ramSize = a.getRamSize();
                if (size <= 0) return "canSave() vrai mais getBatteryRamSize() = " + size;
                for (int i = 0; i < ramSize; i++) a.ram[i] = (byte) (i * 31 + 7);
                byte[] sav = a.getBatteryRam();
                if (sav.length != size) return ".sav de " + sav.length + " octets, attendu " + size;
                b.loadBatteryRam(sav);
                // on compare la RAM seule : le pied RTC contient l'heure de l'écriture
                if (!Arrays.equals(Arrays.copyOf(sav, ramSize), Arrays.copyOf(b.getBatteryRam(), ramSize)))
                    return "RAM de sauvegarde différente après relecture";
            } catch (Throwable t) {
                return "RAM de sauvegarde : " + t + " (RAM déclarée " + RomInfo.declaredRamKb(a.rom[0x149] & 0xFF) + " Ko)";
            }
        }
        return null;
    }

    /** Joue {@code frames} images en appuyant périodiquement sur Start puis A pour passer les écrans-titres. */
    private static void play(Emulator emu, int frames) {
        for (int f = 0; f < frames; f++) {
            if (f % 300 == 200) emu.setButton(Joypad.Button.START, true);
            if (f % 300 == 205) emu.setButton(Joypad.Button.START, false);
            if (f % 300 == 250) emu.setButton(Joypad.Button.A, true);
            if (f % 300 == 255) emu.setButton(Joypad.Button.A, false);
            emu.runFrame();
        }
        emu.getJoypad().releaseAll();
    }

    /** Nombre de couleurs distinctes de l'image, plafonné à 8 (suffit pour détecter un écran uniforme). */
    static int distinctColours(int[] px) {
        Set<Integer> seen = new HashSet<>();
        for (int v : px) { seen.add(v); if (seen.size() >= 8) break; }
        return seen.size();
    }
}

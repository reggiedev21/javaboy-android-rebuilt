package javaboy.android;

import android.content.Context;
import javaboy.core.Cartridge;
import javaboy.core.Emulator;
import javaboy.core.SaveState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Persistance des RAM de cartouche (.sav) et des save states (.state) dans le stockage
 *  interne, indexés par nom de cartouche + type (pas par URI, qui peut changer). */
public final class SaveStore {
    private final File savDir, stateDir;

    public SaveStore(Context ctx) {
        savDir = new File(ctx.getFilesDir(), "saves"); savDir.mkdirs();
        stateDir = new File(ctx.getFilesDir(), "states"); stateDir.mkdirs();
    }

    private static String key(Cartridge c) {
        return c.getCartName().replaceAll("[^A-Za-z0-9_-]", "_") + "_" + Integer.toHexString(c.getCartType());
    }

    public void loadBatteryRam(Cartridge c) {
        if (!c.canSave()) return;
        File f = new File(savDir, key(c) + ".sav");
        if (!f.exists()) return;
        try { c.loadBatteryRam(Files.readAllBytes(f.toPath())); } catch (IOException ignored) { }
    }

    /** Écrit le .sav à partir d'une copie prise entre deux images (sûr pendant l'émulation). */
    public void saveBatteryRam(Emulator e) {
        Cartridge c = e.getCartridge();
        if (!c.canSave()) return;
        try { Files.write(new File(savDir, key(c) + ".sav").toPath(), e.batteryRam()); } catch (IOException ignored) { }
    }

    public File stateFile(Cartridge c, int slot) { return new File(stateDir, key(c) + "_" + slot + ".state"); }

    public void writeState(Cartridge c, int slot, SaveState st) throws IOException {
        Files.write(stateFile(c, slot).toPath(), st.toBytes());
    }

    public SaveState readState(Cartridge c, int slot) throws IOException {
        File f = stateFile(c, slot);
        if (!f.exists()) return null;
        return SaveState.fromBytes(Files.readAllBytes(f.toPath()));
    }
}

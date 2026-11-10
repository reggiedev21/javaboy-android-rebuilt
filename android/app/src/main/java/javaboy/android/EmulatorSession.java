package javaboy.android;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;
import javaboy.core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/** Partie en cours : émulateur, thread d'émulation et son.
 *
 *  <p>Une seule instance par processus, indépendante des écrans : changer de coque Game Boy
 *  lance une autre Activity, la partie doit continuer. L'écran visible se branche avec
 *  {@link #attachScreen} et pilote {@link #resume}/{@link #pause}.
 *
 *  <p>Toutes les méthodes sont appelées depuis le thread principal, sauf {@link #onFrame}
 *  (thread d'émulation) et {@link #setButton} (tout thread). */
public final class EmulatorSession {
    private static final int SLOT = 0;
    private static final String ASSET_PATH = "/android_asset/";

    private static EmulatorSession instance;

    public static synchronized EmulatorSession get(Context ctx) {
        if (instance == null) instance = new EmulatorSession(ctx.getApplicationContext());
        return instance;
    }

    /** URI désignant une ROM embarquée dans les assets (banque de jeux de démonstration). */
    public static Uri assetUri(String assetName) {
        return new Uri.Builder().scheme("file").authority("").path(ASSET_PATH + assetName).build();
    }

    private final Context app;
    private final SaveStore saveStore;
    private final int[] filtered = new int[GraphicsChip.WIDTH * GraphicsChip.HEIGHT];
    private volatile IntUnaryOperator filter;
    private volatile Consumer<int[]> screen;
    private volatile Emulator emulator;
    private EmulationThread thread;
    private AndroidAudioOutput audio;
    private Settings settings;
    private boolean foreground;

    private EmulatorSession(Context app) {
        this.app = app;
        saveStore = new SaveStore(app);
        settings = Settings.load(app);
        filter = settings.filter();
    }

    public boolean hasGame() { return emulator != null; }
    public Settings settings() { return settings; }

    // ------------------------------------------------------------ chargement

    /** Charge une ROM et démarre la partie ; affiche un message en cas d'échec.
     *  @return vrai si la partie a démarré. */
    public boolean load(Uri uri) {
        Cartridge cart;
        try {
            byte[] bytes;
            try (InputStream in = open(uri)) { bytes = RomArchive.unwrap(readFully(in)); }
            CartridgeResult result = Cartridge.load(bytes);
            if (result instanceof CartridgeResult.Error err) throw new IOException(err.message());
            CartridgeResult.Ok ok = (CartridgeResult.Ok) result;
            if (!ok.checksumValid()) Toast.makeText(app, R.string.bad_checksum, Toast.LENGTH_SHORT).show();
            cart = ok.cartridge();
        } catch (IOException | SecurityException e) {
            Toast.makeText(app, app.getString(R.string.invalid_rom, e.getMessage()), Toast.LENGTH_LONG).show();
            return false;
        }
        start(cart, uri);
        return true;
    }

    private InputStream open(Uri uri) throws IOException {
        String path = uri.getPath();
        if ("file".equals(uri.getScheme()) && path != null && path.startsWith(ASSET_PATH)) {
            return app.getAssets().open(path.substring(ASSET_PATH.length()));
        }
        InputStream in = app.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("flux nul");
        return in;
    }

    /** InputStream.readAllBytes() n'existe qu'à partir d'Android 13 (minSdk 26). */
    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buffer = new byte[1 << 16];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private void start(Cartridge cart, Uri uri) {
        stop();
        saveStore.loadBatteryRam(cart);
        AndroidAudioOutput out = new AndroidAudioOutput(Emulator.DEFAULT_SAMPLE_RATE);
        Emulator emu = new Emulator(cart, new Joypad(), out, Emulator.DEFAULT_SAMPLE_RATE);
        emu.setGbcAllowed(settings.gbcAllowed());
        emu.reset();
        emu.setFrameListener(this::onFrame);
        emulator = emu;
        audio = out;
        applySettings(settings);
        // Démarre en pause : la partie ne tourne que lorsqu'une coque est à l'écran.
        out.pause();
        thread = new EmulationThread(emu);
        thread.setSpeedPercent(settings.speedPercent());
        thread.setPaused(true);
        thread.start();
        new RomLibrary(app).recordPlay(uri, cart.getCartName(), cart.getCartTypeName(), cart.isGbc());
        if (foreground) resume();
    }

    private void stop() {
        if (thread != null) { thread.shutdown(); thread = null; }
        if (emulator != null) { saveStore.saveBatteryRam(emulator); emulator = null; }
        if (audio != null) { audio.release(); audio = null; }
    }

    // ------------------------------------------------------------ écran et manette

    /** Destinataire des images 160×144 ARGB, appelé sur le thread d'émulation ; le tampon
     *  est réutilisé d'une image à l'autre, le destinataire doit le copier. */
    public void attachScreen(Consumer<int[]> s) { screen = s; }

    /** Ne détache que si {@code s} est encore l'écran courant (une autre coque a pu prendre le relais). */
    public void detachScreen(Consumer<int[]> s) { if (screen == s) screen = null; }

    private void onFrame(int[] pixels) {
        Consumer<int[]> s = screen;
        if (s == null) return;
        IntUnaryOperator f = filter;
        if (f == Filters.IDENTITY) {
            s.accept(pixels);
        } else {
            Filters.apply(f, pixels, filtered);
            s.accept(filtered);
        }
    }

    public void setButton(Joypad.Button button, boolean pressed) {
        Emulator e = emulator;
        if (e != null) e.setButton(button, pressed);
    }

    // ------------------------------------------------------------ cycle de vie

    /** Une coque passe au premier plan : relit les réglages et relance la partie. */
    public void resume() {
        foreground = true;
        Settings s = Settings.load(app);
        if (!s.equals(settings)) applySettings(s);
        if (thread != null) { thread.setPaused(false); audio.resume(); }
    }

    /** La coque quitte le premier plan : pause, boutons relâchés, RAM de cartouche sauvegardée. */
    public void pause() {
        foreground = false;
        if (thread == null) return;
        thread.setPaused(true);
        audio.pause();
        emulator.getJoypad().releaseAll();
        saveStore.saveBatteryRam(emulator);
    }

    private void applySettings(Settings s) {
        settings = s;
        filter = s.filter();
        if (emulator != null) {
            emulator.setColourScheme(s.palette());
            emulator.getSound().setAllChannelsEnabled(s.sound());
            audio.setEnabled(s.sound());
        }
        if (thread != null) thread.setSpeedPercent(s.speedPercent());
    }

    // ------------------------------------------------------------ actions du menu

    public void reset() { if (emulator != null) emulator.reset(); }

    /** Emulator.saveState() attend la fin de l'image en cours (verrou du cœur) : l'instantané
     *  est toujours cohérent, sans mettre la boucle en pause. */
    public void saveState() {
        Emulator emu = emulator;
        if (emu == null) return;
        try {
            saveStore.writeState(emu.getCartridge(), SLOT, emu.saveState());
            Toast.makeText(app, R.string.state_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void loadState() {
        Emulator emu = emulator;
        if (emu == null) return;
        try {
            SaveState st = saveStore.readState(emu.getCartridge(), SLOT);
            if (st == null) { Toast.makeText(app, R.string.no_state, Toast.LENGTH_SHORT).show(); return; }
            emu.loadState(st);   // appliqué entre deux images
            Toast.makeText(app, R.string.state_loaded, Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

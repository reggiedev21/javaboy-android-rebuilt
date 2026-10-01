package javaboy.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import javaboy.core.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntUnaryOperator;

/** Écran de jeu : SurfaceView + manette tactile. L'émulateur vit dans un ViewModel pour
 *  survivre aux rotations ; la boucle tourne dans {@link EmulationThread}. */
public final class MainActivity extends AppCompatActivity {
    private static final int SLOT = 0;

    private EmulatorViewModel vm;
    private GameView gameView;
    private TouchPad touchPad;
    private TextView status;
    private SaveStore saveStore;
    private RomLibrary library;
    private Settings settings;
    private final int[] filtered = new int[GraphicsChip.WIDTH * GraphicsChip.HEIGHT];
    private volatile IntUnaryOperator filter = Filters.IDENTITY;

    private final ActivityResultLauncher<String[]> openRom =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                loadRom(uri);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vm = new ViewModelProvider(this).get(EmulatorViewModel.class);
        gameView = findViewById(R.id.game_view);
        touchPad = findViewById(R.id.touch_pad);
        status = findViewById(R.id.status);
        saveStore = new SaveStore(this);
        library = new RomLibrary(this);
        findViewById(R.id.menu_button).setOnClickListener(this::showMenu);
        gameView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                gameView.post(() -> touchPad.setScreenRect(gameView.screenRect())));
        touchPad.setListener((button, pressed) -> {
            Emulator e = vm.emulator;
            if (e != null) e.setButton(button, pressed);
        });
        applySettings(Settings.load(this));

        if (vm.hasGame()) {
            attachFrameListener(vm.emulator);
            status.setText(vm.romName);
        } else {
            Uri uri = getIntent().getData();
            if (uri != null) loadRom(uri);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        Settings s = Settings.load(this);
        if (!s.equals(settings)) applySettings(s);
        if (vm.thread != null) { vm.thread.setPaused(false); if (vm.audio != null) vm.audio.resume(); }
    }

    @Override protected void onPause() {
        super.onPause();
        if (vm.thread != null) { vm.thread.setPaused(true); if (vm.audio != null) vm.audio.pause(); }
        if (vm.emulator != null) saveStore.saveBatteryRam(vm.emulator.getCartridge());
    }

    private void applySettings(Settings s) {
        settings = s;
        filter = s.filter();
        touchPad.setOpacity(s.padOpacity());
        touchPad.setHaptics(s.haptics());
        gameView.setSmoothScaling(s.smoothScaling());
        if (vm.emulator != null) {
            vm.emulator.setColourScheme(s.palette());
            vm.emulator.getSound().setAllChannelsEnabled(s.sound());
            if (vm.audio != null) vm.audio.setEnabled(s.sound());
        }
        if (vm.thread != null) vm.thread.setSpeedPercent(s.speedPercent());
    }

    // ------------------------------------------------------------ chargement

    private void loadRom(Uri uri) {
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("flux nul");
            bytes = in.readAllBytes();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.invalid_rom, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        CartridgeResult result = Cartridge.load(bytes);
        if (result instanceof CartridgeResult.Error err) {
            Toast.makeText(this, getString(R.string.invalid_rom, err.message()), Toast.LENGTH_LONG).show();
            return;
        }
        CartridgeResult.Ok ok = (CartridgeResult.Ok) result;
        if (!ok.checksumValid()) Toast.makeText(this, R.string.bad_checksum, Toast.LENGTH_SHORT).show();
        startGame(ok.cartridge(), uri);
    }

    private void startGame(Cartridge cart, Uri uri) {
        stopGame();
        saveStore.loadBatteryRam(cart);
        AndroidAudioOutput audio = new AndroidAudioOutput(Emulator.DEFAULT_SAMPLE_RATE);
        Emulator emu = new Emulator(cart, new Joypad(), audio, Emulator.DEFAULT_SAMPLE_RATE);
        emu.setGbcAllowed(settings.gbcAllowed());
        emu.reset();
        vm.emulator = emu;
        vm.audio = audio;
        vm.romUri = uri;
        vm.romName = cart.getCartName() + " · " + cart.getCartTypeName() + (emu.isGbcMode() ? " · GBC" : "");
        applySettings(settings);
        attachFrameListener(emu);
        vm.thread = new EmulationThread(emu);
        vm.thread.setSpeedPercent(settings.speedPercent());
        vm.thread.start();
        status.setText(vm.romName);
        library.recordPlay(uri, cart.getCartName(), cart.getCartTypeName(), cart.isGbc());
    }

    private void attachFrameListener(Emulator emu) {
        emu.setFrameListener(pixels -> {
            IntUnaryOperator f = filter;
            if (f == Filters.IDENTITY) {
                gameView.draw(pixels);
            } else {
                Filters.apply(f, pixels, filtered);
                gameView.draw(filtered);
            }
        });
    }

    private void stopGame() {
        if (vm.thread != null) { vm.thread.shutdown(); vm.thread = null; }
        if (vm.emulator != null) { saveStore.saveBatteryRam(vm.emulator.getCartridge()); vm.emulator = null; }
        if (vm.audio != null) { vm.audio.release(); vm.audio = null; }
    }

    // ------------------------------------------------------------ menu

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.open_rom);
        menu.getMenu().add(0, 2, 1, R.string.library);
        menu.getMenu().add(0, 3, 2, R.string.reset);
        menu.getMenu().add(0, 4, 3, R.string.save_state);
        menu.getMenu().add(0, 5, 4, R.string.load_state);
        menu.getMenu().add(0, 6, 5, R.string.settings);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> openRom.launch(new String[]{"application/octet-stream", "*/*"});
                case 2 -> startActivity(new Intent(this, LibraryActivity.class));
                case 3 -> { if (vm.emulator != null) vm.emulator.reset(); }
                case 4 -> saveState();
                case 5 -> loadState();
                case 6 -> startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        menu.show();
    }

    /** Le save state est pris entre deux images : on met la boucle en pause le temps de la copie. */
    private void saveState() {
        if (vm.emulator == null) return;
        vm.thread.setPaused(true);
        try {
            saveStore.writeState(vm.emulator.getCartridge(), SLOT, vm.emulator.saveState());
            Toast.makeText(this, R.string.state_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            vm.thread.setPaused(false);
        }
    }

    private void loadState() {
        if (vm.emulator == null) return;
        vm.thread.setPaused(true);
        try {
            SaveState st = saveStore.readState(vm.emulator.getCartridge(), SLOT);
            if (st == null) { Toast.makeText(this, R.string.no_state, Toast.LENGTH_SHORT).show(); return; }
            vm.emulator.loadState(st);
            Toast.makeText(this, R.string.state_loaded, Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            vm.thread.setPaused(false);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) loadRom(uri);
    }
}

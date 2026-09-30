package javaboy.android;

import androidx.lifecycle.ViewModel;
import javaboy.core.Emulator;

/** Survit à la rotation de l'écran : l'émulateur et son thread ne sont pas recréés. */
public final class EmulatorViewModel extends ViewModel {
    Emulator emulator;
    EmulationThread thread;
    AndroidAudioOutput audio;
    android.net.Uri romUri;
    String romName;

    boolean hasGame() { return emulator != null; }

    @Override protected void onCleared() {
        if (thread != null) thread.shutdown();
        if (audio != null) audio.release();
    }
}

package javaboy.android;

import android.app.Application;

/** Point d'entrée du processus : branche l'émulateur sur les coques Game Boy de la bibliothèque Manette. */
public final class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ShellBinder(this));
    }
}

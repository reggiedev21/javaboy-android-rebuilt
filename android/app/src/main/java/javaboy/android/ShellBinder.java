package javaboy.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import androidx.drawerlayout.widget.DrawerLayout;
import com.example.manette.banque_de_jeu;
import com.example.manette.BaseGameBoy;
import com.google.android.material.navigation.NavigationView;
import javaboy.core.Joypad;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Branche l'émulateur sur les coques Game Boy de la bibliothèque Manette, sans modifier ni
 *  sous-classer le code du binôme : chaque Activity héritant de {@link BaseGameBoy} est
 *  équipée à son démarrage.
 *
 *  <ul>
 *   <li>écran : l'image du cœur est dessinée sur la vue {@code ecraninterne} ;</li>
 *   <li>boutons tactiles de la coque : appui/relâchement transmis au {@link Joypad} ;</li>
 *   <li>manette physique : touches traduites selon le mappage enregistré par {@code mappage_manette} ;</li>
 *   <li>menu latéral : « Sauvegarder l'état » et « Charger une sauvegarde » sont rendus
 *       fonctionnels, et trois entrées de l'émulateur sont ajoutées ;</li>
 *   <li>jeu choisi dans la banque : chargé depuis l'extra {@code ASSET_ROM_NAME}.</li>
 *  </ul>
 *
 *  Tout ce qui dépend du code du binôme (identifiants de vues, noms d'extra et de préférences)
 *  est regroupé dans cette classe. */
final class ShellBinder implements Application.ActivityLifecycleCallbacks {
    /** Extra posé par banque_de_jeu : nom d'un fichier d'assets, ou URI content:// d'une ROM ajoutée. */
    private static final String EXTRA_ROM = "ASSET_ROM_NAME";
    /** Préférences écrites par mappage_manette : code de touche Android pour chaque bouton. */
    private static final String PAD_PREFS = "Btn_prefs";

    private final EmulatorSession session;
    /** Banque de jeux de Manette disponible (variante demo) ; sinon remplacée par la bibliothèque. */
    private final boolean gameBank;
    private final Map<Activity, Shell> shells = new HashMap<>();

    ShellBinder(Context app) {
        session = EmulatorSession.get(app);
        gameBank = app.getResources().getBoolean(R.bool.has_game_bank);
    }

    @Override public void onActivityCreated(Activity a, Bundle state) {
        if (a instanceof banque_de_jeu && !gameBank) {
            a.startActivity(new Intent(a, LibraryActivity.class));
            a.finish();
        }
    }

    /** Premier démarrage d'une coque : son onCreate est terminé, ses vues existent. */
    @Override public void onActivityStarted(Activity a) {
        if (a instanceof BaseGameBoy && !shells.containsKey(a)) shells.put(a, new Shell(a));
    }

    @Override public void onActivityResumed(Activity a) {
        Shell s = shells.get(a);
        if (s != null) s.resume();
    }

    @Override public void onActivityPaused(Activity a) {
        Shell s = shells.get(a);
        if (s != null) s.pause();
    }

    @Override public void onActivityDestroyed(Activity a) { shells.remove(a); }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle out) { }

    /** Une coque équipée. */
    private final class Shell {
        private final Activity activity;
        private final ScreenDrawable screen;
        private final DrawerLayout drawer;
        private final Map<Integer, Joypad.Button> keys = new HashMap<>();

        Shell(Activity a) {
            activity = a;
            drawer = a.findViewById(com.example.manette.R.id.drawerLayout);

            View lcd = a.findViewById(com.example.manette.R.id.ecraninterne);
            screen = lcd == null ? null : new ScreenDrawable(lcd);
            if (lcd != null) lcd.setForeground(screen);

            // Absents de certaines coques (la Game Boy Advance n'a ni Select ni Start).
            touch(com.example.manette.R.id.Haut, Joypad.Button.UP);
            touch(com.example.manette.R.id.Bas, Joypad.Button.DOWN);
            touch(com.example.manette.R.id.Gauche, Joypad.Button.LEFT);
            touch(com.example.manette.R.id.Droite, Joypad.Button.RIGHT);
            touch(com.example.manette.R.id.boutonA, Joypad.Button.A);
            touch(com.example.manette.R.id.boutonB, Joypad.Button.B);
            touch(com.example.manette.R.id.Select, Joypad.Button.SELECT);
            touch(com.example.manette.R.id.Start, Joypad.Button.START);

            NavigationView nav = a.findViewById(com.example.manette.R.id.navigation);
            if (nav != null) {
                Menu menu = nav.getMenu();
                onClick(menu.findItem(com.example.manette.R.id.sauvegarde), session::saveState);
                onClick(menu.findItem(com.example.manette.R.id.cherche_sauv), session::loadState);
                // Icônes système, comme celles du menu de Manette (@android:drawable/ic_menu_*).
                onClick(menu.add(R.string.library).setIcon(android.R.drawable.ic_menu_agenda), () -> a.startActivity(new Intent(a, LibraryActivity.class)));
                onClick(menu.add(R.string.reset).setIcon(android.R.drawable.ic_menu_rotate), session::reset);
                onClick(menu.add(R.string.settings).setIcon(android.R.drawable.ic_menu_manage), () -> a.startActivity(new Intent(a, SettingsActivity.class)));
            }

            interceptKeys(a.getWindow());

            // Jeu choisi dans la banque. L'extra est retiré pour qu'une rotation de l'écran
            // (Activity recréée avec le même Intent) ne relance pas la partie depuis le début.
            String rom = a.getIntent().getStringExtra(EXTRA_ROM);
            if (rom != null) {
                a.getIntent().removeExtra(EXTRA_ROM);
                session.load(rom.startsWith("content://") ? Uri.parse(rom) : EmulatorSession.assetUri(rom));
            }
        }

        void resume() {
            loadKeys();
            if (screen != null) {
                screen.setSmoothScaling(session.settings().smoothScaling());
                session.attachScreen(screen);
            }
            session.resume();
        }

        void pause() {
            session.pause();
            if (screen != null) session.detachScreen(screen);
        }

        /** Bouton tactile de la coque : enfoncé tant que le doigt est dessus. */
        private void touch(int viewId, Joypad.Button button) {
            View v = activity.findViewById(viewId);
            if (v == null) return;
            v.setOnTouchListener((view, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        view.setPressed(true);
                        if (session.settings().haptics()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        session.setButton(button, true);
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.setPressed(false);
                        session.setButton(button, false);
                    }
                    default -> { }
                }
                return true;
            });
        }

        /** Le listener d'un élément de menu passe avant celui du NavigationView (posé par
         *  BaseGameBoy), qui reste donc en place pour les entrées de Manette. */
        private void onClick(MenuItem item, Runnable action) {
            if (item == null) return;
            item.setOnMenuItemClickListener(i -> {
                action.run();
                if (drawer != null) drawer.closeDrawers();
                return true;
            });
        }

        /** Mêmes clés et mêmes valeurs par défaut que mappage_manette. */
        private void loadKeys() {
            SharedPreferences p = activity.getSharedPreferences(PAD_PREFS, Context.MODE_PRIVATE);
            keys.clear();
            keys.put(p.getInt("keycode_a", KeyEvent.KEYCODE_BUTTON_A), Joypad.Button.A);
            keys.put(p.getInt("keycode_b", KeyEvent.KEYCODE_BUTTON_B), Joypad.Button.B);
            keys.put(p.getInt("keycode_up", KeyEvent.KEYCODE_DPAD_UP), Joypad.Button.UP);
            keys.put(p.getInt("keycode_down", KeyEvent.KEYCODE_DPAD_DOWN), Joypad.Button.DOWN);
            keys.put(p.getInt("keycode_left", KeyEvent.KEYCODE_DPAD_LEFT), Joypad.Button.LEFT);
            keys.put(p.getInt("keycode_right", KeyEvent.KEYCODE_DPAD_RIGHT), Joypad.Button.RIGHT);
            keys.put(p.getInt("keycode_start", KeyEvent.KEYCODE_BUTTON_START), Joypad.Button.START);
            keys.put(p.getInt("keycode_select", KeyEvent.KEYCODE_BUTTON_SELECT), Joypad.Button.SELECT);
        }

        /** Touche d'une manette physique. @return vrai si elle est mappée (événement consommé). */
        private boolean onKey(KeyEvent ev) {
            int source = ev.getSource();
            boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (!gamepad) return false;
            Joypad.Button button = keys.get(ev.getKeyCode());
            if (button == null) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                if (ev.getRepeatCount() == 0) session.setButton(button, true);
            } else if (ev.getAction() == KeyEvent.ACTION_UP) {
                session.setButton(button, false);
            }
            return true;
        }

        /** Les touches sont lues à l'entrée de la fenêtre, avant les vues (sinon la croix
         *  déplacerait le focus entre les boutons). Les autres appels de Window.Callback sont
         *  relayés tels quels au callback d'origine (celui d'AppCompat). */
        private void interceptKeys(Window window) {
            Window.Callback original = window.getCallback();
            window.setCallback((Window.Callback) Proxy.newProxyInstance(
                    Window.Callback.class.getClassLoader(), new Class<?>[]{Window.Callback.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("dispatchKeyEvent") && onKey((KeyEvent) args[0])) return true;
                        try {
                            return method.invoke(original, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }));
        }
    }
}

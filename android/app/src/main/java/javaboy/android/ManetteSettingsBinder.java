package javaboy.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manette.parametres;
import com.example.manette.parametres_couleur;
import com.example.manette.parametresadapter;

import java.util.List;

import javaboy.core.Emulator;

/** Branche l'écran « Paramètres » de Manette sur les réglages de l'émulateur ({@link Settings}),
 *  sans modifier le code du binôme (même principe que {@link ShellBinder}).
 *
 *  Le binôme a écrit ces branchements dans son dépôt, mais a dû les commenter : Manette est une
 *  bibliothèque dont dépend {@code :app}, elle ne peut pas importer {@code javaboy.android.Settings}.
 *  <ul>
 *   <li>onglet « Emulation » : affiche {@link EmulationSettingsFragment} (son
 *       {@code parametres_emulation}, décommenté) à la place de l'onglet vide ;</li>
 *   <li>onglet « Couleur Gameboy » : les quatre boutons de palette enregistrent la palette choisie
 *       (son {@code selectionnerPalette}, décommenté). Le mode sombre reste géré par Manette.</li>
 *  </ul> */
final class ManetteSettingsBinder implements Application.ActivityLifecycleCallbacks {
    /** Position de l'onglet « Emulation » dans la liste de parametres.onCreate. */
    private static final int TAB_EMULATION = 1;
    /** Libellés de la liste de parametres.onCreate, dans le même ordre. */
    private static final List<String> TABS = List.of("Couleur Gameboy", "Emulation", "Quitter");

    private static final int[] PALETTE_BUTTONS = {
            com.example.manette.R.id.btnNuance0, com.example.manette.R.id.btnNuance1,
            com.example.manette.R.id.btnNuance2, com.example.manette.R.id.btnNuance3,
    };

    /** Appelé pendant super.onCreate de l'Activity, donc avant que l'onglet initial soit créé. */
    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle state) {
        if (a instanceof parametres p) {
            p.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new PaletteButtons(), false);
        }
    }

    /** onCreate de parametres est terminé : sa liste d'onglets existe, on remplace son adapter. */
    @Override public void onActivityStarted(@NonNull Activity a) {
        if (!(a instanceof parametres p)) return;
        RecyclerView menu = p.findViewById(com.example.manette.R.id.Paramètres_menu);
        if (menu == null) return;
        menu.setAdapter(new parametresadapter(TABS, pos -> {
            if (pos == TAB_EMULATION) show(p, new EmulationSettingsFragment());
            else p.Choix_parametres(pos);
        }));
    }

    private static void show(FragmentActivity a, Fragment f) {
        a.getSupportFragmentManager().beginTransaction()
                .replace(com.example.manette.R.id.contenue, f).commit();
    }

    /** Pose les listeners des boutons de palette après ceux de parametres_couleur, qui ne font rien. */
    private static final class PaletteButtons extends FragmentManager.FragmentLifecycleCallbacks {
        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                          @NonNull View v, @Nullable Bundle state) {
            if (!(f instanceof parametres_couleur)) return;
            for (int i = 0; i < PALETTE_BUTTONS.length; i++) {
                View button = v.findViewById(PALETTE_BUTTONS[i]);
                int palette = i;
                if (button != null) button.setOnClickListener(b -> select(b.getContext(), palette));
            }
        }

        private static void select(Context ctx, int palette) {
            Settings.load(ctx).withPalette(palette).save(ctx);
            Toast.makeText(ctx, "Palette : " + Emulator.SCHEME_NAMES[palette], Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onActivityResumed(@NonNull Activity a) { }
    @Override public void onActivityPaused(@NonNull Activity a) { }
    @Override public void onActivityStopped(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle out) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}

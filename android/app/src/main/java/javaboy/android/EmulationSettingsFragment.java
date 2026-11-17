package javaboy.android;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Onglet « Emulation » de l'écran Paramètres de Manette.
 *
 *  Code écrit par le binôme dans {@code third_party/Manette/.../parametres_emulation.java}, où il
 *  est commenté : Manette est une bibliothèque dont dépend {@code :app}, il ne peut donc pas
 *  importer {@link Settings}. Il est repris ici tel quel, décommenté ; seules différences : le
 *  nom de la classe, et la sélection du filtre qui ne déclenche plus de modification au premier
 *  affichage. {@link ManetteSettingsBinder} l'affiche dans l'écran Paramètres. */
public final class EmulationSettingsFragment extends Fragment {

    private Settings s;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        s = Settings.load(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Sélecteur de filtre d'image
        root.addView(label(ctx, "Filtre d'image"));
        String[] filters = {"none", "gray", "green", "sepia", "invert"};
        String[] filterNames = {"Aucun", "Niveaux de gris", "LCD vert", "Sépia", "Inversé"};
        Spinner filter = new Spinner(ctx);
        filter.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, filterNames));
        filter.setSelection(Math.max(0, Arrays.asList(filters).indexOf(s.imageFilter())), false);
        filter.setOnItemSelectedListener(select(i -> s = s.withImageFilter(filters[i])));
        root.addView(filter);

        // Curseurs
        root.addView(slider(ctx, "Luminosité", 50, 150, s.brightness(), v -> s = s.withBrightness(v)));
        root.addView(slider(ctx, "Vitesse (%)", 25, 400, s.speedPercent(), v -> s = s.withSpeedPercent(v)));
        root.addView(slider(ctx, "Opacité de la manette", 30, 255, s.padOpacity(), v -> s = s.withPadOpacity(v)));

        // Interrupteurs
        root.addView(toggle(ctx, "Son", s.sound(), v -> s = s.withSound(v)));
        root.addView(toggle(ctx, "Fonctions Game Boy Color", s.gbcAllowed(), v -> s = s.withGbcAllowed(v)));
        root.addView(toggle(ctx, "Retour haptique", s.haptics(), v -> s = s.withHaptics(v)));
        root.addView(toggle(ctx, "Lissage de l'image", s.smoothScaling(), v -> s = s.withSmoothScaling(v)));

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(root);
        return scroll;
    }

    /** Relit avant d'écrire : l'onglet « Couleur » a pu changer la palette entre-temps. */
    @Override
    public void onPause() {
        super.onPause();
        if (s != null) {
            Context ctx = requireContext();
            Settings current = Settings.load(ctx);
            s = s.withPalette(current.palette());
            s.save(ctx);
        }
    }

    private TextView label(Context ctx, String t) {
        TextView tv = new TextView(ctx);
        tv.setText(t);
        tv.setPadding(0, 24, 0, 4);
        return tv;
    }

    private View slider(Context ctx, String name, int min, int max, int value, IntConsumer onChange) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView tv = label(ctx, name + " : " + value);
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                tv.setText(name + " : " + (p + min));
                onChange.accept(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        box.addView(tv);
        box.addView(sb);
        return box;
    }

    private View toggle(Context ctx, String name, boolean value, Consumer<Boolean> onChange) {
        Switch sw = new Switch(ctx);
        sw.setText(name);
        sw.setChecked(value);
        sw.setPadding(0, 16, 0, 16);
        sw.setOnCheckedChangeListener((v, on) -> onChange.accept(on));
        return sw;
    }

    private AdapterView.OnItemSelectedListener select(IntConsumer c) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                c.accept(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
    }
}

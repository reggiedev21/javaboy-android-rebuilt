package javaboy.android;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import javaboy.core.Emulator;

/** Réglages : chaque contrôle produit un nouveau record Settings (immuable) qui est sauvegardé. */
public final class SettingsActivity extends AppCompatActivity {
    private Settings s;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = Settings.load(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        root.addView(label("Palette (mode Game Boy)"));
        Spinner palette = new Spinner(this);
        palette.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Emulator.SCHEME_NAMES));
        palette.setSelection(s.palette());
        palette.setOnItemSelectedListener(select(i -> s = s.withPalette(i)));
        root.addView(palette);

        root.addView(label("Filtre d'image"));
        String[] filters = {"none", "gray", "green", "sepia", "invert"};
        String[] filterNames = {"Aucun", "Niveaux de gris", "LCD vert", "Sépia", "Inversé"};
        Spinner filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filterNames));
        filter.setSelection(java.util.Arrays.asList(filters).indexOf(s.imageFilter()));
        filter.setOnItemSelectedListener(select(i -> s = s.withImageFilter(filters[i])));
        root.addView(filter);

        root.addView(slider("Luminosité", 50, 150, s.brightness(), v -> s = s.withBrightness(v)));
        root.addView(slider("Vitesse (%)", 25, 400, s.speedPercent(), v -> s = s.withSpeedPercent(v)));
        root.addView(slider("Opacité de la manette", 30, 255, s.padOpacity(), v -> s = s.withPadOpacity(v)));
        root.addView(toggle("Son", s.sound(), v -> s = s.withSound(v)));
        root.addView(toggle("Fonctions Game Boy Color", s.gbcAllowed(), v -> s = s.withGbcAllowed(v)));
        root.addView(toggle("Retour haptique", s.haptics(), v -> s = s.withHaptics(v)));
        root.addView(toggle("Lissage de l'image", s.smoothScaling(), v -> s = s.withSmoothScaling(v)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onPause() { super.onPause(); s.save(this); }

    private TextView label(String t) { TextView tv = new TextView(this); tv.setText(t); tv.setPadding(0, 24, 0, 4); return tv; }

    private View slider(String name, int min, int max, int value, java.util.function.IntConsumer onChange) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView tv = label(name + " : " + value);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) { tv.setText(name + " : " + (p + min)); onChange.accept(p + min); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        box.addView(tv); box.addView(sb);
        return box;
    }

    private View toggle(String name, boolean value, java.util.function.Consumer<Boolean> onChange) {
        Switch sw = new Switch(this);
        sw.setText(name);
        sw.setChecked(value);
        sw.setPadding(0, 16, 0, 16);
        sw.setOnCheckedChangeListener((v, on) -> onChange.accept(on));
        return sw;
    }

    private AdapterView.OnItemSelectedListener select(java.util.function.IntConsumer c) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { c.accept(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
    }
}

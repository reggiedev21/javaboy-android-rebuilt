package javaboy.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Bibliothèque de ROMs : liste triée/filtrée, favoris (appui long), dernière partie jouée. */
public final class LibraryActivity extends AppCompatActivity {
    private RomLibrary library;
    private RomLibrary.Sort sort = RomLibrary.Sort.RECENT;
    private boolean favoritesOnly = false;
    private String query = "";
    private ListView list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        library = new RomLibrary(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        EditText search = new EditText(this);
        search.setHint("Rechercher");
        search.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        search.addTextChangedListener(new SimpleWatcher(s -> { query = s; refresh(); }));

        Spinner sortSpinner = new Spinner(this);
        sortSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.sort_recent), getString(R.string.sort_name), "Plus joués"}));
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                sort = RomLibrary.Sort.values()[new int[]{1, 0, 2}[pos]]; refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        ToggleButton fav = new ToggleButton(this);
        fav.setTextOn("★"); fav.setTextOff("☆"); fav.setText("☆");
        fav.setOnCheckedChangeListener((v, on) -> { favoritesOnly = on; refresh(); });

        Button open = new Button(this);
        open.setText(R.string.open_rom);
        open.setOnClickListener(v -> { setResult(RESULT_OK); startActivity(new Intent(this, MainActivity.class)); finish(); });

        bar.addView(search); bar.addView(sortSpinner); bar.addView(fav);
        root.addView(bar);
        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(open);
        setContentView(root);

        list.setOnItemClickListener((p, v, pos, id) -> play((RomLibrary.Entry) p.getItemAtPosition(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            library.toggleFavorite(((RomLibrary.Entry) p.getItemAtPosition(pos)).uri()); refresh(); return true;
        });
        refresh();
    }

    private void play(RomLibrary.Entry e) {
        Intent i = new Intent(this, MainActivity.class);
        i.setData(Uri.parse(e.uri()));
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void refresh() {
        List<RomLibrary.Entry> entries = library.list(sort, favoritesOnly, query);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                View v = super.getView(pos, convert, parent);
                RomLibrary.Entry e = entries.get(pos);
                ((TextView) v.findViewById(android.R.id.text1)).setText((e.favorite() ? "★ " : "") + e.name() + (e.gbc() ? "  (GBC)" : ""));
                ((TextView) v.findViewById(android.R.id.text2)).setText(e.cartType() + " · "
                        + DateUtils.getRelativeTimeSpanString(e.lastPlayed()) + " · " + e.playCount() + " parties");
                return v;
            }
        });
    }

    /** TextWatcher réduit à une lambda. */
    private static final class SimpleWatcher implements android.text.TextWatcher {
        private final java.util.function.Consumer<String> onChange;
        SimpleWatcher(java.util.function.Consumer<String> c) { onChange = c; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { onChange.accept(s.toString()); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }
}

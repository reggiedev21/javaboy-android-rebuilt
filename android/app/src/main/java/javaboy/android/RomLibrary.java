package javaboy.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/** Bibliothèque de ROMs : entrées immuables, tri et filtres par streams, favoris,
 *  dernière partie jouée. Persistée en JSON dans SharedPreferences (les URI SAF sont
 *  conservées avec une permission persistante). */
public final class RomLibrary {
    public record Entry(String uri, String name, String cartType, boolean gbc, boolean favorite, long lastPlayed, int playCount) {
        Entry withFavorite(boolean f) { return new Entry(uri, name, cartType, gbc, f, lastPlayed, playCount); }
        Entry played(long now) { return new Entry(uri, name, cartType, gbc, favorite, now, playCount + 1); }
    }

    public enum Sort { NAME, RECENT, MOST_PLAYED }

    private final SharedPreferences prefs;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public RomLibrary(Context ctx) {
        prefs = ctx.getSharedPreferences("library", Context.MODE_PRIVATE);
        load();
    }

    /** Enregistre (ou met à jour) une ROM ouverte et note la partie jouée. */
    public void recordPlay(Uri uri, String name, String cartType, boolean gbc) {
        Entry e = entries.get(uri.toString());
        long now = System.currentTimeMillis();
        entries.put(uri.toString(), e == null
                ? new Entry(uri.toString(), name, cartType, gbc, false, now, 1)
                : e.played(now));
        save();
    }

    public void toggleFavorite(String uri) {
        Entry e = entries.get(uri);
        if (e != null) { entries.put(uri, e.withFavorite(!e.favorite())); save(); }
    }

    public void remove(String uri) { entries.remove(uri); save(); }

    public Optional<Entry> lastPlayed() {
        return entries.values().stream().max(Comparator.comparingLong(Entry::lastPlayed));
    }

    /** Liste triée/filtrée : la logique de présentation est purement fonctionnelle. */
    public List<Entry> list(Sort sort, boolean favoritesOnly, String query) {
        Comparator<Entry> cmp = switch (sort) {
            case NAME -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            case RECENT -> Comparator.comparingLong(Entry::lastPlayed).reversed();
            case MOST_PLAYED -> Comparator.comparingInt(Entry::playCount).reversed();
        };
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return entries.values().stream()
                .filter(e -> !favoritesOnly || e.favorite())
                .filter(e -> q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q))
                .sorted(cmp)
                .collect(Collectors.toList());
    }

    private void load() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("entries", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.put(o.getString("uri"), new Entry(o.getString("uri"), o.getString("name"), o.optString("type", ""),
                        o.optBoolean("gbc"), o.optBoolean("fav"), o.optLong("last"), o.optInt("count")));
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        for (Entry e : entries.values()) {
            try {
                arr.put(new JSONObject().put("uri", e.uri()).put("name", e.name()).put("type", e.cartType())
                        .put("gbc", e.gbc()).put("fav", e.favorite()).put("last", e.lastPlayed()).put("count", e.playCount()));
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("entries", arr.toString()).apply();
    }
}

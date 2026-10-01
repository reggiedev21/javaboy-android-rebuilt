package javaboy.android;

import android.content.Context;
import android.content.SharedPreferences;
import javaboy.core.Filters;

import java.util.function.IntUnaryOperator;

/** Réglages de l'application : record immuable, lu/écrit dans SharedPreferences.
 *  {@link #filter()} construit le pipeline d'image composable correspondant. */
public record Settings(
        int palette,          // index dans Emulator.SCHEME_COLOURS (mode DMG)
        String imageFilter,   // "none", "gray", "green", "sepia", "invert"
        int brightness,       // pourcentage
        int speedPercent,     // 25..400
        boolean sound,
        boolean gbcAllowed,
        int padOpacity,       // 0..255
        boolean haptics,
        boolean smoothScaling) {

    public static final Settings DEFAULT = new Settings(0, "none", 100, 100, true, true, 140, true, false);

    private static final String PREFS = "settings";

    public static Settings load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Settings d = DEFAULT;
        return new Settings(
                p.getInt("palette", d.palette), p.getString("filter", d.imageFilter),
                p.getInt("brightness", d.brightness), p.getInt("speed", d.speedPercent),
                p.getBoolean("sound", d.sound), p.getBoolean("gbc", d.gbcAllowed),
                p.getInt("padOpacity", d.padOpacity), p.getBoolean("haptics", d.haptics),
                p.getBoolean("smooth", d.smoothScaling));
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("palette", palette).putString("filter", imageFilter)
                .putInt("brightness", brightness).putInt("speed", speedPercent)
                .putBoolean("sound", sound).putBoolean("gbc", gbcAllowed)
                .putInt("padOpacity", padOpacity).putBoolean("haptics", haptics)
                .putBoolean("smooth", smoothScaling).apply();
    }

    /** Pipeline d'image : filtre de teinte puis luminosité. */
    public IntUnaryOperator filter() {
        IntUnaryOperator tint = switch (imageFilter) {
            case "gray" -> Filters::toGray;
            case "green" -> Filters::toGreenLcd;
            case "sepia" -> Filters::toSepia;
            case "invert" -> Filters::invert;
            default -> Filters.IDENTITY;
        };
        IntUnaryOperator bright = brightness == 100 ? Filters.IDENTITY : Filters.brightness(brightness);
        if (tint == Filters.IDENTITY && bright == Filters.IDENTITY) return Filters.IDENTITY;
        return Filters.pipeline(tint, bright);
    }

    // "with-ers" pour l'écran de réglages
    public Settings withPalette(int v) { return new Settings(v, imageFilter, brightness, speedPercent, sound, gbcAllowed, padOpacity, haptics, smoothScaling); }
    public Settings withImageFilter(String v) { return new Settings(palette, v, brightness, speedPercent, sound, gbcAllowed, padOpacity, haptics, smoothScaling); }
    public Settings withBrightness(int v) { return new Settings(palette, imageFilter, v, speedPercent, sound, gbcAllowed, padOpacity, haptics, smoothScaling); }
    public Settings withSpeedPercent(int v) { return new Settings(palette, imageFilter, brightness, v, sound, gbcAllowed, padOpacity, haptics, smoothScaling); }
    public Settings withSound(boolean v) { return new Settings(palette, imageFilter, brightness, speedPercent, v, gbcAllowed, padOpacity, haptics, smoothScaling); }
    public Settings withGbcAllowed(boolean v) { return new Settings(palette, imageFilter, brightness, speedPercent, sound, v, padOpacity, haptics, smoothScaling); }
    public Settings withPadOpacity(int v) { return new Settings(palette, imageFilter, brightness, speedPercent, sound, gbcAllowed, v, haptics, smoothScaling); }
    public Settings withHaptics(boolean v) { return new Settings(palette, imageFilter, brightness, speedPercent, sound, gbcAllowed, padOpacity, v, smoothScaling); }
    public Settings withSmoothScaling(boolean v) { return new Settings(palette, imageFilter, brightness, speedPercent, sound, gbcAllowed, padOpacity, haptics, v); }
}

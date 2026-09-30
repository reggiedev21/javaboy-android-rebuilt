package javaboy.core;

import java.util.function.IntUnaryOperator;

/** Filtres d'image composables sur des pixels ARGB : chaque filtre est un
 *  {@code IntUnaryOperator} pur, et un pipeline est une composition {@code andThen}.
 *  Appliqués par l'hôte sur le tampon 160×144 avant affichage. */
public final class Filters {
    private Filters() {}

    public static final IntUnaryOperator IDENTITY = c -> c;

    /** Niveaux de gris (luminance Rec. 601). */
    public static int toGray(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int y = (r * 299 + g * 587 + b * 114) / 1000;
        return (c & 0xFF000000) | (y << 16) | (y << 8) | y;
    }

    /** Teinte « écran LCD vert » : applique la luminance sur un dégradé vert. */
    public static int toGreenLcd(int c) {
        int y = toGray(c) & 0xFF;
        int r = 0x0F + (0x9B - 0x0F) * y / 255;
        int g = 0x38 + (0xBC - 0x38) * y / 255;
        int b = 0x0F + (0x0F - 0x0F) * y / 255;
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Sépia. */
    public static int toSepia(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int nr = Math.min(255, (r * 393 + g * 769 + b * 189) / 1000);
        int ng = Math.min(255, (r * 349 + g * 686 + b * 168) / 1000);
        int nb = Math.min(255, (r * 272 + g * 534 + b * 131) / 1000);
        return (c & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
    }

    /** Inversion des couleurs. */
    public static int invert(int c) { return c ^ 0x00FFFFFF; }

    /** Luminosité : facteur en pourcentage (100 = inchangé). */
    public static IntUnaryOperator brightness(int percent) {
        return c -> {
            int r = clamp(((c >> 16) & 0xFF) * percent / 100);
            int g = clamp(((c >> 8) & 0xFF) * percent / 100);
            int b = clamp((c & 0xFF) * percent / 100);
            return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        };
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** Compose une liste de filtres en un seul (identité si vide). */
    public static IntUnaryOperator pipeline(IntUnaryOperator... filters) {
        IntUnaryOperator p = IDENTITY;
        for (IntUnaryOperator f : filters) p = p.andThen(f);
        return p;
    }

    /** Applique un filtre en place sur un tampon de pixels (boucle impérative volontaire :
     *  c'est le chemin chaud, une image toutes les 16 ms). */
    public static void apply(IntUnaryOperator filter, int[] src, int[] dst) {
        if (filter == IDENTITY) { System.arraycopy(src, 0, dst, 0, src.length); return; }
        for (int i = 0; i < src.length; i++) dst[i] = filter.applyAsInt(src[i]);
    }
}

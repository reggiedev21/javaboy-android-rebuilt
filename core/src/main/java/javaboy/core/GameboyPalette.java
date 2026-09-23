package javaboy.core;

/** Une palette de quatre entrées. Sur DMG, chaque entrée renvoie vers l'une des quatre
 *  couleurs configurables ; sur GBC, chaque entrée est une couleur RGB555 directe. */
public final class GameboyPalette {
    /** Pour chaque entrée, l'index de couleur (0-3) dans {@link #colours}. */
    private final int[] data = new int[4];
    /** Couleurs GBC brutes (15 bits) pour réponse aux lectures de FF69/FF6B. */
    private final int[] gbcData = new int[4];
    /** Couleurs ARGB. */
    private final int[] colours = {0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000};

    public GameboyPalette(int c1, int c2, int c3, int c4) {
        data[0] = c1; data[1] = c2; data[2] = c3; data[3] = c4;
    }

    public void setColours(int c1, int c2, int c3, int c4) {
        colours[0] = c1; colours[1] = c2; colours[2] = c3; colours[3] = c4;
    }

    public int getGbcColours(int entryNo, boolean high) {
        return high ? (gbcData[entryNo] >> 8) & 0xFF : gbcData[entryNo] & 0xFF;
    }

    /** Écrit un octet (haut ou bas) d'une couleur RGB555 et recalcule la couleur ARGB.
     *  Remplace l'ancien {@code new java.awt.Color(r, g, b).getRGB()}. */
    public void setGbcColours(int entryNo, boolean high, int dat) {
        if (high) {
            gbcData[entryNo] = (gbcData[entryNo] & 0x00FF) | ((dat & 0xFF) << 8);
        } else {
            gbcData[entryNo] = (gbcData[entryNo] & 0xFF00) | (dat & 0xFF);
        }
        data[0] = 0; data[1] = 1; data[2] = 2; data[3] = 3;
        colours[entryNo] = rgb555ToArgb(gbcData[entryNo]);
    }

    /** Conversion pure RGB555 -> ARGB 8888 (chaque composante 5 bits est décalée de 3). */
    public static int rgb555ToArgb(int rgb555) {
        int red   = (rgb555 & 0x001F) << 3;
        int green = (rgb555 & 0x03E0) >> 2;
        int blue  = (rgb555 & 0x7C00) >> 7;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** Décode le format DMG (deux bits par entrée). */
    public void decodePalette(int pal) {
        data[0] = pal & 0x03;
        data[1] = (pal & 0x0C) >> 2;
        data[2] = (pal & 0x30) >> 4;
        data[3] = (pal & 0xC0) >> 6;
    }

    /** Couleur ARGB pour l'entrée e. */
    public int getRgbEntry(int e) { return colours[data[e]]; }

    /** Index de couleur (0-3) pour l'entrée e. */
    public int getEntry(int e) { return data[e]; }
}

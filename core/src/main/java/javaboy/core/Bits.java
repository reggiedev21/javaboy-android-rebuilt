package javaboy.core;

/** Petites fonctions pures sur les octets, utilisées partout dans le cœur. */
public final class Bits {
    private Bits() {}

    /** Valeur non signée (0-255) d'un octet signé. */
    public static int unsign(byte b) { return b & 0xFF; }

    /** Valeur non signée (0-255) d'une valeur 8 bits signée stockée dans un short. */
    public static int unsign(short s) { return s & 0xFF; }

    /** Idem pour une valeur déjà stockée dans un int. */
    public static int unsign(int i) { return i & 0xFF; }

    private static final String HEX = "0123456789ABCDEF";

    public static String hexByte(int b) {
        return "" + HEX.charAt((b >> 4) & 0xF) + HEX.charAt(b & 0xF);
    }

    public static String hexWord(int w) {
        return hexByte((w >> 8) & 0xFF) + hexByte(w & 0xFF);
    }
}

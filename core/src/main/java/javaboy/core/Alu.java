package javaboy.core;

/** Calculs de l'ALU du SM83 sous forme de fonctions pures.
 *
 *  <p>Chaque fonction renvoie {@code (flags << 8) | résultat} dans un {@code int} : pas
 *  d'allocation, pas d'état, testable unitairement. Les flags sont ceux du registre F
 *  (Z = 0x80, N = 0x40, H = 0x20, C = 0x10). Les opérations qui laissent certains flags
 *  inchangés reçoivent l'ancien F en paramètre.
 */
public final class Alu {
    private Alu() {}

    public static final int Z = 0x80, N = 0x40, H = 0x20, C = 0x10;

    public static int result(int packed) { return packed & 0xFF; }
    public static int flags(int packed) { return (packed >> 8) & 0xF0; }

    private static int pack(int flags, int result) { return (flags << 8) | (result & 0xFF); }

    /** ADD a, b. */
    public static int add8(int a, int b) { return adc8(a, b, 0); }

    /** ADC a, b + carry. */
    public static int adc8(int a, int b, int carry) {
        int sum = a + b + carry;
        int r = sum & 0xFF;
        int flags = (r == 0 ? Z : 0)
                  | (((a & 0xF) + (b & 0xF) + carry) > 0xF ? H : 0)
                  | (sum > 0xFF ? C : 0);
        return pack(flags, r);
    }

    /** SUB a, b. */
    public static int sub8(int a, int b) { return sbc8(a, b, 0); }

    /** SBC a, b - carry. */
    public static int sbc8(int a, int b, int carry) {
        int diff = a - b - carry;
        int r = diff & 0xFF;
        int flags = N
                  | (r == 0 ? Z : 0)
                  | (((a & 0xF) - (b & 0xF) - carry) < 0 ? H : 0)
                  | (diff < 0 ? C : 0);
        return pack(flags, r);
    }

    /** CP a, b : flags de SUB, résultat = a inchangé. */
    public static int cp8(int a, int b) { return pack(flags(sub8(a, b)), a); }

    public static int and8(int a, int b) { int r = a & b; return pack((r == 0 ? Z : 0) | H, r); }
    public static int or8(int a, int b)  { int r = a | b; return pack(r == 0 ? Z : 0, r); }
    public static int xor8(int a, int b) { int r = a ^ b; return pack(r == 0 ? Z : 0, r); }

    /** INC r : C conservé. */
    public static int inc8(int v, int oldF) {
        int r = (v + 1) & 0xFF;
        return pack((oldF & C) | (r == 0 ? Z : 0) | ((v & 0xF) == 0xF ? H : 0), r);
    }

    /** DEC r : C conservé. */
    public static int dec8(int v, int oldF) {
        int r = (v - 1) & 0xFF;
        return pack((oldF & C) | N | (r == 0 ? Z : 0) | ((v & 0xF) == 0 ? H : 0), r);
    }

    /** DAA : ajustement décimal après ADD/SUB. */
    public static int daa(int a, int oldF) {
        int r = a;
        int flags = oldF & N;
        if ((oldF & N) == 0) {
            if ((oldF & C) != 0 || r > 0x99) { r += 0x60; flags |= C; }
            if ((oldF & H) != 0 || (r & 0xF) > 0x9) r += 0x06;
        } else {
            if ((oldF & C) != 0) { r -= 0x60; flags |= C; }
            if ((oldF & H) != 0) r -= 0x06;
        }
        r &= 0xFF;
        if (r == 0) flags |= Z;
        return pack(flags, r);
    }

    // --- rotations et décalages (préfixe CB et RLCA/RRCA/RLA/RRA) ---

    public static int rlc(int v) { int r = ((v << 1) | (v >> 7)) & 0xFF; return pack(zc(r, v & 0x80), r); }
    public static int rrc(int v) { int r = ((v >> 1) | (v << 7)) & 0xFF; return pack(zc(r, v & 0x01), r); }
    public static int rl(int v, int oldF)  { int r = ((v << 1) | ((oldF & C) != 0 ? 1 : 0)) & 0xFF; return pack(zc(r, v & 0x80), r); }
    public static int rr(int v, int oldF)  { int r = ((v >> 1) | ((oldF & C) != 0 ? 0x80 : 0)) & 0xFF; return pack(zc(r, v & 0x01), r); }
    public static int sla(int v) { int r = (v << 1) & 0xFF; return pack(zc(r, v & 0x80), r); }
    public static int sra(int v) { int r = (v >> 1) | (v & 0x80); return pack(zc(r, v & 0x01), r); }
    public static int srl(int v) { int r = v >> 1; return pack(zc(r, v & 0x01), r); }
    public static int swap(int v) { int r = ((v & 0x0F) << 4) | (v >> 4); return pack(r == 0 ? Z : 0, r); }

    /** RLCA/RRCA/RLA/RRA : comme les versions CB mais Z toujours à 0. */
    public static int clearZ(int packed) { return packed & ~(Z << 8); }

    private static int zc(int r, int carryBit) { return (r == 0 ? Z : 0) | (carryBit != 0 ? C : 0); }

    /** BIT n, v : Z si le bit est nul, H=1, N=0, C conservé. Résultat = v inchangé. */
    public static int bit(int n, int v, int oldF) {
        return pack((oldF & C) | H | (((v >> n) & 1) == 0 ? Z : 0), v);
    }

    // --- 16 bits ---

    /** ADD HL, rr : Z conservé, N=0, H bit 11, C bit 15. Renvoie (flags << 16) | résultat 16 bits. */
    public static int add16(int hl, int rr, int oldF) {
        int sum = hl + rr;
        int flags = (oldF & Z)
                  | (((hl & 0xFFF) + (rr & 0xFFF)) > 0xFFF ? H : 0)
                  | (sum > 0xFFFF ? C : 0);
        return (flags << 16) | (sum & 0xFFFF);
    }

    /** ADD SP, e / LD HL, SP+e : Z=N=0, H et C calculés sur l'octet bas (e non signé).
     *  Renvoie (flags << 16) | résultat 16 bits. */
    public static int addSpSigned(int sp, int e) {
        int r = (sp + (byte) e) & 0xFFFF;
        int u = e & 0xFF;
        int flags = (((sp & 0xF) + (u & 0xF)) > 0xF ? H : 0)
                  | (((sp & 0xFF) + u) > 0xFF ? C : 0);
        return (flags << 16) | r;
    }

    public static int result16(int packed) { return packed & 0xFFFF; }
    public static int flags16(int packed) { return (packed >> 16) & 0xF0; }
}

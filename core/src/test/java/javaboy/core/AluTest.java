package javaboy.core;

import org.junit.jupiter.api.Test;
import static javaboy.core.Alu.*;
import static org.junit.jupiter.api.Assertions.*;

class AluTest {
    @Test void addCarryHalfCarry() {
        int p = add8(0xF8, 0x0A);
        assertEquals(0x02, result(p));
        assertEquals(C | H, flags(p));
    }
    @Test void addZero() { assertEquals(Z | H | C, flags(add8(0xFF, 0x01))); }
    @Test void addNoFlags() { assertEquals(0, flags(add8(0x01, 0x02))); assertEquals(3, result(add8(1, 2))); }
    @Test void adcUsesCarry() { assertEquals(0x00, result(adc8(0xFF, 0x00, 1))); assertEquals(Z | H | C, flags(adc8(0xFF, 0, 1))); }
    @Test void subBorrow() { int p = sub8(0x10, 0x20); assertEquals(0xF0, result(p)); assertEquals(N | C, flags(p)); }
    @Test void subHalfBorrow() { assertEquals(N | H, flags(sub8(0x10, 0x01))); }
    @Test void subZero() { assertEquals(N | Z, flags(sub8(5, 5))); }
    @Test void cpKeepsA() { assertEquals(5, result(cp8(5, 9))); assertEquals(N | C | H, flags(cp8(5, 9))); }
    @Test void logic() {
        assertEquals(H, flags(and8(0xF0, 0x0F)) & ~Z); assertEquals(Z | H, flags(and8(0xF0, 0x0F)));
        assertEquals(0, flags(or8(1, 2))); assertEquals(Z, flags(xor8(7, 7)));
    }
    @Test void incDecKeepCarry() {
        assertEquals(C | H | Z, flags(inc8(0xFF, C)));
        assertEquals(0x10, result(inc8(0x0F, 0))); assertEquals(H, flags(inc8(0x0F, 0)));
        assertEquals(C | N | H, flags(dec8(0x10, C))); assertEquals(0x0F, result(dec8(0x10, C)));
        assertEquals(N | Z, flags(dec8(1, 0)));
    }
    @Test void daaAfterAdd() {
        // 0x19 + 0x28 = 0x41 avec H -> DAA -> 0x47
        int p = add8(0x19, 0x28); assertEquals(0x41, result(p)); assertEquals(H, flags(p));
        int d = daa(result(p), flags(p)); assertEquals(0x47, result(d)); assertEquals(0, flags(d));
        // 0x99 + 0x01 = 0x9A -> DAA 0x00, C et Z
        int q = add8(0x99, 0x01); int e = daa(result(q), flags(q));
        assertEquals(0x00, result(e)); assertEquals(Z | C, flags(e));
    }
    @Test void daaAfterSub() {
        int p = sub8(0x20, 0x01); // 0x1F, H
        int d = daa(result(p), flags(p)); assertEquals(0x19, result(d)); assertEquals(N, flags(d));
    }
    @Test void rotates() {
        assertEquals(0x01, result(rlc(0x80))); assertEquals(C, flags(rlc(0x80)));
        assertEquals(0x80, result(rrc(0x01))); assertEquals(C, flags(rrc(0x01)));
        assertEquals(0x00, result(rl(0x80, 0))); assertEquals(Z | C, flags(rl(0x80, 0))); assertEquals(Z, flags(rl(0, 0)));
        assertEquals(0x01, result(rl(0x00, C))); assertEquals(0, flags(rl(0, C)));
        assertEquals(0xC0, result(rr(0x81, C))); assertEquals(C, flags(rr(0x81, C)));
        assertEquals(0xFE, result(sla(0xFF))); assertEquals(0xC0, result(sra(0x81))); assertEquals(0x40, result(srl(0x81)));
        assertEquals(0x21, result(swap(0x12))); assertEquals(Z, flags(swap(0)));
        assertEquals(0, flags(clearZ(rlc(0)))); // RLCA n'affecte pas Z
    }
    @Test void bitTest() {
        assertEquals(Z | H | C, flags(bit(7, 0x7F, C)));
        assertEquals(H, flags(bit(0, 0x01, 0)));
        assertEquals(0x7F, result(bit(7, 0x7F, C)));
    }
    @Test void add16Flags() {
        int p = add16(0x0FFF, 0x0001, Z); assertEquals(0x1000, result16(p)); assertEquals(Z | H, flags16(p));
        int q = add16(0xFFFF, 0x0001, 0); assertEquals(0x0000, result16(q)); assertEquals(H | C, flags16(q));
    }
    @Test void addSpSignedFlags() {
        int p = addSpSigned(0xFFF8, 0x08); assertEquals(0x0000, result16(p)); assertEquals(H | C, flags16(p));
        int q = addSpSigned(0x0010, 0xFF); assertEquals(0x000F, result16(q)); assertEquals(C, flags16(q)); // -1 = 0xFF : retenue sur l'octet bas
        int r = addSpSigned(0x0001, 0xFF); assertEquals(0x0000, result16(r)); assertEquals(H | C, flags16(r));
    }
}

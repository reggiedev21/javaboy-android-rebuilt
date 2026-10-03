package org.junit.jupiter.api;
/** Sous-ensemble minimal de JUnit 5 pour exécuter les tests sans réseau. */
public final class Assertions {
    private Assertions() {}
    public static void assertEquals(Object e, Object a) { assertEquals(e, a, ""); }
    public static void assertEquals(Object e, Object a, String m) {
        if (e instanceof Number && a instanceof Number) {
            if (((Number) e).longValue() == ((Number) a).longValue()) return;
        } else if (java.util.Objects.equals(e, a)) return;
        throw new AssertionError(m + " expected: <" + e + "> but was: <" + a + ">");
    }
    public static void assertTrue(boolean c) { assertTrue(c, "expected true"); }
    public static void assertTrue(boolean c, String m) { if (!c) throw new AssertionError(m); }
    public static void assertFalse(boolean c) { assertFalse(c, "expected false"); }
    public static void assertFalse(boolean c, String m) { if (c) throw new AssertionError(m); }
    public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("null"); }
    public static void assertArrayEquals(int[] e, int[] a) { if (!java.util.Arrays.equals(e, a)) throw new AssertionError("arrays differ"); }
    public static void assertArrayEquals(byte[] e, byte[] a) { if (!java.util.Arrays.equals(e, a)) throw new AssertionError("arrays differ"); }
    public static void fail(String m) { throw new AssertionError(m); }
    public static <T extends Throwable> T assertThrows(Class<T> c, Runnable r) {
        try { r.run(); } catch (Throwable t) { if (c.isInstance(t)) return c.cast(t); throw new AssertionError("wrong exception " + t); }
        throw new AssertionError("expected " + c);
    }
    public static void assertInstanceOf(Class<?> c, Object o) { if (!c.isInstance(o)) throw new AssertionError("not " + c + ": " + o); }
}

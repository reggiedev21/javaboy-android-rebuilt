package org.junit.jupiter.api;
public final class Assumptions {
    private Assumptions() {}
    public static class SkippedException extends RuntimeException { public SkippedException(String m) { super(m); } }
    public static void assumeTrue(boolean c, String m) { if (!c) throw new SkippedException(m); }
}

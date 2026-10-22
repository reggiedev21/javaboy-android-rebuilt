import java.lang.reflect.*;
/** Exécute toutes les méthodes @Test des classes passées en argument. */
public class MiniRunner {
    /** Durée écoulée depuis t0, affichée seulement au-delà de 100 ms. */
    static String ms(long t0) {
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return ms >= 100 ? "  (" + ms + " ms)" : "";
    }

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        int ok = 0, ko = 0, skip = 0;
        for (String cn : args) {
            Class<?> c = Class.forName(cn);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) continue;
                Constructor<?> ctor = c.getDeclaredConstructor(); ctor.setAccessible(true); Object inst = ctor.newInstance();
                for (Method b : c.getDeclaredMethods())
                    if (b.isAnnotationPresent(org.junit.jupiter.api.BeforeEach.class)) { b.setAccessible(true); b.invoke(inst); }
                m.setAccessible(true);
                long t0 = System.nanoTime();
                try { m.invoke(inst); ok++; System.out.println("  OK   " + c.getSimpleName() + "." + m.getName() + ms(t0)); }
                catch (InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof org.junit.jupiter.api.Assumptions.SkippedException) { skip++; System.out.println("  SKIP " + c.getSimpleName() + "." + m.getName() + " (" + t.getMessage() + ")"); }
                    else { ko++; System.out.println("  FAIL " + c.getSimpleName() + "." + m.getName() + ": " + t); t.printStackTrace(System.out); }
                }
            }
        }
        System.out.println(ok + " ok, " + ko + " échecs, " + skip + " ignorés" + ms(start));
        if (ko > 0) System.exit(1);
    }
}

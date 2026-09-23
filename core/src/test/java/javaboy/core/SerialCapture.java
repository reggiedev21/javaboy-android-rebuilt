package javaboy.core;

/** Câble link factice qui enregistre tout ce que la ROM écrit sur le port série
 *  (les ROMs de test de Blargg y affichent leur résultat). */
public final class SerialCapture extends GameLink {
    private final StringBuilder text = new StringBuilder();
    @Override public void send(byte b) { text.append((char) (b & 0xFF)); }
    @Override public void shutDown() { }
    @Override public void setDmgcpu(Dmgcpu d) { }
    public String text() { return text.toString(); }
}

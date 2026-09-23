package javaboy.core;

/** Interface du port série (câble link). Aucune implémentation dans le cœur ;
 *  voir extras/ pour l'imprimante et le link TCP. */
public abstract class GameLink {
    protected boolean serverRunning = false;

    public abstract void send(byte b);
    public abstract void shutDown();
    public abstract void setDmgcpu(Dmgcpu d);

    public boolean isServerRunning() { return serverRunning; }
}

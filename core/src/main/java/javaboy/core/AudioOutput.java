package javaboy.core;

/** Sortie audio fournie par l'hôte (javax.sound sur PC, AudioTrack sur Android).
 *  Le cœur produit du PCM 8 bits signé, stéréo entrelacé, à la fréquence d'échantillonnage
 *  passée au {@link SoundChip}. */
public interface AudioOutput {
    /** Nombre d'octets que l'hôte peut accepter sans bloquer. */
    int availableBytes();

    /** Envoie {@code length} octets de {@code buffer} à la sortie. */
    void write(byte[] buffer, int length);

    /** Sortie muette. */
    AudioOutput NONE = new AudioOutput() {
        @Override public int availableBytes() { return 0; }
        @Override public void write(byte[] buffer, int length) { }
    };
}

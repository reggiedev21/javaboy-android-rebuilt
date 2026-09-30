package javaboy.android;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import javaboy.core.AudioOutput;

/** Sortie audio Android : AudioTrack en PCM 8 bits stéréo, derrière l'interface du cœur.
 *  Le cœur produit du 8 bits signé ; AudioTrack attend du 8 bits non signé : on décale. */
public final class AndroidAudioOutput implements AudioOutput {
    private final AudioTrack track;
    private final int bufferBytes;
    private byte[] scratch = new byte[0];
    private volatile boolean enabled = true;

    public AndroidAudioOutput(int sampleRate) {
        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_8BIT);
        bufferBytes = Math.max(min, sampleRate / 5 * 2); // ~200 ms
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track.play();
    }

    public void setEnabled(boolean on) { enabled = on; if (!on) track.pause(); else track.play(); }

    @Override public int availableBytes() {
        if (!enabled) return 0;
        // Nombre d'octets non encore joués : on ne remplit que ce qui manque pour ~200 ms.
        int written = track.getPlaybackHeadPosition() * 2; // 2 octets par frame stéréo 8 bits
        int queued = totalWritten - written;
        return Math.max(0, bufferBytes - queued);
    }

    private int totalWritten = 0;

    @Override public void write(byte[] buffer, int length) {
        if (!enabled) return;
        if (scratch.length < length) scratch = new byte[length];
        for (int i = 0; i < length; i++) scratch[i] = (byte) (buffer[i] + 128); // signé -> non signé
        int n = track.write(scratch, 0, length, AudioTrack.WRITE_NON_BLOCKING);
        if (n > 0) totalWritten += n;
    }

    public void pause() { track.pause(); }
    public void resume() { if (enabled) track.play(); }
    public void release() { track.stop(); track.release(); }
}

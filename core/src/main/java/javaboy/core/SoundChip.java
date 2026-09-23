package javaboy.core;

/** Contrôleur central du son : quatre canaux mixés dans un tampon PCM 8 bits stéréo,
 *  envoyé à l'{@link AudioOutput} fourni par l'hôte. */
public final class SoundChip {
    public final SquareWaveGenerator channel1;
    public final SquareWaveGenerator channel2;
    public final VoluntaryWaveGenerator channel3;
    public final NoiseGenerator channel4;

    public boolean channel1Enable = true, channel2Enable = true,
            channel3Enable = true, channel4Enable = true;

    private AudioOutput output;
    private final int sampleRate;
    private byte[] buffer = new byte[0];

    public SoundChip(AudioOutput output, int sampleRate) {
        this.output = output == null ? AudioOutput.NONE : output;
        this.sampleRate = sampleRate;
        channel1 = new SquareWaveGenerator(sampleRate);
        channel2 = new SquareWaveGenerator(sampleRate);
        channel3 = new VoluntaryWaveGenerator(sampleRate);
        channel4 = new NoiseGenerator(sampleRate);
    }

    public int getSampleRate() { return sampleRate; }

    public void setOutput(AudioOutput output) {
        this.output = output == null ? AudioOutput.NONE : output;
    }

    public void setAllChannelsEnabled(boolean on) {
        channel1Enable = channel2Enable = channel3Enable = channel4Enable = on;
    }

    /** Ajoute une image de son (1/60 s environ) au tampon de sortie. Appelé à chaque VBlank. */
    public void outputSound() {
        int available = output.availableBytes();
        if (available <= 0) return;
        int numSamples = Math.min(sampleRate / 28, available) & 0xFFFE;
        if (numSamples <= 0) return;
        if (buffer.length < numSamples) buffer = new byte[numSamples];
        java.util.Arrays.fill(buffer, 0, numSamples, (byte) 0);
        if (channel1Enable) channel1.play(buffer, numSamples / 2, 0);
        if (channel2Enable) channel2.play(buffer, numSamples / 2, 0);
        if (channel3Enable) channel3.play(buffer, numSamples / 2, 0);
        if (channel4Enable) channel4.play(buffer, numSamples / 2, 0);
        output.write(buffer, numSamples);
    }
}

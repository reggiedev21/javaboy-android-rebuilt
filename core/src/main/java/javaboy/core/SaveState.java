package javaboy.core;

import java.io.*;

/** Instantané complet et immuable de l'état de l'émulateur.
 *  Les tableaux sont copiés à la création et à la restauration : une valeur de ce record
 *  ne peut pas être altérée par la suite de l'émulation. */
public record SaveState(
        int version,
        String cartName,
        CpuRegisters cpu,
        byte[] mainRam,
        byte[] videoRam,
        byte[] oam,
        byte[] ioRegisters,
        byte[] cartRam,
        MapperState mapper,
        int[] paletteColours) {

    public static final int VERSION = 1;

    public record CpuRegisters(int a, int b, int c, int d, int e, int f, int hl, int sp, int pc,
                               boolean ime, int ieDelay, boolean halted, int instrCount,
                               boolean gbc, int gbcRamBank, int vramBank, boolean timaEnabled, int instrsPerTima,
                               boolean doubleSpeed, boolean hdmaRunning) {}

    public record MapperState(int currentBank, int ramBank, boolean ramEnabled, boolean mbc1LargeRamMode) {}

    public SaveState {
        mainRam = mainRam.clone(); videoRam = videoRam.clone(); oam = oam.clone();
        ioRegisters = ioRegisters.clone(); cartRam = cartRam.clone(); paletteColours = paletteColours.clone();
    }

    // Les accesseurs renvoient des copies : le record reste immuable de l'extérieur.
    @Override public byte[] mainRam() { return mainRam.clone(); }
    @Override public byte[] videoRam() { return videoRam.clone(); }
    @Override public byte[] oam() { return oam.clone(); }
    @Override public byte[] ioRegisters() { return ioRegisters.clone(); }
    @Override public byte[] cartRam() { return cartRam.clone(); }
    @Override public int[] paletteColours() { return paletteColours.clone(); }

    /** Sérialisation binaire simple (fichier `.state` sur Android ou PC). */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(new java.util.zip.GZIPOutputStream(bos));
            out.writeInt(version);
            out.writeUTF(cartName);
            CpuRegisters r = cpu;
            for (int v : new int[]{r.a, r.b, r.c, r.d, r.e, r.f, r.hl, r.sp, r.pc, r.ieDelay, r.instrCount, r.gbcRamBank, r.vramBank, r.instrsPerTima}) out.writeInt(v);
            for (boolean v : new boolean[]{r.ime, r.halted, r.gbc, r.timaEnabled, r.doubleSpeed, r.hdmaRunning}) out.writeBoolean(v);
            for (byte[] arr : new byte[][]{mainRam, videoRam, oam, ioRegisters, cartRam}) { out.writeInt(arr.length); out.write(arr); }
            out.writeInt(mapper.currentBank); out.writeInt(mapper.ramBank);
            out.writeBoolean(mapper.ramEnabled); out.writeBoolean(mapper.mbc1LargeRamMode);
            out.writeInt(paletteColours.length);
            for (int c : paletteColours) out.writeInt(c);
            out.close();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SaveState fromBytes(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(data)));
        int version = in.readInt();
        if (version != VERSION) throw new IOException("Version de save state inconnue : " + version);
        String name = in.readUTF();
        int[] i = new int[14];
        for (int k = 0; k < i.length; k++) i[k] = in.readInt();
        boolean[] b = new boolean[6];
        for (int k = 0; k < b.length; k++) b[k] = in.readBoolean();
        byte[][] arrs = new byte[5][];
        for (int k = 0; k < 5; k++) { arrs[k] = new byte[in.readInt()]; in.readFully(arrs[k]); }
        MapperState m = new MapperState(in.readInt(), in.readInt(), in.readBoolean(), in.readBoolean());
        int[] pal = new int[in.readInt()];
        for (int k = 0; k < pal.length; k++) pal[k] = in.readInt();
        CpuRegisters r = new CpuRegisters(i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], i[8], b[0], i[9], b[1], i[10],
                b[2], i[11], i[12], b[3], i[13], b[4], b[5]);
        return new SaveState(version, name, r, arrs[0], arrs[1], arrs[2], arrs[3], arrs[4], m, pal);
    }
}

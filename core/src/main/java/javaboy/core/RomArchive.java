package javaboy.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Déballe une ROM éventuellement compressée : .zip (première entrée .gb/.gbc), .gz, ou
 *  fichier brut. Fonction pure sur des octets : aucune entrée/sortie, l'hôte lit le fichier. */
public final class RomArchive {
    private RomArchive() {}

    private static final int MAX_ROM = 8 * 1024 * 1024;

    /** Renvoie les octets de la ROM contenue dans {@code bytes}, ou {@code bytes} inchangé
     *  s'il ne s'agit pas d'une archive. */
    public static byte[] unwrap(byte[] bytes) throws IOException {
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    if (!entry.isDirectory() && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                        return zip.readNBytes(MAX_ROM);
                    }
                }
            }
            throw new IOException("Aucun fichier .gb ou .gbc dans l'archive zip.");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return gz.readNBytes(MAX_ROM);
            }
        }
        return bytes;
    }
}

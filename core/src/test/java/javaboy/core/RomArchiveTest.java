package javaboy.core;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class RomArchiveTest {
    @Test void rawBytesPassThrough() throws Exception {
        byte[] rom = new TestRom().loopForever().build();
        assertTrue(RomArchive.unwrap(rom) == rom);
    }

    @Test void extractsGbFromZipIgnoringOtherEntries() throws Exception {
        byte[] rom = new TestRom().loopForever().build();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry("lisezmoi.txt")); zip.write("hello".getBytes()); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Jeu/CRISTAL.GBC")); zip.write(rom); zip.closeEntry();
        }
        assertArrayEquals(rom, RomArchive.unwrap(bos.toByteArray()));
    }

    @Test void gzip() throws Exception {
        byte[] rom = new TestRom().loopForever().build();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(rom); }
        assertArrayEquals(rom, RomArchive.unwrap(bos.toByteArray()));
    }

    @Test void zipWithoutRomIsAnError() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry("photo.png")); zip.write(new byte[10]); zip.closeEntry();
        }
        byte[] data = bos.toByteArray();
        assertThrows(RuntimeException.class, () -> { try { RomArchive.unwrap(data); } catch (java.io.IOException e) { throw new RuntimeException(e); } });
    }
}

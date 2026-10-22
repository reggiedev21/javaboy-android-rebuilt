package javaboy.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exécute cpu_instrs.gb (Blargg) sans affichage et lit le verdict sur le port série.
 *  La ROM doit être déposée dans core/src/test/resources/roms/ (jamais versionnée). */
class BlarggTest {
    static final Path ROM = RomFiles.find("cpu_instrs.gb");

    @Test
    void cpuInstrsPasses() throws IOException {
        assumeTrue(ROM != null, "ROM Blargg absente (cpu_instrs.gb) dans " + RomFiles.candidateDirs());
        Cartridge cart = TestRom.cartridge(Files.readAllBytes(ROM));
        Emulator emu = new Emulator(cart);
        SerialCapture serial = new SerialCapture();
        emu.getCpu().gameLink = serial;
        emu.reset();
        for (int frame = 0; frame < 60 * 60 && !serial.text().contains("Passed") && !serial.text().contains("Failed"); frame++) {
            emu.runFrame();
        }
        System.out.println(serial.text());
        assertTrue(serial.text().contains("Passed"), "cpu_instrs : " + serial.text());
    }
}

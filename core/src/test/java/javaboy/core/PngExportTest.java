package javaboy.core;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

/** Exporte une image rendue en PNG (build/captures/) pour vérification visuelle.
 *  AWT n'est utilisé que dans le test, jamais dans le cœur. */
class PngExportTest {
    static File export(int[] frame, String name) throws Exception {
        BufferedImage img = new BufferedImage(GraphicsChip.WIDTH, GraphicsChip.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, GraphicsChip.WIDTH, GraphicsChip.HEIGHT, frame, 0, GraphicsChip.WIDTH);
        File dir = new File("build/captures");
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        ImageIO.write(img, "png", out);
        return out;
    }

    @Test
    void exportsCheckerboardFrame() throws Exception {
        TestRom r = new TestRom().ldA(0xE4).ldhA(0x47).ldHL(0x8000);
        for (int row = 0; row < 8; row++) r.ldA(row % 2 == 0 ? 0xAA : 0x55).ldHLIA().ldA(0xAA).ldHLIA();
        r.ldA(0x91).ldhA(0x40).loopForever();
        Emulator e = new Emulator(TestRom.cartridge(r.build()));
        e.reset();
        for (int i = 0; i < 2; i++) e.runFrame();
        File f = export(e.getGraphics().getFrame(), "damier");
        assertTrue(f.length() > 0);
        BufferedImage back = ImageIO.read(f);
        assertEquals(160, back.getWidth());
        assertEquals(0xFF000000, back.getRGB(0, 0));
    }
}

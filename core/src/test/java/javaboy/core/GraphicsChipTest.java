package javaboy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsChipTest {
    static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, LIGHT = 0xFFAAAAAA, DARK = 0xFF555555;

    /** Programme : palette 0xE4, tuile 0 = damier vertical (colonnes paires couleur 3),
     *  tuile 1 = pleine couleur 1, LCDC on, puis boucle. */
    private TestRom baseProgram() {
        TestRom r = new TestRom()
            .ldA(0x00).ldhA(0x40)                 // LCD off pendant l'écriture VRAM
            .ldA(0xE4).ldhA(0x47)                 // BGP : 0->0, 1->1, 2->2, 3->3
            .ldHL(0x8000);
        for (int row = 0; row < 8; row++) r.ldA(0xAA).ldHLIA().ldA(0xAA).ldHLIA(); // lo & hi = 10101010
        for (int row = 0; row < 8; row++) r.ldA(0xFF).ldHLIA().ldA(0x00).ldHLIA(); // tuile 1 : couleur 1
        return r;
    }

    private Emulator boot(TestRom r, int frames) {
        Emulator e = new Emulator(TestRom.cartridge(r.build()));
        e.reset();
        for (int i = 0; i < frames; i++) e.runFrame();
        return e;
    }

    @Test
    void backgroundCheckerboardTile() {
        Emulator e = boot(baseProgram().ldA(0x91).ldhA(0x40).loopForever(), 3);
        int[] f = e.getGraphics().getFrame();
        assertEquals(BLACK, f[0]);      // colonne 0 : bit 7 = 1 -> couleur 3
        assertEquals(WHITE, f[1]);
        assertEquals(BLACK, f[2]);
        assertEquals(BLACK, f[143 * 160 + 158]);
        assertEquals(WHITE, f[143 * 160 + 159]);
    }

    @Test
    void scrollXShiftsBackground() {
        Emulator e = boot(baseProgram().ldA(1).ldhA(0x43).ldA(0x91).ldhA(0x40).loopForever(), 3);
        int[] f = e.getGraphics().getFrame();
        assertEquals(WHITE, f[0]);
        assertEquals(BLACK, f[1]);
    }

    @Test
    void lcdOffGivesBlankFrame() {
        Emulator e = boot(baseProgram().loopForever(), 2);
        for (int p : e.getGraphics().getFrame()) assertEquals(WHITE, p);
    }

    @Test
    void windowCoversBackground() {
        // tile map fenêtre (0x9C00) rempli de tuile 1 ; WY=0, WX=7+80 -> moitié droite couleur 1
        TestRom r = baseProgram().ldHL(0x9C00).ldB(0);
        // boucle : LD A,1 ; LD (HL+),A ; DEC B ; JR NZ,-6  (256 itérations -> 1re ligne de 32 tuiles ×8)
        r.ldA(1).ldHLIA().decB().jrNZ(-6);
        r.ldA(0).ldhA(0x4A).ldA(87).ldhA(0x4B).ldA(0xF1).ldhA(0x40).loopForever();
        Emulator e = boot(r, 3);
        int[] f = e.getGraphics().getFrame();
        assertEquals(BLACK, f[0]);
        assertEquals(LIGHT, f[80]);
        assertEquals(LIGHT, f[159]);
        assertEquals(LIGHT, f[7 * 160 + 100]);
    }

    @Test
    void spriteDrawnWithTransparency() {
        // OAM sprite 0 : y=16, x=8, tuile 0 (damier), attr 0 ; OBP0=0x1B (3->0,2->2,1->1,0->3)
        TestRom r = baseProgram().ldA(0x1B).ldhA(0x48)
            .ldHL(0xFE00).ldA(16).ldHLIA().ldA(8).ldHLIA().ldA(0).ldHLIA().ldA(0).ldHLIA()
            .ldA(0x93).ldhA(0x40).loopForever();
        Emulator e = boot(r, 3);
        int[] f = e.getGraphics().getFrame();
        assertEquals(WHITE, f[0]);   // pixel sprite couleur 3 -> OBP0 entrée 3 = 0 -> blanc, recouvre le noir du fond
        assertEquals(WHITE, f[1]);   // pixel sprite couleur 0 : transparent, fond blanc dessous
        assertEquals(BLACK, f[8]);   // hors du sprite : fond
    }

    @Test
    void gbcPaletteConversion() {
        assertEquals(0xFFF80000, GameboyPalette.rgb555ToArgb(0x001F));
        assertEquals(0xFF00F800, GameboyPalette.rgb555ToArgb(0x03E0));
        assertEquals(0xFF0000F8, GameboyPalette.rgb555ToArgb(0x7C00));
    }
}

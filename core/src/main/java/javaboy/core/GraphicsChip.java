package javaboy.core;

import java.util.function.Consumer;

/** Puce graphique : rend chaque ligne de balayage dans un tampon {@code int[160*144]} ARGB.
 *
 *  <p>Remplace l'ancien cache de tuiles AWT (64 images par tuile) par un rendu direct par
 *  ligne : fond, fenêtre puis sprites, avec les priorités DMG et GBC. Les effets raster
 *  (changement de SCX/SCY/WX/palette en cours d'image) sont donc respectés ligne à ligne.
 *
 *  <p>La puce ne connaît ni l'affichage ni le rythme : à chaque VBlank elle remet le tampon
 *  au {@link Consumer} enregistré par l'hôte. Le tampon est réutilisé d'une image à l'autre :
 *  l'hôte doit le copier ou l'afficher pendant l'appel.
 */
public final class GraphicsChip {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 144;

    /** 2 banques de 8 Ko (la seconde n'est utilisée qu'en mode GBC). */
    final byte[] videoRam = new byte[0x4000];
    /** Décalage de la banque VRAM courante (0 ou 0x2000). */
    int vidRamStart = 0;

    final GameboyPalette backgroundPalette = new GameboyPalette(0, 1, 2, 3);
    final GameboyPalette obj1Palette = new GameboyPalette(0, 1, 2, 3);
    final GameboyPalette obj2Palette = new GameboyPalette(0, 1, 2, 3);
    final GameboyPalette[] gbcBackground = new GameboyPalette[8];
    final GameboyPalette[] gbcSprite = new GameboyPalette[8];

    private final Dmgcpu cpu;
    private final int[] frame = new int[WIDTH * HEIGHT];
    private Consumer<int[]> frameListener = f -> { };

    // Tampons de travail par ligne (jamais réalloués dans la boucle chaude).
    private final int[] bgIndex = new int[WIDTH];      // index couleur 0-3 du fond/fenêtre
    private final boolean[] bgPriority = new boolean[WIDTH]; // bit 7 de l'attribut GBC
    private final boolean[] spriteDrawn = new boolean[WIDTH];
    private final int[] spriteOrder = new int[10];
    private int windowLine;

    public GraphicsChip(Dmgcpu cpu) {
        this.cpu = cpu;
        for (int r = 0; r < 8; r++) {
            gbcBackground[r] = new GameboyPalette(0, 1, 2, 3);
            gbcSprite[r] = new GameboyPalette(0, 1, 2, 3);
        }
    }

    /** Enregistre le destinataire des images (typiquement {@code pixels -> view.draw(pixels)}). */
    public void setFrameListener(Consumer<int[]> listener) {
        frameListener = listener == null ? f -> { } : listener;
    }

    public GameboyPalette getBackgroundPalette() { return backgroundPalette; }
    public GameboyPalette getObj1Palette() { return obj1Palette; }
    public GameboyPalette getObj2Palette() { return obj2Palette; }

    /** Applique un jeu de 12 couleurs ARGB (BG, OBJ0, OBJ1) en mode DMG. */
    public void setColourScheme(int[] scheme) {
        backgroundPalette.setColours(scheme[0], scheme[1], scheme[2], scheme[3]);
        obj1Palette.setColours(scheme[4], scheme[5], scheme[6], scheme[7]);
        obj2Palette.setColours(scheme[8], scheme[9], scheme[10], scheme[11]);
    }

    public void reset() {
        java.util.Arrays.fill(videoRam, (byte) 0);
        java.util.Arrays.fill(frame, backgroundPalette.getRgbEntry(0));
        vidRamStart = 0;
        windowLine = 0;
    }

    /** Lecture VRAM (adr relative à 0x8000). */
    public int addressRead(int addr) {
        return videoRam[addr + vidRamStart];
    }

    /** Écriture VRAM (adr relative à 0x8000). */
    public void addressWrite(int addr, byte data) {
        videoRam[addr + vidRamStart] = data;
    }

    /** Appelé par le CPU pour chaque ligne balayée (0-153). Les lignes 0-143 sont rendues. */
    public void notifyScanline(int line) {
        if (line == 0) windowLine = 0;
        if (line < 0 || line >= HEIGHT) return;
        byte[] io = cpu.ioHandler.registers;
        int lcdc = io[0x40] & 0xFF;
        int base = line * WIDTH;

        if ((lcdc & 0x80) == 0) { // LCD éteint : ligne blanche
            java.util.Arrays.fill(frame, base, base + WIDTH, backgroundPalette.getRgbEntry(0));
            return;
        }
        boolean gbc = cpu.gbcFeatures;
        renderBackground(line, lcdc, io, gbc);
        renderWindow(line, lcdc, io, gbc);
        if ((lcdc & 0x02) != 0) renderSprites(line, lcdc, gbc);
    }

    /** Appelé par le CPU en fin d'image : livre le tampon à l'hôte. */
    public void frameCompleted() {
        frameListener.accept(frame);
    }

    /** Tampon courant (utile pour les tests et les captures). */
    public int[] getFrame() { return frame; }

    // ------------------------------------------------------------------ fond

    private void renderBackground(int line, int lcdc, byte[] io, boolean gbc) {
        int base = line * WIDTH;
        if (!gbc && (lcdc & 0x01) == 0) { // BG désactivé sur DMG : couleur 0
            int c = backgroundPalette.getRgbEntry(0);
            for (int x = 0; x < WIDTH; x++) {
                frame[base + x] = c;
                bgIndex[x] = 0;
                bgPriority[x] = false;
            }
            return;
        }
        int scy = io[0x42] & 0xFF;
        int scx = io[0x43] & 0xFF;
        int mapBase = (lcdc & 0x08) != 0 ? 0x1C00 : 0x1800;
        boolean unsignedData = (lcdc & 0x10) != 0;
        int y = (line + scy) & 0xFF;
        int mapRow = mapBase + ((y >> 3) << 5);

        for (int x = 0; x < WIDTH; x++) {
            int xx = (x + scx) & 0xFF;
            int mapAddr = mapRow + (xx >> 3);
            int tile = videoRam[mapAddr] & 0xFF;
            int attr = gbc ? videoRam[mapAddr + 0x2000] & 0xFF : 0;
            int colour = tilePixel(tile, attr, unsignedData, y & 7, xx & 7);
            bgIndex[x] = colour;
            bgPriority[x] = (attr & 0x80) != 0;
            frame[base + x] = gbc ? gbcBackground[attr & 0x07].getRgbEntry(colour)
                                  : backgroundPalette.getRgbEntry(colour);
        }
    }

    // -------------------------------------------------------------- fenêtre

    private void renderWindow(int line, int lcdc, byte[] io, boolean gbc) {
        if ((lcdc & 0x20) == 0) return;
        if (!gbc && (lcdc & 0x01) == 0) return;
        int wy = io[0x4A] & 0xFF;
        int wx = (io[0x4B] & 0xFF) - 7;
        if (line < wy || wx >= WIDTH) return;

        int base = line * WIDTH;
        int mapBase = (lcdc & 0x40) != 0 ? 0x1C00 : 0x1800;
        boolean unsignedData = (lcdc & 0x10) != 0;
        int mapRow = mapBase + ((windowLine >> 3) << 5);
        int row = windowLine & 7;

        for (int x = Math.max(wx, 0); x < WIDTH; x++) {
            int wxx = x - wx;
            int mapAddr = mapRow + (wxx >> 3);
            int tile = videoRam[mapAddr] & 0xFF;
            int attr = gbc ? videoRam[mapAddr + 0x2000] & 0xFF : 0;
            int colour = tilePixel(tile, attr, unsignedData, row, wxx & 7);
            bgIndex[x] = colour;
            bgPriority[x] = (attr & 0x80) != 0;
            frame[base + x] = gbc ? gbcBackground[attr & 0x07].getRgbEntry(colour)
                                  : backgroundPalette.getRgbEntry(colour);
        }
        windowLine++;
    }

    /** Index couleur (0-3) d'un pixel de tuile de fond/fenêtre, flips et banque compris. */
    private int tilePixel(int tile, int attr, boolean unsignedData, int row, int col) {
        int dataAddr = unsignedData ? tile << 4 : 0x1000 + ((byte) tile << 4);
        if ((attr & 0x08) != 0) dataAddr += 0x2000;
        if ((attr & 0x40) != 0) row = 7 - row;
        int bit = (attr & 0x20) != 0 ? col : 7 - col;
        int lo = (videoRam[dataAddr + (row << 1)] >> bit) & 1;
        int hi = (videoRam[dataAddr + (row << 1) + 1] >> bit) & 1;
        return (hi << 1) | lo;
    }

    // -------------------------------------------------------------- sprites

    private void renderSprites(int line, int lcdc, boolean gbc) {
        byte[] oam = cpu.oam;
        int height = (lcdc & 0x04) != 0 ? 16 : 8;

        // 1. Les 10 premiers sprites de la ligne, dans l'ordre OAM (limite matérielle).
        int count = 0;
        for (int i = 0; i < 40 && count < 10; i++) {
            int sy = (oam[i * 4] & 0xFF) - 16;
            if (line >= sy && line < sy + height) spriteOrder[count++] = i;
        }
        if (count == 0) return;

        // 2. Priorité : sur DMG le plus petit X gagne (puis l'index OAM) ; sur GBC l'index OAM.
        if (!gbc) {
            for (int a = 1; a < count; a++) { // tri par insertion, 10 éléments max, stable
                int cur = spriteOrder[a];
                int curX = oam[cur * 4 + 1] & 0xFF;
                int b = a - 1;
                while (b >= 0 && (oam[spriteOrder[b] * 4 + 1] & 0xFF) > curX) {
                    spriteOrder[b + 1] = spriteOrder[b];
                    b--;
                }
                spriteOrder[b + 1] = cur;
            }
        }

        java.util.Arrays.fill(spriteDrawn, false);
        boolean bgMasterPriority = (lcdc & 0x01) != 0;
        int base = line * WIDTH;

        // 3. Dessin du plus prioritaire au moins prioritaire : le premier pixel opaque gagne.
        for (int n = 0; n < count; n++) {
            int i = spriteOrder[n] * 4;
            int sy = (oam[i] & 0xFF) - 16;
            int sx = (oam[i + 1] & 0xFF) - 8;
            int tile = oam[i + 2] & 0xFF;
            int attr = oam[i + 3] & 0xFF;
            if (height == 16) tile &= 0xFE;

            int row = line - sy;
            if ((attr & 0x40) != 0) row = height - 1 - row;
            int dataAddr = (tile << 4) + (row << 1);
            if (gbc && (attr & 0x08) != 0) dataAddr += 0x2000;
            int lo = videoRam[dataAddr];
            int hi = videoRam[dataAddr + 1];

            GameboyPalette pal = gbc ? gbcSprite[attr & 0x07]
                                     : ((attr & 0x10) != 0 ? obj2Palette : obj1Palette);
            boolean behindBg = (attr & 0x80) != 0;

            for (int px = 0; px < 8; px++) {
                int x = sx + px;
                if (x < 0 || x >= WIDTH || spriteDrawn[x]) continue;
                int bit = (attr & 0x20) != 0 ? px : 7 - px;
                int colour = (((hi >> bit) & 1) << 1) | ((lo >> bit) & 1);
                if (colour == 0) continue; // transparent : ne masque pas les sprites suivants

                boolean hidden;
                if (gbc) {
                    hidden = bgMasterPriority && bgIndex[x] != 0 && (bgPriority[x] || behindBg);
                } else {
                    hidden = behindBg && bgIndex[x] != 0;
                }
                spriteDrawn[x] = true;
                if (!hidden) frame[base + x] = pal.getRgbEntry(colour);
            }
        }
    }
}

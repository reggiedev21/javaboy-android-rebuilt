# Phase 2 — Découplage du cœur

## Architecture

```
core/     javaboy.core   Java pur : aucun import java.awt, java.applet, javax.sound
desktop/  javaboy.desktop Harnais Swing (remplace JavaBoy/GameBoyScreen/DefineControls/ModalDialog)
extras/                   GameBoyPrinter, TCPGameLink (mis de côté)
```

Trois interfaces entre le cœur et l'extérieur :

| Sens | Interface | Hôte PC | Hôte Android |
|---|---|---|---|
| Vidéo | `Consumer<int[]>` (160×144 ARGB) via `Emulator.setFrameListener` | `BufferedImage.setRGB` | `Bitmap.setPixels` |
| Audio | `AudioOutput { availableBytes(); write(buf, len) }` | `SourceDataLine` | `AudioTrack` |
| Manette | `Joypad` (`press`/`release`, lu par `IoHandler`) | clavier | boutons tactiles |

`Emulator` est la façade : `reset()`, `runFrame()` (une image, puis retour à l'hôte
qui gère le rythme), `setButton()`, `stop()`.

## Changements par classe

- **Cartridge** : `Cartridge.load(byte[])` renvoie un `sealed interface CartridgeResult`
  (`Ok(cartridge, checksumValid)` / `Error(message)`). Plus de `ModalDialog`, plus de
  fichiers : la RAM de sauvegarde est exposée par `getBatteryRam()`/`loadBatteryRam()`.
  Les mappers sont repris à l'identique.
- **GameboyPalette** : `rgb555ToArgb` en opérations sur les bits, plus de `java.awt.Color`.
- **SoundChip** : écrit dans un `AudioOutput` ; plus de `javax.sound`.
- **GraphicsChip** (réécrit) : rendu par ligne dans un `int[]`. Fond, fenêtre (compteur de
  ligne interne), sprites (10 par ligne, priorité par X sur DMG, par index OAM sur GBC,
  bit maître LCDC.0 sur GBC). Les effets raster sont respectés ligne à ligne, ce que
  l'ancien cache de tuiles ne permettait pas. Lit les registres LCDC/SCX/SCY/WX/WY
  directement : `IoHandler` ne pousse plus d'états vers la puce graphique.
- **Dmgcpu** : plus de `Component`, plus de `Thread.sleep` ni d'attente de `repaint`.
  À la fin de l'image : `soundChip.outputSound()`, `graphicsChip.frameCompleted()`,
  `frameReady = true` ; `runFrame()` exécute jusqu'à ce point.
- **IoHandler** : lit le `Joypad` ; `toggleKey` supprimé.

## Tests (core/src/test)

- `TestRom` : mini-assembleur qui fabrique une ROM 32 Ko avec en-tête et checksum valides.
- `CartridgeTest`, `CpuTest`, `GraphicsChipTest` (fond, défilement, fenêtre, sprites,
  LCD éteint), `PngExportTest` (capture dans `build/captures/`).
- `BlarggTest` : lit `cpu_instrs.gb` dans `core/src/test/resources/roms/` et vérifie
  « Passed » sur le port série ; ignoré si la ROM est absente.

Exécution : `./gradlew :core:test`, ou hors-ligne `./test.sh`.

## État

- `core` : 0 import AWT/applet/son (vérifié par `grep`).
- 18 tests passent, 1 ignoré (ROM Blargg à fournir).

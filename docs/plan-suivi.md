# Suivi du plan d'action — 15/09/2026

Légende : ✅ fait et committé · ◐ partiel (nécessite des ROMs ou un téléphone) · ☐ à faire.

| Phase | Item | État | Commit / note |
|---|---|---|---|
| 0 | Récupérer JavaBoy 0.92, compiler/lancer avec Java 21 | ✅ | compile sans erreur (voir `00-etat-initial.md`) |
| 0 | Présenter le projet à l'enseignant | ✅ | plan validé |
| 0 | Tester des ROMs légales, captures « état initial » | ◐ | ROMs à déposer dans `core/src/test/resources/roms/` ; `BlarggTest` prêt |
| 0 | Commit « Import JavaBoy 0.92 original » | ✅ | `611f890` |
| 1 | `package` + imports | ✅ | `60a72ce` |
| 1 | Retirer applet, PHP, sauvegarde web | ✅ | `efbb6a9` |
| 1 | Mettre de côté printer / TCP link | ✅ | `2e17f27` (`extras/`) |
| 1 | Structure Gradle | ✅ | `fb8d491`, multi-modules en phase 2 |
| 2 | Trois interfaces vidéo / audio / manette | ✅ | `10f1378` |
| 2 | `GameboyPalette` sans `java.awt.Color` | ✅ | `rgb555ToArgb` |
| 2 | `Cartridge` depuis `byte[]`, résultat `sealed` | ✅ | `CartridgeResult.Ok/Error` |
| 2 | `SoundChip` derrière l'interface | ✅ | `AudioOutput` |
| 2 | Rendu réécrit en `int[]` | ✅ | scanline, priorités DMG/GBC |
| 2 | Tests JUnit, Blargg sans affichage, export PNG | ✅ | 50 tests, 1 ignoré sans ROM |
| 3 | Mesurer avant | ✅ | 335 images/s |
| 3 | ALU en fonctions pures + tests | ✅ | `Alu`, 15 tests |
| 3 | `switch` → `Opcode[256]` de lambdas | ✅ | `e3c22d0` |
| 3 | Save states en `record` | ✅ | `SaveState` |
| 3 | Mesurer après, comparer | ✅ | 4 650 images/s, analyse JIT (`03-benchmark.md`) |
| 4 | Modules `:core` / `:app` | ✅ | `android/settings.gradle.kts` |
| 4 | `SurfaceView` + `Bitmap`, thread dédié | ✅ | non compilé (pas de SDK ici) |
| 4 | `ACTION_OPEN_DOCUMENT` | ✅ | idem |
| 4 | Cycle de vie, rotation | ✅ | `EmulatorViewModel` |
| 4 | Boutons tactiles | ✅ | `TouchPad` |
| 4 | Son `AudioTrack` | ✅ | `AndroidAudioOutput` |
| 4 | **Critère de fin : jouable sur un vrai téléphone** | ☐ | première tâche à faire dans Android Studio |
| 5.1 | Manette soignée (multitouch, haptique, opacité, zones) | ✅ | |
| 5.2 | Bibliothèque (streams, favoris, dernière partie) | ✅ | `RomLibrary` |
| 5.3 | Sauvegardes `.sav` + save states | ✅ | `SaveStore` |
| 5.4 | Réglages en `record`, filtres composables | ✅ | `Settings`, `Filters` |
| 5.5 | Bonus (éditeur de disposition, Bluetooth, accessibilité) | ☐ | |
| 6 | Tests téléphone, test utilisateur, corrections | ☐ | protocole dans `06-protocole-tests.md` |
| 7 | Rapport et soutenance | ◐ | plan dans `07-plan-rapport.md` |

## Écarts assumés par rapport au plan

- L'ancienne IHM AWT (`JavaBoy`, `GameBoyScreen`, `DefineControls`, `ModalDialog`) a été
  remplacée dès la phase 2 par un harnais Swing de 200 lignes consommant le cœur découplé :
  elle était trop imbriquée dans `GraphicsChip.draw(Graphics)` pour être conservée telle
  quelle, et le harnais reproduit exactement l'intégration Android (mêmes trois interfaces).
- Le modèle de timing par comptage d'instructions de l'original est conservé (comparabilité
  du benchmark, comportement inchangé) ; le passage aux cycles est listé comme suite.
- Plusieurs flags CPU faux dans l'original ont été corrigés au passage à l'ALU pure ; ils sont
  documentés dans `03-benchmark.md` et couverts par des tests.

# Phase 0 — État initial de JavaBoy 0.92

## Origine

- Auteur : Neil Millstone, 2001–2005, GPL v2 (`GPL.txt`, `LICENSE`).
- Source : miroir https://github.com/chijure/JavaBoy (commit `e63da24`).
- Import intact dans le commit « Import JavaBoy 0.92 original ».

## Compilation avec Java 21

```
javac -d build src/com/neilmillstone/*.java
java -cp build JavaBoy
```

Le code compile **sans erreur** avec Java 21. Avertissements :

- API dépréciées et « marquées pour suppression » : `java.applet.Applet`,
  `Thread.stop`, `new Character(...)`, `URLEncoder.encode(String)`.
- Opérations non vérifiées (`DefineControls` utilise `List`/`Vector` bruts).

`java.applet.Applet` est encore présent en Java 21 mais déprécié « for
removal ». Le mode applet est de toute façon inutilisable (aucun navigateur ne
le charge) : c'est la première chose à retirer.

## Constat sur le code

| Élément | Observation |
|---|---|
| Paquetage | Les fichiers sont dans `src/com/neilmillstone/` mais **aucun** ne déclare `package` : tout est dans le paquet par défaut. |
| Imports | Chaque classe recopie le même bloc de 18 imports (AWT, applet, réseau, son) dont la plupart sont inutiles. |
| Couplage | `Dmgcpu`, `Cartridge`, `GraphicsChip` reçoivent un `java.awt.Component` pour créer des images et des boîtes de dialogue. |
| Timing | Le CPU ne compte pas les cycles : 60 instructions par ligne, 154 lignes par image (`INSTRS_PER_HBLANK`). Le timer TIMA et DIV sont dérivés du compteur d'instructions. |
| Rendu | Cache de tuiles AWT (`MemoryImageSource`), 64 variantes par tuile (palette × flip). Le fond est dessiné par bandes de 8 lignes, la fenêtre et les sprites en fin d'image : pas d'effets raster fins. |
| Son | `javax.sound.sampled.SourceDataLine`, 8 bits stéréo, quatre générateurs. |
| Mappers | ROM only, MBC1, MBC2, MBC3 (RTC partiel), MBC5. |
| Réseau | `TCPGameLink` (câble link par TCP), `GameBoyPrinter`, sauvegarde web via `loadram.php`/`saveram.php`. |

## Tests fonctionnels de départ

Le dépôt ne contient aucune ROM (règle : aucune ROM commerciale). Les ROMs
légales à utiliser pour comparer avant/après :

- `cpu_instrs.gb` (Blargg) — attendu : « Passed » sur le port série.
- Tobu Tobu Girl (homebrew, open source).

Ces ROMs se placent dans `core/src/test/resources/roms/` (ignoré par Git) ;
les tests correspondants sont ignorés automatiquement si les fichiers sont
absents. Les captures avant/après sont à déposer dans `docs/captures/`.

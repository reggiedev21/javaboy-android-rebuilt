# Phase 3 — Benchmark avant/après

Mesure : `java -cp build/test-classes javaboy.core.bench.Benchmark 3000`
(ROM synthétique mêlant ALU, accès mémoire, rotations CB, CALL/RET et DMA OAM ; LCD
allumé donc rendu par ligne inclus ; sans affichage ni son ; 600 images d'échauffement
JIT puis 3000 images chronométrées). Machine : conteneur Linux, OpenJDK 21.0.10.

| Étape | frames/s | × temps réel | M instr/s |
|---|---|---|---|
| A. Avant : `switch` de 2000 lignes, `cartridge.update()` à chaque instruction | 335 | 5,6 | 3,7 |
| B. A sans `update()` par instruction (2 × `currentTimeMillis()` en moins) | 400 | 6,7 | 4,4 |
| C. B avec `-XX:-DontCompileHugeMethods` (option JVM) | 3 230 | 54 | 35,6 |
| **D. Après : table `Opcode[256]` de lambdas + `Alu` pur, sans option JVM** | **4 650** | **78** | **51,5** |

## Analyse

- **Le vrai coupable n'était pas le `switch` en soi mais sa taille.** HotSpot refuse de
  compiler en JIT toute méthode de plus de 8000 bytecodes (`DontCompileHugeMethods`),
  et `execute()` dépassait largement ce seuil : la boucle chaude tournait en mode
  **interprété**. Le point C le prouve : la seule option JVM multiplie par 8.
- **La table de lambdas** découpe le décodage en ~300 petites méthodes que le JIT
  compile et inline normalement. Elle fait mieux que C (×1,4) et n'exige aucune option.
  Aucune lambda n'est créée dans la boucle : `buildTables()` est appelé une fois dans
  le constructeur ; la boucle fait `table[op].execute(this)`.
- **L'ALU en fonctions pures** (`Alu.add8` etc., résultat et flags dans un `int`,
  zéro allocation) n'a pas de coût mesurable et a permis 15 tests unitaires
  indépendants du CPU. Plusieurs flags étaient faux dans l'original
  (RLCA modifiait Z, `ADD HL,rr` écrasait Z, `CP nn` ignorait H, DAA partiel) ;
  ces corrections sont couvertes par `AluTest` et `CpuTableTest`.
- `cartridge.update()` (horloge MBC3) est désormais appelé une fois par image.

Règle appliquée : fonctionnel pour les calculs (ALU pure, table immuable),
impératif dans la boucle chaude (registres en champs `int`, pas d'objets par instruction).

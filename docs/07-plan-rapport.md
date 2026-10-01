# Phase 7 — Plan du rapport et de la soutenance

1. **Origine et licence** — JavaBoy 0.92, GPL v2, miroir GitHub ; premier commit = import
   intact ; `git log --stat` pour quantifier l'apport personnel (≈ 5 000 lignes originales
   retirées ou réécrites, cœur de 24 classes, 50 tests).
2. **Architecture avant / après** — schéma : avant, `Dmgcpu`/`GraphicsChip`/`Cartridge`
   dépendent d'un `Component` AWT et d'un `Applet` ; après, `core` exporte trois interfaces
   (vidéo `Consumer<int[]>`, `AudioOutput`, `Joypad`) consommées par `desktop` et `android`.
   Voir `docs/02-decouplage.md`.
3. **Choix fonctionnels raisonnés** — `Alu` pur (résultat + flags dans un `int`, zéro
   allocation, 15 tests), table `Opcode[256]` de lambdas créée une fois, `SaveState` record
   immuable, `RomLibrary` en streams, filtres `IntUnaryOperator` composables.
   **Argument central** : `docs/03-benchmark.md` — 335 → 4 650 images/s, et la démonstration
   que l'ancien `switch` dépassait le seuil JIT de HotSpot (méthode jamais compilée).
   Règle appliquée : fonctionnel pour les calculs et la périphérie, impératif dans la boucle
   chaude.
4. **IHM** — manette multitouch/haptique/transparence, bibliothèque, sauvegardes, réglages ;
   résultats du test utilisateur ; captures avant/après.
5. **Tests** — ROM synthétique (`TestRom`), tests par composant, Blargg, export PNG.
6. **Démonstration** — sur téléphone : ouvrir une ROM depuis la bibliothèque, jouer, rotation,
   save state, palette.
7. **Limites et suites** — timing par comptage d'instructions hérité de l'original (pas de
   cycles exacts), pas de MBC3 RTC persistant, son non resynchronisé finement ; pistes :
   timing par cycles, imprimante/link (extras), manettes Bluetooth, éditeur de disposition.

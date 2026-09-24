# Phase 3 — Benchmark avant/après

Mesure : `java -cp build/test-classes javaboy.core.bench.Benchmark 3000`
(ROM synthétique, LCD allumé, sans affichage ni son ; 600 images d'échauffement JIT
puis 3000 images chronométrées). Machine : conteneur Linux, OpenJDK 21.0.10.

| Étape | frames/s | × temps réel | M instr/s |
|---|---|---|---|
| Avant (switch de 2000 lignes, `cartridge.update()` à chaque instruction) | 333 – 339 | 5,6 | 3,7 |

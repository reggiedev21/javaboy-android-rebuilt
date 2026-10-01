# Phase 6 — Protocole de test sur appareil et test utilisateur

## Tests techniques (téléphone réel, idéalement d'entrée de gamme)

| # | Test | Attendu | Résultat |
|---|---|---|---|
| 1 | Ouvrir `cpu_instrs.gb` (Blargg) | « Passed » à l'écran | |
| 2 | Ouvrir Tobu Tobu Girl, jouer 5 min | 60 images/s stables (indicateur dans le titre), pas de craquement audio | |
| 3 | Rotation portrait ↔ paysage pendant le jeu | la partie continue, manette relayoutée | |
| 4 | Mettre l'app en arrière-plan puis revenir | pause puis reprise exacte, son coupé pendant la pause | |
| 5 | Jeu avec pile (type MBC1+RAM+BATTERY), sauvegarder en jeu, tuer l'app, relancer | la sauvegarde est présente (`.sav` interne) | |
| 6 | Sauvegarder l'état, jouer, charger l'état | retour exact à l'instant sauvegardé | |
| 7 | Multitouch : courir + sauter simultanément | les deux boutons sont lus | |
| 8 | Diagonales sur la croix | 8 directions sans zone morte gênante | |
| 9 | Jeu GBC (homebrew couleur) | couleurs correctes, double vitesse acceptée | |
| 10 | Batterie : 20 min de jeu | pas de chauffe anormale (mesurer la charge consommée) | |

Mesures à consigner : modèle, version Android, images/s, consommation batterie,
captures d'écran avant (PC, JavaBoy original) / après (téléphone) dans `docs/captures/`.

## Test utilisateur (3 à 5 personnes, 10 minutes chacune)

Tâches : 1) ouvrir une ROM depuis la bibliothèque ; 2) jouer un niveau ;
3) changer la palette dans les réglages ; 4) sauvegarder puis recharger l'état.

Observer sans aider ; noter pour chaque tâche : réussite, temps, hésitations, remarques
spontanées. Questions finales : lisibilité des boutons, confort de la croix, réglage
d'opacité utile ? Consigner dans `docs/test-utilisateur.md` et en déduire les
corrections (phase 6) à tracer dans Git.

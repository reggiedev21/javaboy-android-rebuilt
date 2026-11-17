# Application Android

Ouvrir **ce dossier** (`android/`) dans Android Studio. Le module `:core` est le dossier
`../core` du dépôt : le cœur n'est pas copié, il est partagé avec le harnais PC.

Le cœur est compilé en bytecode Java 17 (records, `sealed`) ; l'application aussi.
Le code Android n'a pas encore été compilé : c'est la première chose à faire dans
Android Studio (Build ▸ Make Project) avant les tests sur téléphone (phase 6).

## Manette (submodule) et variantes

Le dépôt Manette du binôme est un submodule (`third_party/Manette`) compilé en bibliothèque
par le module `:manette`. Ses coques Game Boy sont l'écran de jeu ; l'émulateur s'y branche
via `ShellBinder` (et son écran Paramètres via `ManetteSettingsBinder`). Après un clone : `git submodule update --init`.

- `demo` : banque de jeux de Manette avec ses ROMs — pour la démonstration uniquement.
- `play` : aucune ROM embarquée — version destinée au Play Store.

Détails : `docs/08-integration-manette.md`.

## Structure

| Classe | Rôle |
|---|---|
| `App`, `ShellBinder`, `ManetteSettingsBinder` | Branchent l'émulateur sur les coques Game Boy et l'écran Paramètres de Manette |
| `ScreenDrawable` | Image 160×144 dessinée sur l'écran LCD de la coque |
| `EmulatorSession` | Partie en cours (émulateur, thread, son), partagée par tous les écrans |
| `EmulationThread` | Boucle `runFrame()` à 60 Hz, pause/reprise, vitesse réglable |
| `AndroidAudioOutput` | `AudioTrack` derrière `javaboy.core.AudioOutput` |
| `RomLibrary` | Bibliothèque (records, streams, favoris, dernière partie) |
| `SaveStore` | `.sav` et `.state` dans le stockage interne |
| `Settings` | Record immuable + pipeline de filtres composable |
| `LibraryActivity`, `SettingsActivity` | IHM correspondantes |
| `GameView`, `TouchPad` | Ancien écran plein écran, plus utilisés |

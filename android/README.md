# Application Android

Ouvrir **ce dossier** (`android/`) dans Android Studio. Le module `:core` est le dossier
`../core` du dépôt : le cœur n'est pas copié, il est partagé avec le harnais PC.

Le cœur est compilé en bytecode Java 17 (records, `sealed`) ; l'application aussi.
Le code Android n'a pas encore été compilé : c'est la première chose à faire dans
Android Studio (Build ▸ Make Project) avant les tests sur téléphone (phase 6).

## Structure

| Classe | Rôle |
|---|---|
| `MainActivity` | Écran de jeu, menu, sélecteur `ACTION_OPEN_DOCUMENT`, cycle de vie, save states |
| `EmulatorViewModel` | Conserve l'émulateur et son thread à travers les rotations |
| `EmulationThread` | Boucle `runFrame()` à 60 Hz, pause/reprise, vitesse réglable |
| `GameView` | `SurfaceView` + `Bitmap` 160×144 mis à l'échelle entière |
| `AndroidAudioOutput` | `AudioTrack` derrière `javaboy.core.AudioOutput` |
| `TouchPad` | Manette multitouch, haptique, transparence, zones élargies |
| `RomLibrary` | Bibliothèque (records, streams, favoris, dernière partie) |
| `SaveStore` | `.sav` et `.state` dans le stockage interne |
| `Settings` | Record immuable + pipeline de filtres composable |
| `LibraryActivity`, `SettingsActivity` | IHM correspondantes |

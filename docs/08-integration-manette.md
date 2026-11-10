# Intégration du dépôt Manette : coques Game Boy + noyau JavaBoy

_10 novembre 2026 — **non compilé avec le SDK Android** (absent de l'environnement de travail).
Le nouveau code Java a seulement été vérifié par `javac` contre des bouchons de l'API Android._

## Objectif

Les coques Game Boy du binôme (dépôt Manette) deviennent l'écran de jeu ; le noyau JavaBoy
tourne dedans. Contraintes retenues :

- Manette est un **submodule git**, compilé en **bibliothèque (AAR)** ; son dépôt n'est pas modifié ;
- la **banque de jeux** (ROMs embarquées) sert à la démo et ne doit pas partir sur le Play Store :
  deux variantes de build, `demo` et `play`.

## Architecture

```
third_party/Manette/     submodule git (dépôt du binôme, intact)
android/manette/         module :manette — enveloppe « bibliothèque » : build.gradle.kts + manifeste vide
android/app/             émulateur : session, branchement sur les coques, bibliothèque, réglages
```

| Classe (`javaboy.android`) | Rôle |
|---|---|
| `App` | Enregistre `ShellBinder` au démarrage du processus |
| `ShellBinder` | Équipe chaque coque (`BaseGameBoy`) : écran, boutons, manette physique, menu, ROM de la banque |
| `ScreenDrawable` | Dessine l'image 160×144 sur la vue `ecraninterne` de la coque |
| `EmulatorSession` | Partie en cours (émulateur, thread, son, sauvegardes), partagée par tous les écrans |

### Enveloppe `:manette`

Le dépôt Manette est une *application*. `android/manette/build.gradle.kts` applique
`com.android.library` et pointe ses `sourceSets` vers `third_party/Manette/app/src/main/{java,res}`.
Le manifeste du binôme est remplacé par un manifeste vide ; ses Activities sont déclarées dans
le manifeste de `:app`, et sa `MainActivity` (coque classique) est l'écran de lancement.
Le binôme continue à lancer son application depuis son dépôt, sans rien changer.

- `android/settings.gradle.kts` importe `third_party/Manette/gradle/libs.versions.toml`
  (`manetteLibs`) : les dépendances de `:manette` suivent celles du binôme (sauf Gson, écrit en dur chez lui).
- `./gradlew :manette:assembleRelease` produit `android/manette/build/outputs/aar/manette-release.aar`.
  `:app` en dépend par `implementation(project(":manette"))` : même bibliothèque, sans copie manuelle.

### Branchement sur les coques (`ShellBinder`)

Le code du binôme n'est ni modifié ni sous-classé : `ShellBinder` est un
`ActivityLifecycleCallbacks` qui équipe toute Activity héritant de `BaseGameBoy` à son premier
`onStart`. Ses écrans et sa navigation fonctionnent donc exactement comme dans son application.

| Élément | Mécanisme |
|---|---|
| Écran | `ScreenDrawable` posé en premier plan de `ecraninterne` ; la teinte LCD choisie dans ses paramètres reste visible autour de l'image |
| Boutons tactiles | `OnTouchListener` sur `Haut`, `Bas`, `Gauche`, `Droite`, `boutonA`, `boutonB`, `Select`, `Start` → `Joypad` |
| Manette physique | touches lues à l'entrée de la fenêtre et traduites selon les préférences `Btn_prefs` écrites par `mappage_manette` |
| Menu latéral | « Sauvegarder l'état » et « Charger une sauvegarde » rendus fonctionnels ; ajout de « Bibliothèque », « Reset », « Réglages » |
| Jeu de la banque | extra `ASSET_ROM_NAME` (asset ou URI `content://`) chargé dans la session |

### Session (`EmulatorSession`)

Changer de style lance une autre Activity : la partie ne peut pas vivre dans l'écran. La session
est unique par processus ; la coque visible s'y attache à `onResume` et s'en détache à `onPause`
(pause de l'émulation, sauvegarde de la RAM de cartouche).

## Ce qui change dans l'émulateur

- **Supprimés** : `MainActivity`, `EmulatorViewModel`, `layout/activity_main.xml`. Leur logique
  est dans `EmulatorSession` ; le layout portait le même nom que celui de Manette et l'aurait écrasé.
- **Plus utilisés, laissés en place** : `GameView` et `TouchPad` (ancien écran plein écran).
- `LibraryActivity` : charge la ROM dans la session puis revient à la coque ; son bouton
  « Ouvrir une ROM » ouvre maintenant le sélecteur de fichiers.
- Lecture de la ROM sans `InputStream.readAllBytes()` (absent avant Android 13).
- AGP 8.5.2 → **9.4.1**, Gradle 9.3.0 → **9.6.0**, `compileSdk` 34 → **37**, alignés sur le
  dépôt Manette. `minSdk` et `targetSdk` inchangés.

## Variantes

| | `demo` | `play` |
|---|---|---|
| applicationId | `javaboy.android.demo` | `javaboy.android` |
| ROMs embarquées | oui (assets du submodule) | **aucune** |
| Entrée « Banque de jeu » | banque de Manette | redirigée vers la bibliothèque de l'émulateur |
| Coques, mappage, styles, paramètres | oui | oui |

`./gradlew :app:assembleDemoDebug` · `./gradlew :app:assemblePlayRelease`

Les ROMs ne sont pas dans l'AAR : `:app` ajoute les assets du submodule à sa seule variante
`demo`. Le booléen `has_game_bank` (`res/values/flavor.xml`, surchargé dans `src/demo`) décide
de la redirection.

**Reste dans `play` :** le code de `Banque_de_jeu` (titres et descriptions du catalogue) et les
**jaquettes** (`res/drawable`), inatteignables mais présents dans l'APK. On ne peut pas les
retirer sans toucher au dépôt Manette, car `BaseGameBoy` référence `Banque_de_jeu`.

## Mise en place

```
scripts/ajouter-submodule-manette.sh <URL du dépôt Manette> [branche]   # une fois
git commit -m "Ajout du submodule Manette"
git clone --recurse-submodules …        # ou : git submodule update --init
git submodule update --remote third_party/Manette                       # suivre le binôme
```

Sans le submodule, Gradle s'arrête avec un message indiquant la commande à lancer.

## Points de couplage avec le code du binôme

Tous dans `ShellBinder`, le manifeste de `:app` et les deux `build.gradle.kts`. À revérifier à
chaque mise à jour du submodule :

- identifiants de vues et d'entrées de menu (`ecraninterne`, `Haut`…, `navigation`, `sauvegarde`, `cherche_sauv`) ;
- extra `ASSET_ROM_NAME` ; préférences `Btn_prefs` (`keycode_a`…) ;
- noms des Activities déclarées dans le manifeste ; package `com.example.manette` ;
- arborescence `app/src/main/{java,res,assets}` et `gradle/libs.versions.toml`.

## Limites connues et propositions (non implémentées)

1. **Avant la publication** : sortir du code de Manette le catalogue et les jaquettes (la
   branche `fix/bibliotheque` a déjà un `CatalogueJeux` séparé), pour qu'ils ne soient plus dans `play`.
2. La coque **Game Boy Advance** n'a ni Select ni Start : la plupart des jeux y sont
   injouables au tactile (une manette physique fonctionne).
3. Croix tactile : quatre boutons séparés, donc pas de diagonale en glissant le doigt
   (l'ancien `TouchPad` le permettait).
4. Chaque jeu lancé depuis la banque empile une nouvelle coque au-dessus de la précédente
   (comportement de Manette) : « retour » revient à l'ancienne coque, avec la même partie.
5. `:manette` est compilé avec `minSdk 26` alors que le binôme déclare 30 : non testé sur Android 8 à 10.
6. Les ROMs et jaquettes sont des œuvres protégées : l'APK `demo` ne doit pas être diffusé,
   et elles restent dans l'historique git du dépôt Manette.

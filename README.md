# JavaBoy — portage et modernisation Android

Portage de l'émulateur Game Boy **JavaBoy 0.92** (Neil Millstone, 2001-2005, GPL v2)
vers Android, avec un cœur d'émulation découplé, une modernisation fonctionnelle
mesurée, et un travail d'IHM. Aucune ROM commerciale n'est incluse.

```
core/      cœur d'émulation, Java pur (aucun import AWT/applet/son), tests JUnit 5
desktop/   harnais Swing pour tester sur PC :  ./gradlew :desktop:run --args=rom.gb
android/   projet Android Studio (module :app + le même :core)
extras/    imprimante Game Boy et câble link TCP, mis de côté
docs/      fiche état initial, découplage, benchmark, protocole de test, plan du rapport
```

## Construire

- Gradle (recommandé) : `./gradlew :core:test :desktop:run`
- Sans Gradle : `./build.sh` puis `java -cp build/classes javaboy.desktop.DesktopMain rom.gb`
- Tests hors-ligne : `./test.sh` (mini-runner compatible JUnit 5 dans `tools/junit-shim`)
- Benchmark : `java -cp build/test-classes javaboy.core.bench.Benchmark 3000`
- Android : ouvrir `android/` dans Android Studio.

## Licence

GPL v2, comme l'original (`GPL.txt`). L'historique Git distingue l'import du code
original (premier commit) des modifications apportées ensuite.

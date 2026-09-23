#!/bin/sh
# Compilation sans Gradle (secours) : ./build.sh puis java -cp build/classes javaboy.desktop.DesktopMain [rom]
set -e
JAVAC=${JAVAC:-javac}
rm -rf build/classes && mkdir -p build/classes
"$JAVAC" -encoding UTF-8 -nowarn -d build/classes $(find core/src/main/java desktop/src/main/java -name '*.java')
echo "OK -> build/classes"

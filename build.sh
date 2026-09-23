#!/bin/sh
# Compilation sans Gradle (secours) : ./build.sh puis java -cp build/classes javaboy.JavaBoy
set -e
JAVAC=${JAVAC:-javac}
rm -rf build/classes && mkdir -p build/classes
"$JAVAC" -encoding ISO-8859-1 -nowarn -d build/classes $(find src/main/java -name '*.java')
echo "OK -> build/classes"

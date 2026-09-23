#!/bin/sh
# Tests hors-ligne : compile core + shim JUnit + tests, puis exécute MiniRunner.
set -e
JAVAC=${JAVAC:-javac}
OUT=build/test-classes; rm -rf $OUT; mkdir -p $OUT
"$JAVAC" -encoding UTF-8 -nowarn -d $OUT $(find core/src/main/java tools/junit-shim core/src/test/java -name '*.java')
java -cp $OUT MiniRunner javaboy.core.CartridgeTest javaboy.core.CpuTest javaboy.core.GraphicsChipTest javaboy.core.PngExportTest javaboy.core.BlarggTest "$@"

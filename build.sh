#!/bin/sh
# Builds dist/metajava-<version>.jar with a plain JDK (no Maven, no downloads).
# The JAR targets Java 11 (class-file version 55), so it runs on Java 11 or later.
# Usage: ./build.sh [--test]
set -e
VERSION=0.1.0
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/classes build/test-classes dist
javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/* build/classes/
printf 'Main-Class: org.metajava.gui.MainWindow\nImplementation-Title: metajava\nImplementation-Version: %s\n' "$VERSION" > build/manifest.txt
jar --create --file "dist/metajava-$VERSION.jar" --manifest build/manifest.txt -C build/classes .
echo "Built dist/metajava-$VERSION.jar"
if [ "$1" = "--test" ]; then
  javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options -cp build/classes -d build/test-classes src/test/java/org/metajava/SelfTest.java
  java -Djava.awt.headless=true -cp build/classes:build/test-classes org.metajava.SelfTest
  javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options -cp build/classes -d build/test-classes src/test/java/org/metajava/plot/LabelPlacerTest.java
  java -Djava.awt.headless=true -cp build/classes:build/test-classes org.metajava.plot.LabelPlacerTest
fi

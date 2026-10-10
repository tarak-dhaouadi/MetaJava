@echo off
rem Builds dist\metajava-0.1.0.jar with a plain JDK (no Maven, no downloads).
rem The JAR targets Java 11 (class-file version 55), so it runs on Java 11 or later.
setlocal
set VERSION=0.1.0
cd /d "%~dp0"
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes build\test-classes dist
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options -d build\classes @build\sources.txt || exit /b 1
xcopy /s /y /q src\main\resources\* build\classes\ >nul
(echo Main-Class: org.metajava.gui.MainWindow& echo Implementation-Title: metajava& echo Implementation-Version: %VERSION%) > build\manifest.txt
jar --create --file dist\metajava-%VERSION%.jar --manifest build\manifest.txt -C build\classes . || exit /b 1
echo Built dist\metajava-%VERSION%.jar
if "%1"=="--test" (
  javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options -cp build\classes -d build\test-classes src\test\java\org\metajava\SelfTest.java || exit /b 1
  java -Djava.awt.headless=true -cp build\classes;build\test-classes org.metajava.SelfTest
)

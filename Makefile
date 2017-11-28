#!/bin/bash

os="`uname`"

echo Your OS is $os

: <<'END'

case $os in
	Linux*)
		find -name "*.java" > sources.txt
		find -name "*.target" | tr "\n" ":" > jars.txt
		;;
	MINGW32_NT-6.2*)
		find -name "*.java" > sources.txt
		find -name "*.target" | tr "\n" ";" > jars.txt
		;;
	*)
		dir /s /B *.java > sources.txt
		dir /s /B *.jar > jars.txt
		;;
esac

javac -cp @sources.txt
END

echo BEGINNING CLEANING
echo ___________________________
mvn clean
echo
echo
echo BEGINNING COMPILING
echo ___________________________
mvn compile
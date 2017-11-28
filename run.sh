#!/bin/bash

# Runs MultiCast Video program
src=src
jars=jars/*

os="`uname`"

echo Your OS is $os

# If not linux, we assume Windows.
#case $os in
#	Linux*) path=$src:$jars ;;
#	MINGW32_NT-6.2*) path="$src;$jars" ;;
#	*) path="$src;$jars" ;;
#esac

echo $path

#java -Xmx512M -cp $path -Djava.net.preferIPv4Stack=true ClientApplication
mvn exec:java -Dexec.mainClass="ClientApplication"
#mvn exec:java -Dexec.mainClass="Test"

#!/bin/bash

#java -Xmx512M -cp $path -Djava.net.preferIPv4Stack=true ClientApplication
mvn exec:java -Dexec.mainClass="CMNode"

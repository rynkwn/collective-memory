JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH:=${JAVA_HOME}/bin:${PATH}

#find Hadoop path using /home/cs-local-linux/339/hadoop-2.7.4/bin/hadoop classpath

SRC = $(wildcard *.java)

all: build

build: ${SRC}
	${JAVA_HOME}/bin/javac -Xlint -classpath ${CLASSPATH} ${SRC}
	#${JAVA_HOME}/bin/jar cvf build.jar *.class lib

clean:
	rm *~


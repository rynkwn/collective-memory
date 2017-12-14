# collective-memory

# To Compile:

Maven is required. You can check if maven is locally available by running `mvn -v`

Run these commands: `mvn clean`, and then `mvn build`. However,
this won't produce the same jar file provided. That jar file is produced using the Eclipse IDE,
and is fully self-contained. I haven't quite been able to get Maven to produce a similar jar,
as the external jars appear to be giving me some trouble.

# To run
`java -jar collective_memory.jar`

You can also specify an IP address and port to directly connect to.

`java -jar collective_memory.jar 123.445.68.581 51325`
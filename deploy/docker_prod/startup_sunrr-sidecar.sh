#!/bin/bash

SIDECAR_JAR_NAME=ipc-client-1.5.0-jar-with-dependencies.jar

JAVA_OPTS="-Xms2048m -Xmx2048m -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:NewSize=948m -XX:MaxNewSize=948m -XX:SurvivorRatio=18"

java $JAVA_OPTS -Dipc.file.path=/opt/ipc/$HOSTNAME/sunRR \
     -Dipc.file.name.prefix=ipctmp \
     -Dipc.checkpointfile.path=/opt/ipc/$HOSTNAME/sunRR \
     -Dipc.checkpointfile.name=readcheckpoint \
     -Dbootstrap.servers=10.112.5.33:9092,10.112.5.34:9092,10.112.5.35:9092,10.112.5.36:9092,10.112.5.37:9092,10.112.5.38:9092 \
     -Dtopic.name=s-sun-rr \
     -jar /opt/sun-rr-sidecar/$SIDECAR_JAR_NAME

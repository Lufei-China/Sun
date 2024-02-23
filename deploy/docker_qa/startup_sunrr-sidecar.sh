#!/bin/bash

SIDECAR_JAR_NAME=ipc-client-1.5.0-jar-with-dependencies.jar

JAVA_OPTS="-Xms1024m -Xmx1024m -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:NewSize=512m -XX:MaxNewSize=512m -XX:SurvivorRatio=18"

java $JAVA_OPTS -Dipc.file.path=/opt/ipc/$HOSTNAME/sunRR \
     -Dipc.file.name.prefix=ipctmp \
     -Dipc.checkpointfile.path=/opt/ipc/$HOSTNAME/sunRR \
     -Dipc.checkpointfile.name=readcheckpoint \
     -Dipc.dstfile.path=/opt/ipc/$HOSTNAME/final \
     -Dipc.dstfile.name.prefix=sunRR \
     -jar /opt/sun-rr-sidecar/$SIDECAR_JAR_NAME

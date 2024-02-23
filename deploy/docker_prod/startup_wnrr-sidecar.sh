#!/bin/bash

SIDECAR_JAR_NAME=ipc-client-1.5.0-jar-with-dependencies.jar

JAVA_OPTS="-Xms1048m -Xmx1048m -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:NewSize=496m -XX:MaxNewSize=496m -XX:SurvivorRatio=18"

java $JAVA_OPTS -Dipc.file.path=/opt/ipc/$HOSTNAME/winnoticeRR \
     -Dipc.file.name.prefix=ipctmp \
     -Dipc.checkpointfile.path=/opt/ipc/$HOSTNAME/winnoticeRR \
     -Dipc.checkpointfile.name=readcheckpoint \
     -Dbootstrap.servers=10.112.5.75:9092,10.112.5.76:9092,10.112.5.77:9092 \
     -Dtopic.name=s-sun-winnotice \
     -jar /opt/wn-rr-sidecar/$SIDECAR_JAR_NAME

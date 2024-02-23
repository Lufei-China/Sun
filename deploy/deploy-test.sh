#!/bin/bash

APOLLO_CONFIG_SERVICE_ADDR="-Dapollo.meta=http://10.112.0.29:8080"
JAVA_OPTS="-Xms4096m -Xmx4096m -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=384m -XX:NewSize=2048m -XX:MaxNewSize=2048m -XX:SurvivorRatio=18"
JAR_NAME=inmobi-supply-solar-component-1.0.3-SNAPSHOT.jar
nohup java JAVA_OPTS $APOLLO_CONFIG_SERVICE_ADDR \
    -Denv='dev' \
    -Djava.util.concurrent.ForkJoinPool.common.parallelism=60 \
    -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
    -jar /opt/supply-sun-project/$JAR_NAME >/dev/null 2>&1 &
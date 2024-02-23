#!/bin/bash

APOLLO_CONFIG_SERVICE_ADDR="-Dapollo.meta=http://10.114.3.180:8080"
JAR_NAME=inmobi-supply-sun.jar

exec nohup java -Xms2g -Xmx2g -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=384m -XX:NewSize=2048m -XX:MaxNewSize=2048m \
    $APOLLO_CONFIG_SERVICE_ADDR \
    -Djava.util.concurrent.ForkJoinPool.common.parallelism=1000 \
    -Denv=$env \
    -DsunRR.file.path=/opt/ipc/$HOSTNAME/sunRR \
    -DsunRR.file.name.prefix=ipctmp \
    -DwinnoticeRR.file.path=/opt/ipc/$HOSTNAME/winnoticeRR \
    -DwinnoticeRR.file.name.prefix=ipctmp \
    -Dfile.encoding=utf-8 \
    -DLogCenterKafkaBrokers=10.114.5.128:9092,10.114.5.129:9092,10.114.5.130:9092 \
    -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
    -Dproject.name=sun \
    -Dcsp.sentinel.dashboard.server=10.114.1.205:9090 \
    -Dlog4j.configurationFile=classpath:log4j2-fat.xml \
    -jar /opt/sun/$JAR_NAME
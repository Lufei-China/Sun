#!/bin/bash

JAR_NAME=inmobi-supply-sun.jar
if [[ "$cluster" == 'uat' ]]; then
    LOG_4J2=classpath:log4j2-uat.xml
else
    LOG_4J2=classpath:log4j2-pro.xml
fi
# G1垃圾收集器没有fullGc，只有mixedGc
JAVA_OPTS="-Xms10g -Xmx10g -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=384m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 \
          -XX:+PrintGCDetails -XX:+PrintReferenceGC -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC \
          -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/opt/sun/logs -XX:ErrorFile=/opt/sun/logs/jvm-error.log -Xloggc:/opt/sun/logs/gc.log "
JAVA_OPTS1="-Xms10g -Xmx10g -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=384m -XX:NewSize=5120m -XX:MaxNewSize=5120m \
            -XX:+UseConcMarkSweepGC -XX:+CMSScavengeBeforeRemark -XX:+UseCMSCompactAtFullCollection -XX:CMSFullGCsBeforeCompaction=5 \
            -XX:+PrintGCDetails -XX:+PrintReferenceGC -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC \
            -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/opt/sun/logs -XX:ErrorFile=/opt/sun/logs/jvm-error.log -Xloggc:/opt/sun/logs/gc.log "
JAVA_OPTS2="-Xms12g -Xmx12g -Xss256k -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=384m -XX:NewSize=6g -XX:MaxNewSize=6g \
            -XX:+PrintGCDetails -XX:+PrintReferenceGC -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC \
            -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/opt/sun/logs -XX:ErrorFile=/opt/sun/logs/jvm-error.log -Xloggc:/opt/sun/logs/gc.log "

exec nohup java  $JAVA_OPTS2\
    -Denv=$env \
    -Dfile.encoding=utf-8 \
    -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
    -Dproject.name=sun \
    -Dcsp.sentinel.dashboard.server=10.112.1.202:9090 \
    -jar /opt/sun/$JAR_NAME

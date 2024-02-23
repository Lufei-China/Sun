FROM acr-test01-registry.cn-beijing.cr.aliyuncs.com/inmobi_sy/centos-jdk8:v1.1

# 作者描述信息
MAINTAINER remi.li  @inmobi.com


#supervisor
RUN mkdir -p /opt/supervisord
COPY ./supervisord.conf /opt/supervisord/

#sunRR sidecar
RUN mkdir -p /opt/sun-rr-sidecar
COPY ./ipc-client-1.5.0-jar-with-dependencies.jar /opt/sun-rr-sidecar
COPY ./startup_sunrr-sidecar.sh /opt/sun-rr-sidecar
RUN chmod 777 /opt/sun-rr-sidecar/startup_sunrr-sidecar.sh

#wn sidecar
RUN mkdir -p /opt/wn-rr-sidecar
COPY ./ipc-client-1.5.0-jar-with-dependencies.jar /opt/wn-rr-sidecar
COPY ./startup_wnrr-sidecar.sh /opt/wn-rr-sidecar
RUN chmod 777 /opt/wn-rr-sidecar/startup_wnrr-sidecar.sh

#sun
RUN mkdir -p /opt/sun/logs
COPY ./inmobi-supply-sun.jar /opt/sun
COPY ./startup_sun.sh /opt/sun
RUN chmod 777 /opt/sun/startup_sun.sh

COPY ./wurfl.zip /opt/sun
RUN chmod 777 /opt/sun/wurfl.zip

CMD ["/usr/bin/supervisord","-c","/opt/supervisord/supervisord.conf"]
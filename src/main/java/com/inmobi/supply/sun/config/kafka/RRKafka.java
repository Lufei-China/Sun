package com.inmobi.supply.sun.config.kafka;

import lombok.NonNull;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * 功能概述
 * className:      SunKafka
 * package:        com.inmobi.supply.sun.config.kafka
 * author:         Gavin.Xu
 * date:           2022/2/16
 */
@Service
public class RRKafka {

    @Value("${rr.brokers}")
    private String brokers1;
    @Value("${rr.topic}")
    private String topic1;

    @Value("${winnotice.brokers}")
    private String brokers2;
    @Value("${winnotice.topic}")
    private String topic2;

    @Bean
    KafkaSender<String,String> rrSender(){
        return getKafkaSender(brokers1);
    }

    @Bean
    KafkaSender<String,String> winNoticeSender(){
        return getKafkaSender(brokers2);
    }

    @NonNull
    private KafkaSender<String,String> getKafkaSender(String brokers2) {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers2);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, "0");
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, "200");
        producerProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "67108864");
        producerProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        SenderOptions<String, String> senderOptions = SenderOptions.create(producerProps);
        return KafkaSender.create(senderOptions);
    }


}

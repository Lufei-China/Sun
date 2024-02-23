package com.inmobi.supply.sun.utils;

import cn.inmobi.pb.sun.rr.SunRR;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import javax.annotation.Resource;
import java.util.Base64;

/**
 * kafka发送工具类
 *
 * @author remi.li
 */
@Slf4j
@Service
public class SendKafkaUtils {

    @Resource(name = "rrSender")
    KafkaSender<String, String> rrSender;

    @Resource(name = "winNoticeSender")
    KafkaSender<String, String> winNoticeSender;


    public void sendRR2(SunRR sunRR) {
        rrSender.send(
                Mono.just(
                        SenderRecord.create(new ProducerRecord<>("s-sun-rr",
                            Base64.getEncoder().withoutPadding().encodeToString(sunRR.toByteArray())
                        ), "key")
                )
        ).subscribe();
    }

    public void sendServedRR(SunRR sunRR) {
        rrSender.send(
                Mono.just(
                        SenderRecord.create(new ProducerRecord<>("s-sun-served",
                                Base64.getEncoder().withoutPadding().encodeToString(sunRR.toByteArray())
                        ), "key")
                )
        ).subscribe();
    }

    public void sendWinNotice2(WinNotice winNotice) {
        winNoticeSender.send(
                Mono.just(
                        SenderRecord.create(new ProducerRecord<>("s-sun-winnotice-new",
                                Base64.getEncoder().withoutPadding().encodeToString(winNotice.toByteArray())
                        ), "key")
                )
        ).subscribe();
    }


}
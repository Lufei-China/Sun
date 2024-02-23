package com.inmobi.supply.sun;

import com.inmobi.supply.framework.common.utils.json.ObjectMapperUtils;
import com.inmobi.supply.sun.config.GeTuiEnrichConfig;
import com.inmobi.supply.sun.constant.RequestHeader;
import com.inmobi.supply.sun.model.getui.GeTuiAuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * @author zeki.zhao
 */
@Slf4j
public class GeTuiTest {

    @Test
    public void test() {

        String userCode = "inmobi";
        String authCode = "Up0ukc7u2N71jG3dcfCz15";
        String authUrl = "http://api-inmobi.getui.com:8081/accesser/auth";


        long ts = System.currentTimeMillis();
        String md5OfTS = DigestUtils.md5Hex(Long.toString(ts));
        String sign1 = DigestUtils.md5Hex(userCode + md5OfTS);
        String sign2 = DigestUtils.md5Hex(userCode + authCode + md5OfTS);

        System.out.println("md5OfTS : " + md5OfTS);
        System.out.println("sign1 : " + sign1);
        System.out.println("sign2 : " + sign2);
        System.out.println("ts : " + ts);

        StringBuilder sb = new StringBuilder(authUrl)
                .append("?user_code=").append(userCode)
                .append("&sign1=").append(sign1)
                .append("&sign2=").append(sign2)
                .append("&timestamp=").append(ts);

        System.out.println(sb);

        String authRes = HttpClient.create()
                .headers(h -> h.set("Accept", RequestHeader.GETUI_ACCEPT_HEADER))
                .get()
                .uri(sb.toString())
                .responseContent()
                .aggregate()
                .asString()
                .timeout(Duration.ofSeconds(10), Mono.error(new TimeoutException("GeTui auth Timeout")))
                .onErrorResume(throwable -> {
                    System.out.println(throwable.getMessage());
                    return Mono.empty();
                })
                .block();

        System.out.println(authRes);
    }


    @Test
    public void test2() {
        GeTuiEnrichConfig config = new GeTuiEnrichConfig();
        System.out.println(ObjectMapperUtils.toJSON(config));

        String imei = "1c5a0f9e83ff76283b5794ba8473cd7e";
        System.out.println(DigestUtils.md5Hex(imei));
    }


    @Test
    public void test3() {
        String res = "{\"access_token\":\"9825604a01a1a092c350e28e925af03c\",\"code\":\"0\",\"expires_in\":7200}";
        System.out.println(ObjectMapperUtils.fromJSON(res, GeTuiAuthResponse.class));
    }

    @Test
    public void test4() {
        Mono.just(1/0).flatMap( x1 -> {
            int a = 1/x1;
            return Mono.just(1);
        }).flatMap(x2 -> {
            return Mono.just(3);
        }).onErrorResume(throwable -> {
            System.out.println(throwable.getMessage());
            return Mono.just(5);
        }).subscribe();

    }
}

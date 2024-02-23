package com.inmobi.supply.sun;

import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.api.App;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.aliyun.oss.OSS;
import com.google.protobuf.InvalidProtocolBufferException;
import com.inmobi.supply.sun.config.dynamic.autoConf.DynamicConfigService;
import com.inmobi.supply.sun.model.handset.HandsetEntity;
import com.inmobi.supply.sun.service.postProc.auction.AuctionService;
import com.inmobi.supply.sun.service.preProc.call.SendRequestService;
import com.inmobi.supply.sun.service.preProc.normalizer.NormalizerService;
import com.inmobi.supply.sun.utils.HttpClientUtils;
import com.inmobi.supply.sun.utils.SendKafkaUtils;
import lombok.extern.slf4j.Slf4j;
import net.ipip.ipdb.City;
import net.ipip.ipdb.CityInfo;
import net.ipip.ipdb.District;
import net.ipip.ipdb.DistrictInfo;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import sun.misc.BASE64Decoder;

import java.io.*;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullTest {
    private WebTestClient client;
    private WebTestClient client2;

    @LocalServerPort
    private int port = 8120;
    @Autowired
    SendKafkaUtils sendKafkaUtils;
    @Autowired
    SendRequestService sendRequestService;
    @Autowired
    AuctionService auctionService;
    @Autowired
    NormalizerService normalizerService;
    @Autowired
    OSS ossClient;
    @Autowired
    DynamicConfigService dynamicConfigService;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> stringTemplate;

    @Before
    public void init() {
        client = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10))
                .baseUrl("http://localhost:" + this.port)
                .defaultHeader("Content-Type", "text/plain")
                .build();
        client2 = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10))
                .baseUrl("http://localhost:" + this.port)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Test
    public void Test1() {
        Map<String, Object> m = new HashMap<>();
        m.put("aa", "bb");
        client.post().uri("/solar/sdk")
                .bodyValue(m.toString())
                .exchange().expectStatus().is2xxSuccessful();
    }

    @Test
    public void Test2() {
        Map<String, Object> m = new HashMap<>();
        m.put("aa", "bb");
        client2.post().uri("/solar/api/v3.3")
                .bodyValue(m.toString())
                .exchange().expectStatus().is2xxSuccessful();


    }

    @Test
    public void TestWinNotice() {
        List<WinNotice> wins = new ArrayList<>();
        WinNotice.Builder winBuilder = WinNotice.newBuilder();
        winBuilder.setEvent(WinNotice.Event.WIN);
        winBuilder.setMethod(WinNotice.Method.GET);
        winBuilder.setUrl("https://dsp.toutiao.com/api/common/ads/win?extra=BceCYZrtV4biNgoQhMHfhyGWVmurX1jLiZypTC2sVhjuNHJyJbV7ljq6aJGlF_gFn-S4Vd52Rovh4cVt1uwXo-IfjdIHyw3E_4mR1z_fqRZo5qK4r_7SBub-J4ZjEFCSyv3z7RmXVAQ0ohrt3I3qEm4YsfmQ6VXQ-WdLvaUxBdvUtwoj0MFItH1psDEOGmqV-uVmplS-rKtVues_Y_tlE5qCVXgd_905MV1U8_hvF-fumvesubKlz2dOMDueZ_Aa78Gnabrt7AXcD4NFQausazKHp3szAUJQWlhac93B9JhqJ1BlrcpbOcGZc4sO4GCRuPpWU7Xz4sxgeapGOf-E3k1BDFJnzmrnnKa_Z8GsWaeHh3zAjeLL_LQmY4psZLCN0-CzIIrVnfZeojo6xCFSmeW7ZRRBMty7Unw9DI8dEa6g1KWI6rAkpXxPgRRSc3H_68HmtmmHAxiaTlkcUrjBG8GI0kBf2-D1wWj7C87MEiCvpHr-V-0Hku4SHRMwefjngAylGVTPl858l2-QJh0yx-djVKWROilG6X5kbhiL8KHVFcmYmjH8wzsQrec5z4KQJ0jxCVU0trMA6O69OuGYBDmYV73nx-V_rRteln9hYopg7toe-xRKIz2AoNMyKHtU7DAZcp_GPaK7SPVn2Fd_sJe6Z19G-b7Vm_-8wI4oZ16ilPr6PH4K2NqtOlvhEa8TdtSI-JkSUOREoBHI9mZloBKYRUScHtdfqQCRX9ZT2hgFCzoobrvTEqw6mc0OOtwyTTzLiKd_6ejUTUraCpsvHMniBLegVCe2CHX41P_lB37zjrrY0fwO_2fqfZYwyBpGgKIjcyi2S3w7ltlHTz5hyMoEtTIKbv_4zeQBK8ct9AlZnJUEzLEsC-WGTPJywOJ927dgG9XlIOOfocVJLql0m1fSNB7S-tKF9jZ9qNEpcbY7a1m-TmIduG4Gxnsx9aFqKr116XDvQyO5YfCqBky9s_vqdJtqoAMclajT2nc6q6GgZX-exrxf4FpqeYdpoVgKN8VWqLdI0Wb-G-Wq192nGyQWsgRJ7QaiNyBeRlFav1dL5XMMrEIl8WLKX_KjyoFQNFV8adJnWFeeCFZaNBwzWcNrCIHmj5-L-JTJL6ab85nDoMJlR1vFqz4AOZdZVjxhNpdTDKCeogTC64297uqjAn9T8L6jLUiTxZjP9B4i_6FgB1br7tNl63rYNYrO9bhw1V5DpY218eerF5fxhKpk0RVb146PQk0-JLN_NWPQWYvDAm1pQ3snS79v3PR5-854MFRBogBJbjKqjIhaunkFT44XMW4bB2E0VPKq_6BSbY2jFrcXS1oIRlVXKdAbCOxuOVQGlfo1lWw5JYiG1Qaw2Gj7q6ZZ2qAP9BoTCqYBCJ9wLKywbPG1NPR0nUChxnnVOH5iQhkbye6P7GyeIunLa_hAB306CFwk2_WhCK0B9vh6A9Q_p8w3FuZU3jrR7Q==&price=100");
        winBuilder.setSaturnDsp("ToutiaoRTB");
        wins.add(winBuilder.build());
        try {
            WinNotice winNotice = WinNotice.parseFrom(winBuilder.build().toByteArray());
            Map<String, String> map = winNotice.getHeadersMap();
            System.out.println(map);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        wins.forEach(url -> sendKafkaUtils.sendWinNotice2(url));
    }

    @Test
    public void TestProtoBuf() {
        HttpClientUtils httpClientUtils = new HttpClientUtils();
        ApiV3Request.Builder builder = ApiV3Request.newBuilder();
        builder.setApp(App.newBuilder().setBundle("test Bundle").build());
        try {
            httpClientUtils.doPost4ProtocleBuffer("http://localhost:8080/showad/v3.3", builder.build().toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIpip() throws Exception {
        InputStream mydata4vipchina3CnIn = getClass().getResourceAsStream("/ipip/mydata4vipchina3_cn.ipdb");
        InputStream quxianIn = getClass().getResourceAsStream("/ipip/quxian.ipdb");

        City city = new City(mydata4vipchina3CnIn);
        District district = new District(quxianIn);
        CityInfo cityInfo = city.findInfo("61.146.0.95", "CN");
        DistrictInfo districtInfo = district.findInfo("61.146.0.95", "CN");
        System.out.println(cityInfo);
        System.out.println("-----");
        System.out.println(districtInfo);
    }


    @Test
    public void test() throws Exception {
        InputStream inputStream = getClass().getResourceAsStream("/FL-LX-A002.txt");
        BufferedReader br = new BufferedReader(new BufferedReader(new InputStreamReader(inputStream)));
        String line;
        int count = 0;
        while ((line = br.readLine()) != null) {
            if (StringUtils.isNotEmpty(line)) {
                count++;
            }
        }
        System.out.println(count);
    }
}
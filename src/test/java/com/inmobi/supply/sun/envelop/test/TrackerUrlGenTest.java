package com.inmobi.supply.sun.envelop.test;

import cn.inmobi.pb.api.Ad;
import cn.inmobi.pb.api.AdType;
import cn.inmobi.pb.api.MaterialMeta;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.sun.constant.ChargeAndPay;

import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.TrackingUrlGen;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TrackerUrlGenTest {

    @Autowired
    private TrackingUrlGen trackingUrlGen;

    private String testData = "122\t1128\t73\t1\thttp://toutiao\n" +
            "            121\t1129\t74\t16\thttps://10.14.41.60:8080/\n" +
            "            121\t1129\t76\t16\thttps://10.14.41.60:8080/\n" +
            "            121\t1129\t77\t16\thttps://10.14.41.60:8080/\n" +
            "            125\t1131\t78\t14\thttps://apps.apple.com/cn/app/id586871187\n" +
            "            125\t1132\t79\t14\thttps://apps.apple.com/cn/app/id586871187\n" +
            "            122\t1133\t80\t1\thttp://toutiao\n" +
            "            126\t1134\t81\t17\thttps://ccc-x.jd.com/dsp/nc?ext=aHR0cHM6Ly9wcm8ubS5qZC5jb20vbWFsbC9hY3RpdmUvM0xva29OQmtKN1Nicm9TU3VjcTZ6SGg3SmlhWi9p\n" +
            "            122\t1135\t82\t1\thttp://toutiao\n" +
            "            127\t1136\t83\t17\thttps://ccc-x.jd.com/dsp/nc?ext=aHR0cHM6Ly9wcm8ubS5qZC5jb20vbWFsbC9hY3RpdmUvM0xva29OQmtKN1Nicm9TU3VjcTZ6SGg3SmlhWi9p\n" +
            "            128\t1137\t84\t1\thttps://fallback";

    @Test
    public void TestImpressionUrl() {

        RunTimeContext runTimeContext = mockRuntimeContext(122, 1128, 73, "http://toutiao");
        runTimeContext.setAdType(AdType.INTERSTITIAL);
        //trackingUrlGen.process(runTimeContext);
        String result = runTimeContext.getAdRunTimeInfo().getBeaconUrl();
        assertTrue(!result.isEmpty());
        System.out.println(result);
    }

    private void genOneUrl(int campaignId, int adGroupId, int advAccountId, String landingUrl) {
        RunTimeContext runTimeContext = mockRuntimeContext(campaignId, adGroupId, advAccountId, landingUrl);
        runTimeContext.setAdType(AdType.INTERSTITIAL);
        //trackingUrlGen.process(runTimeContext);
        String result = runTimeContext.getAdRunTimeInfo().getBeaconUrl();
        result = result.replace("127.0.0.1", "127.0.0.1:8000");
        result = result + "?m=18&TS=123";
        System.out.println(result);

    }

    @Test
    public void testAllImpressionUrl() {

        String[] lines = testData.split("\n");
        for (String line : lines) {
            String[] cols = line.split("\t");
            genOneUrl(Integer.parseInt(cols[0].trim()), Integer.parseInt(cols[1].trim()),
                    Integer.parseInt(cols[2].trim()), cols[3].trim());
        }
    }

    private RunTimeContext mockRuntimeContext(int campaignId, int adGroupId, int advAccountId, String landingUrl) {
        RunTimeContext rc = new RunTimeContext();

        MaterialMeta materialMeta = MaterialMeta.newBuilder()
                .setTargetUrl(landingUrl)
                .build();


        rc.setRequestId("12345678990" + new Random().nextInt(10000));
        rc.setPlacementId(1503835463516l);
        Ad windAd = Ad.newBuilder()
                .setAdId("adid"+new Random().nextInt(10000))
                .setBidPrice(120)
                .setAdvAccId(advAccountId)
                .setGroupId(adGroupId)
                .setCampId(campaignId)
                .setCreative(materialMeta)
                .build();

//        BidResponse winBidResponse = BidResponse.newBuilder()
//                .addAds(windAd)
//                .build();
        rc.setWinAd(windAd);
        AppModel appModel = new AppModel();
        appModel.setAppId("1504398947719");
        appModel.setPubAccountId("2a1db671e0ed45d7893ab59496743dac");
        rc.setAppModel(appModel);



        Map<ChargeAndPay, Long> chargeAndPayLongMap = new HashMap<>();
        chargeAndPayLongMap.put(ChargeAndPay.CHARGE, 12l);
        chargeAndPayLongMap.put(ChargeAndPay.PAY, 8l);
        rc.setChargeAndPayMap(chargeAndPayLongMap);
        return rc;
    }
}

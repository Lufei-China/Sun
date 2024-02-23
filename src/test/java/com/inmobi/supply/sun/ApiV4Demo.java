package com.inmobi.supply.sun;

import com.stars.bid.pb.*;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.util.Arrays;

public class ApiV4Demo {

    @Test
    public void test() throws IOException {
        BidRequest bidRequest = BidRequest.newBuilder()
                .setRequestId("2AF968FA840140AEC0A8C72201000000")
                .setApp(App.newBuilder()
                        .setBundleId("com.snda.wifilocating")
                        .setStoreUrl("https://app.cdo.oppomobile.com/home/detail?app_id=13160")
                        .build())
                .setSite(Site.newBuilder()
                        .setId("2000000000002970")//inmobi 平台申请的广告位id
                        .setAdType(1)//开屏或者信息流 自助选择
                        .addCreativeSpecs(CreativeSpecs.newBuilder().setSupportCreativeType(CreativeType.IMAGE).setSupportSizes(Size.newBuilder().setW(720).setH(1280).build()).build())//支持的素材类型 选填
                        .setSupport302(true)//是否支持302跳转，默认不支持
                        .build())
                .setImp(Imp.newBuilder().setFloorPrice(1500).build())
                .setDevice(
                        Device.newBuilder()
                                .setUa("Mozilla/5.0 (Linux; Android 10; HLK-AL00 Build/HONORHLK-AL00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/83.0.4103.106 Mobile Safari/537.36")
                                .setIpv4("110.231.160.200")
                                .setImeiMd5("")
                                .setAndroidIdMd5("bdd9516e02c8c3cfdd02fe0a48c33cd0")
                                .setOaidMd5("9a0a6988346433267bdc399eb6ab328b")
                                .setDeviceType(DeviceType.PHONE)
                                .setH(2224)
                                .setW(1080)
                                .setOs(OsType.ANDROID)
                                .setMake("huawei")
                                .setModel("hlk-al00")
                                .setCarrier(Carrier.UNICOM)
                                .setConnType(ConnType.WIFI)
                                .build())
                .setTmax(300)
                .build();

        System.out.println(bidRequest);

        byte[] bytes1 = bidRequest.toByteArray();
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost post = new HttpPost();
        try {
            //URI uri = URI.create("https://union.api.w.inmobi.cn/showad/v4.0");
            URI uri = URI.create("https://uat-api-w.inmobi.cn/showad/v4.0");
            post.setURI(uri);
            Header[] headers = new Header[]{
                    new BasicHeader("Content-Type", "application/octet-stream"),
                    new BasicHeader("Accept-Encoding", "gzip"),
            };
            post.setHeaders(headers);
            HttpEntity entity = new ByteArrayEntity(bytes1);
            post.setEntity(entity);
            CloseableHttpResponse response = httpclient.execute(post);
            Header[] allHeaders = response.getAllHeaders();
            int statusCode = response.getStatusLine().getStatusCode();//200 请求成功
            System.out.println("status code:"+statusCode);
            Arrays.stream(allHeaders).forEach(System.out::println);
            byte[] bytes = EntityUtils.toByteArray(response.getEntity());
            BidResponse bidResponse = BidResponse.parseFrom(bytes);
            System.out.println(bidResponse.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        cn.inmobi.pb.api.Geo.Builder geoBuilder = cn.inmobi.pb.api.Geo.newBuilder();
        geoBuilder.setCaaStateName("重庆市");
        System.out.println(geoBuilder);
    }
}

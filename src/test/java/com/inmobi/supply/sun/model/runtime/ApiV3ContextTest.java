package com.inmobi.supply.sun.model.runtime;

import static org.junit.jupiter.api.Assertions.*;

import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.api.ApiV3Request.Builder;
import cn.inmobi.pb.sun.rr.api.App;
import cn.inmobi.pb.sun.rr.api.Device;
import cn.inmobi.pb.sun.rr.api.Device.Geo;
import cn.inmobi.pb.sun.rr.api.Imp;
import com.inmobi.supply.sun.model.common.LatLong;
import org.junit.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
class ApiV3ContextTest {

  ApiV3Request apiV3Request;

  ApiV3Context apiV3Context;

  @BeforeEach
  public void init() {
    apiV3Request = ApiV3Request.newBuilder()
            .setDevice(Device.newBuilder()
                    .setUa("dummyUserAgent")
                    .setIp("ip")
                    .setIpv6("ipv6")
                    .setGeo(Geo.newBuilder()
                            .setLat(12.5F)
                            .setLon(27.5F)
                            .build())
                    .build())
            .setApp(App.newBuilder()
                    .setId("AppId")
                    .setBundle("BundleId")
                    .build())
            .setImp(Imp.newBuilder()
                    .setSecure(1)
                    .build())
            .build();

    apiV3Context = new ApiV3Context(apiV3Request);
  }

  @Test
  void getUserAgent() {
    String userAgent = apiV3Context.getUserAgent();
    Assertions.assertEquals("dummyUserAgent", userAgent);
  }

  @Test
  void getPlacementId() {
    String placementId = apiV3Context.getPlacementId();
    Assertions.assertEquals("AppId", placementId);
  }

  @Test
  void getIpv4() {
    String ipV4 = apiV3Context.getIpv4();
    Assertions.assertEquals("ip", ipV4);
  }

  @Test
  void getIpv6() {
    String ipv6 = apiV3Context.getIpv6();
    Assertions.assertEquals("ipv6", ipv6);
  }

  @Test
  void getLatLong() {
    LatLong latLong = apiV3Context.getLatLong();
    Assertions.assertEquals(new LatLong(12.5F, 27.5F, 0), latLong);
  }

  @Test
  void getBundleId() {
    String bundleId = apiV3Context.getBundleId();
    Assertions.assertEquals("BundleId", bundleId);
  }

  @Test
  void checkIfApiNeedsHttps() {
    boolean check = apiV3Context.checkIfApiNeedsHttps();
    assertTrue(check);
  }

  @Test
  void getRequestType() {
    ApiRequestType apiRequestType = apiV3Context.getApiRequestType();
    Assertions.assertEquals(ApiRequestType.API_V3, apiRequestType);
  }
}
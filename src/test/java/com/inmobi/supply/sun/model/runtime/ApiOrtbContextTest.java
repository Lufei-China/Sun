package com.inmobi.supply.sun.model.runtime;

import static org.junit.jupiter.api.Assertions.*;

import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.api.ortb.ApiOrtbRequest;
import cn.inmobi.pb.sun.rr.api.ortb.App;
import cn.inmobi.pb.sun.rr.api.ortb.Device;
import cn.inmobi.pb.sun.rr.api.ortb.Geo;
import cn.inmobi.pb.sun.rr.api.ortb.Imp;
import com.google.protobuf.Api;
import com.inmobi.supply.sun.model.common.LatLong;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
class ApiOrtbContextTest {

  ApiOrtbRequest apiOrtbRequest;

  ApiOrtbContext apiOrtbContext;

  @BeforeEach
  public void init() {
    apiOrtbRequest = ApiOrtbRequest.newBuilder()
            .setId("requestId")
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
                    .setBundle("BundleId")
                    .build())
            .addImp(Imp.newBuilder()
                    .setSecure(1)
                    .setTagid("tagid")
                    .build())
            .build();

    apiOrtbContext = new ApiOrtbContext(apiOrtbRequest);
  }

  @Test
  void getRequestId() {
    String requestId = apiOrtbContext.getRequestId();
    Assertions.assertEquals("requestId", requestId);
  }

  @Test
  void getUserAgent() {
    String userAgent = apiOrtbContext.getUserAgent();
    Assertions.assertEquals("dummyUserAgent", userAgent);
  }

  @Test
  void getPlacementId() {
    String placementId = apiOrtbContext.getPlacementId();
    Assertions.assertEquals("tagid", placementId);
  }

  @Test
  void getIpv4() {
    String ipV4 = apiOrtbContext.getIpv4();
    Assertions.assertEquals("ip", ipV4);
  }

  @Test
  void getIpv6() {
    String ipv6 = apiOrtbContext.getIpv6();
    Assertions.assertEquals("ipv6", ipv6);
  }

  @Test
  void getLatLong() {
    LatLong latLong = apiOrtbContext.getLatLong();
    Assertions.assertEquals(new LatLong(12.5F, 27.5F, 0), latLong);
  }

  @Test
  void getBundleId() {
    String bundleId = apiOrtbContext.getBundleId();
    Assertions.assertEquals("BundleId", bundleId);
  }

  @Test
  void checkIfApiNeedsHttps() {
    boolean check = apiOrtbContext.checkIfApiNeedsHttps();
    assertTrue(check);
  }

  @Test
  void getRequestType() {
    ApiRequestType apiRequestType = apiOrtbContext.getApiRequestType();
    Assertions.assertEquals(ApiRequestType.API_ORTB, apiRequestType);
  }
}
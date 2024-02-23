package com.inmobi.supply.sun.model.runtime;

import cn.inmobi.pb.sun.rr.api.ortb.ApiOrtbRequest;
import cn.inmobi.pb.sun.rr.api.ortb.Geo;
import cn.inmobi.pb.sun.rr.api.ortb.Imp;
import com.inmobi.supply.framework.common.utils.sfidgen.SfIdGen;
import com.inmobi.supply.sun.model.common.LatLong;
import java.util.Random;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Builder
public class ApiOrtbContext extends ApiContext {

  @Getter
  @Setter
  private ApiOrtbRequest apiOrtbRequest;

  public ApiOrtbContext(ApiOrtbRequest ortbRequest) {
    this.apiRequestType = ApiRequestType.API_ORTB;
    this.apiOrtbRequest = ortbRequest;
  }

  @Override
  public String getRequestId() {
    if (StringUtils.isNotBlank(apiOrtbRequest.getId())) {
      return apiOrtbRequest.getId();
    }
    return SfIdGen.nextId() + String.format("%04d", new Random().nextInt(10000));
  }

  @Override
  public String getUserAgent() {
    return apiOrtbRequest.getDevice().getUa();
  }

  @Override
  public String getPlacementId() {
    if (!apiOrtbRequest.getImpList().isEmpty()) {
      Imp imp = apiOrtbRequest.getImp(0);
      if (imp.hasField(imp.getDescriptorForType().findFieldByName("tagid"))) {
        return imp.getTagid();
      } else if (imp.hasExt() &&
              imp.getExt()
                      .hasField(imp.getExt().getDescriptorForType().findFieldByName("placementid"))) {
        return String.valueOf(imp.getExt().getPlacementid());
      }
    }
    return "";
  }

  @Override
  public String getBundleId() {
    return apiOrtbRequest.getApp().getBundle();
  }

  @Override
  public String getIpv4() {
    return apiOrtbRequest.getDevice().getIp();
  }

  @Override
  public String getIpv6() {
    return apiOrtbRequest.getDevice().getIpv6();
  }

  @Override
  public LatLong getLatLong() {
    Geo geo = apiOrtbRequest.getDevice().getGeo();
    return new LatLong(geo.getLat(), geo.getLon(), 0);
  }

  @Override
  public boolean checkIfApiNeedsHttps() {
    return apiOrtbRequest.getImp(0).getSecure() == 1;
  }

  @Override
  public String getDeviceOS() {
    return apiOrtbRequest.getDevice().getOs();
  }

  @Override
  public String getDeviceModel() {
    return apiOrtbRequest.getDevice().getModel();
  }

  @Override
  public String getDeviceMake() {
    return apiOrtbRequest.getDevice().getMake();
  }

  @Override
  public Float getDeviceDensity() {
    return Float.parseFloat(String.valueOf(apiOrtbRequest.getDevice().getPpi()));
  }

  @Override
  public int getDeviceType() {
    return apiOrtbRequest.getDevice().getDevicetype();
  }

  @Override
  public int getDeviceConnectionType() {
    return apiOrtbRequest.getDevice().getConnectiontype();
  }

  @Override
  public String getDeviceCarrier() {
    return apiOrtbRequest.getDevice().getCarrier();
  }

  @Override
  public String getDeviceOsv() {
    return apiOrtbRequest.getDevice().getOsv();
  }

  @Override
  public String getDeviceUA() {
    return apiOrtbRequest.getDevice().getUa();
  }

  @Override
  public Integer getDeviceOrientation() {
    return null;
  }

  @Override
  public String getDeviceUpdateMark() {
    return null;
  }

  @Override
  public String getDeviceBootmark() {
    return null;
  }


}

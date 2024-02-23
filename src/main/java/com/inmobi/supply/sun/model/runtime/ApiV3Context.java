package com.inmobi.supply.sun.model.runtime;

import cn.inmobi.pb.sun.rr.api.ApiV3Request;
import cn.inmobi.pb.sun.rr.api.Device;
import com.inmobi.supply.adapterlibrary.request.APIV3Request;
import com.inmobi.supply.framework.common.utils.sfidgen.SfIdGen;
import com.inmobi.supply.sun.model.common.LatLong;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

@Builder
public class ApiV3Context extends ApiContext {

  public ApiV3Context(ApiV3Request apiV3Request) {
    this.apiRequestType = ApiRequestType.API_V3;
    this.apiV3Request = apiV3Request;
  }

  @Getter
  @Setter
  private ApiV3Request apiV3Request;

  @Override
  public String getRequestId() {
    return SfIdGen.nextId() + String.format("%04d", new Random().nextInt(10000));
  }

  @Override
  public String getUserAgent() {
    return apiV3Request.getDevice().getUa();
  }

  @Override
  public String getPlacementId() {
    return apiV3Request.getApp().getId();
  }

  @Override
  public String getIpv4() {
    return apiV3Request.getDevice().getIp();
  }

  @Override
  public String getIpv6() {
    return apiV3Request.getDevice().getIpv6();
  }

  @Override
  public LatLong getLatLong() {
    Device.Geo geo = apiV3Request.getDevice().getGeo();
    return new LatLong(geo.getLat(), geo.getLon(), geo.getAccu());
  }

  @Override
  public String getBundleId() {
    return apiV3Request.getApp().getBundle();
  }

  @Override
  public boolean checkIfApiNeedsHttps() {
    return apiV3Request.getImp().getSecure() == 1;
  }

  @Override
  public String getDeviceOS() {
    return apiV3Request.getDevice().getOs();
  }

  @Override
  public String getDeviceModel() {
    return apiV3Request.getDevice().getModel();
  }

  @Override
  public String getDeviceMake() {
    return apiV3Request.getDevice().getMake();
  }

  @Override
  public Float getDeviceDensity() {
    return Float.parseFloat(String.valueOf(apiV3Request.getDevice().getPpi()));
  }

  @Override
  public int getDeviceType() {
    return apiV3Request.getDevice().getType();
  }

  @Override
  public int getDeviceConnectionType() {
    return apiV3Request.getDevice().getConnectiontype();
  }

  @Override
  public String getDeviceCarrier() {
    return apiV3Request.getDevice().getCarrier();
  }

  @Override
  public String getDeviceOsv() {
    return apiV3Request.getDevice().getOsv();
  }

  @Override
  public String getDeviceUA() {
    return apiV3Request.getDevice().getUa();
  }

  @Override
  public Integer getDeviceOrientation() {
    if (apiV3Request.getDevice().getExt() != null) {
      return apiV3Request.getDevice().getExt().getOrientation();
    }
    return null;
  }

  @Override
  public String getDeviceUpdateMark() {
    return apiV3Request.getDevice().getUpdateMark();
  }

  @Override
  public String getDeviceBootmark() {
    return apiV3Request.getDevice().getBootMark();
  }
}

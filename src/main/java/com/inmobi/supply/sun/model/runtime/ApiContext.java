package com.inmobi.supply.sun.model.runtime;

import com.inmobi.supply.sun.model.common.LatLong;
import lombok.Getter;
import lombok.Setter;

public abstract class ApiContext {
  @Getter
  @Setter
  protected ApiRequestType apiRequestType;

  public abstract String getRequestId();

  public abstract String getUserAgent();

  public abstract String getPlacementId();

  public abstract String getBundleId();

  public abstract String getIpv4();

  public abstract String getIpv6();

  public abstract LatLong getLatLong();

  public abstract boolean checkIfApiNeedsHttps();

  public abstract String getDeviceOS();

  public abstract String getDeviceModel();

  public abstract String getDeviceMake();

  public abstract Float getDeviceDensity();

  public abstract int getDeviceType();

  public abstract int getDeviceConnectionType();

  public abstract String getDeviceCarrier();

  public abstract String getDeviceOsv();

  public abstract String getDeviceUA();

  public abstract Integer getDeviceOrientation();

  public abstract String getDeviceUpdateMark();

  public abstract String getDeviceBootmark();
}

package com.inmobi.supply.sun.service.preProc.normalizer.module.strategy;


import cn.inmobi.pb.sun.rr.base.BaseRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.constant.RequestFieldConstant;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.SunException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.utils.LogUtils;
import com.stars.bid.pb.*;
import lombok.Builder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.inmobi.supply.sun.constant.ProtocolConstant.*;

/**
 * @Description NormalizerRequestV4  支持PB  JSON 格式
 * @Date 2022/5/7 16:54
 * @Author william.wang
 */
@Component
@Builder
public class NormalizerRequestV4Worker implements NormalizerRequestWorker {

    @Override
    public void parseRequest(RunTimeContext runTimeContext) throws SunException {
        MediaType mediaType = runTimeContext.getServerRequest().headers().contentType().orElse(MediaType.APPLICATION_JSON);
        //确定接入协议
        ProtocolConstant protocol = getProtocolBuyContentType(mediaType);
        runTimeContext.setContentType(protocol);
        BaseRequest.Builder baseRequestBuilder = BaseRequest.newBuilder();
        BidRequest bidRequest;
        if (protocol == JSON) {
            String requestBody = runTimeContext.getRequestBody();
            LogUtils.logInfo(runTimeContext.getRequestId(),"apiV4Request Info jsonBody {}",requestBody);
            BidRequest.Builder bidRequestBuilder = BidRequest.newBuilder();
            try {
                JsonFormat.parser().ignoringUnknownFields().merge(requestBody, bidRequestBuilder);
                LogUtils.logInfo(runTimeContext.getRequestId(),"apiV4Request json info {}",bidRequestBuilder);
                bidRequest = bidRequestBuilder.build();
            } catch (Exception e) {
                LogUtils.logError(runTimeContext.getRequestId(), "parseAPIV4Request json error! error:{}", e);
                throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
            }
        } else if (protocol == PROTO_BUF) {
            // protobuf格式的请求
            try {
                  bidRequest = BidRequest.parseFrom(runTimeContext.getInputStream());
            } catch (Exception e) {
                LogUtils.logError(runTimeContext.getRequestId(), "parseAPIV4Request proto error {}", e);
                throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
            }
        } else {
            LogUtils.logInfo(runTimeContext.getRequestId(), "parseAPIV4Request Version error {}", mediaType.getType());
            throw new SunException(TermCode.INVALID_REQUEST_TYPE, runTimeContext);
        }

        try {
            baseRequestBuilder.setRequestId(bidRequest.getRequestId());
            parseImp(baseRequestBuilder.getImpBuilder(), bidRequest);
            parseApp(baseRequestBuilder.getAppBuilder(), bidRequest);
            parseSite(baseRequestBuilder.getSiteBuilder(), bidRequest);
            parseDevice(baseRequestBuilder.getDeviceBuilder(), bidRequest);
            parseUser(baseRequestBuilder.getUserBuilder(), bidRequest);
            parseExt(baseRequestBuilder.getExtBuilder(), bidRequest);
            baseRequestBuilder.setTmax(bidRequest.getTmax());
            runTimeContext.setBaseRequest(baseRequestBuilder.build());
            LogUtils.logInfo(runTimeContext.getRequestId(), "NormalizerRequestV4Worker baseRequest {}", runTimeContext.getBaseRequest());
        }catch (Exception e){
            LogUtils.logError(runTimeContext.getRequestId(), "NormalizerRequestV4Worker error {}", e);
            throw new SunException(TermCode.API_PARAM_INVALID, runTimeContext);
        }
    }

    private void parseSite(cn.inmobi.pb.sun.rr.base.Site.Builder siteBuilder, BidRequest bidRequest) throws InvalidProtocolBufferException {
        Site site = bidRequest.getSite();
        siteBuilder.setAdType(site.getAdType());
        for (CreativeSpecs creativeSpecs : site.getCreativeSpecsList()) {
            final cn.inmobi.pb.sun.rr.base.Site.CreativeSpecs.Builder builder = cn.inmobi.pb.sun.rr.base.Site.CreativeSpecs.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(JsonFormat.printer().print(creativeSpecs), builder);
            siteBuilder.addCreativeSpecs(builder.build());
        }
    }

    private void parseExt(cn.inmobi.pb.sun.rr.base.RequestExtension.Builder extBuilder, BidRequest bidRequest) {
        Site site = bidRequest.getSite();
        List<CreativeSpecs> creativeSpecsList = site.getCreativeSpecsList();
        List<Integer> creativeSupportList = creativeSpecsList.stream().map(creativeSpecs -> creativeSpecs.getSupportCreativeType().getNumber()).collect(Collectors.toList());
        List<Integer> supportInteractionTypeValueList = new ArrayList<>(site.getSupportInteractionTypeValueList());
        if (CollectionUtils.isEmpty(supportInteractionTypeValueList)) {
            supportInteractionTypeValueList = Arrays.asList(1, 2, 3, 4);
        }
        extBuilder.addAllInteractSupport(supportInteractionTypeValueList)
                .addAllCreativeSupport(creativeSupportList)
                .setSupport302(site.getSupport302());
    }

    private void parseImp(cn.inmobi.pb.sun.rr.base.Imp.Builder impBuilder, BidRequest bidRequest) {
        com.stars.bid.pb.Imp imp = bidRequest.getImp();
        if (imp.getPmpCount() > 0) {
            List<com.stars.bid.pb.Imp.Pmp> pmpList = imp.getPmpList();
            List<cn.inmobi.pb.sun.rr.base.Imp.Deal> dealsList = new ArrayList<>();
            for (com.stars.bid.pb.Imp.Pmp pmp : pmpList) {
                cn.inmobi.pb.sun.rr.base.Imp.Deal.Builder dealBuilder = cn.inmobi.pb.sun.rr.base.Imp.Deal.newBuilder();
                dealBuilder.setId(pmp.getDealId())
                        .setBidfloor(pmp.getDealPrice());
                if (imp.getSettleType().equals(SettleType.PD)) {
                    dealBuilder.setAt(1);
                }
                if (imp.getSettleType().equals(SettleType.PDB)) {
                    dealBuilder.setAt(2);
                }
                dealsList.add(dealBuilder.build());
            }
            cn.inmobi.pb.sun.rr.base.Imp.PMP.Builder pmpBuilder = impBuilder.getPmpBuilder();
            pmpBuilder.addAllDeals(dealsList);
        } else {
            impBuilder.setPriceFloor(imp.getFloorPrice());
        }
        impBuilder.setSecure(imp.getSecure());
    }


    private void parseApp(cn.inmobi.pb.sun.rr.base.App.Builder appBuilder, BidRequest bidRequest) {
        App app = bidRequest.getApp();
        Site site = bidRequest.getSite();
        appBuilder.setId(site.getId())
                .setName(app.getAppName())
                .setBundle(app.getBundleId())
                .setDomain(app.getDomain())
                .setStoreurl(app.getStoreUrl())
                .setVer(app.getVersion());
    }

    private void parseDevice(cn.inmobi.pb.sun.rr.base.Device.Builder deviceBuilder, BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        deviceBuilder.setUa(device.getUa())
                .setIp(device.getIpv4())
                .setIpv6(device.getIpv6())
                .setIfa(device.getIdfa())
                .setIdfv(device.getIdfv())
                .setIdfaMd5(device.getIdfaMd5())
                .setIem(device.getImei())
                .setMd5Imei(device.getImeiMd5())
                .setOaid(device.getOaid())
                .setOaidMd5(device.getOaidMd5())
                .setAndroidId(device.getAndroidId())
                .setUm5(device.getAndroidIdMd5())
                .setW(device.getW())
                .setH(device.getH())
                .setOsv(device.getOsv())
                .setMake(device.getMake())
                .setModel(device.getModel())
                .setCpuFrequency(device.getCpuFrequency())
                .setCpuNumber(device.getCpuNumber())
                .setBatteryPower(device.getBatteryPower())
                .setBatteryStatus(device.getBatteryStatus())
                .setCaid(device.getCaid())
                .setCaidVersion(device.getCaidVersion())
                .setPreCaid(device.getPreCaid())
                .setPreCaidVersion(device.getPreCaidVersion())
                .setLanguage(device.getLanguage())
                .setPhoneName(device.getPhoneNameMd5())
                .setMemorySize(device.getMemorySize())
                .setDiskSize(device.getDiskSize())
                .setModel(device.getModel())
                .setTimeZone(device.getTimeZone());

        String mac = device.getMac();
        String macMd5 = device.getMacMd5();
        if (StringUtils.isNotBlank(mac)) {
            deviceBuilder.setMac(mac);
        }
        if (StringUtils.isBlank(macMd5) && StringUtils.isNotBlank(mac)) {
            deviceBuilder.setMacMd5(DigestUtils.md5Hex(mac));
        } else if(StringUtils.isNotBlank(macMd5)){
            deviceBuilder.setMacMd5(macMd5);
        }
        if (DeviceType.PHONE.equals(device.getDeviceType())) {
            deviceBuilder.setType(RequestFieldConstant.DEVICE_TYPE_PHONE);
        } else if (DeviceType.PAD.equals(device.getDeviceType())) {
            deviceBuilder.setType(RequestFieldConstant.DEVICE_TYPE_PAD);
        } else {
            deviceBuilder.setType(RequestFieldConstant.DEVICE_TYPE_KNOWN);
        }

        if (OsType.ANDROID.equals(device.getOs())) {
            deviceBuilder.setOs(RequestFieldConstant.OS_ANDROID);
        }
        if (OsType.IOS.equals(device.getOs())) {
            deviceBuilder.setOs(RequestFieldConstant.OS_IOS);
        }
        deviceBuilder.setBootMark(device.getBootMark());
        deviceBuilder.setUpdateMark(device.getUpdateMark());
        deviceBuilder.setBootTimeSecond(device.getBootSecond());
        deviceBuilder.setOsUpdateTimeSecond(device.getUpdateSecond());

        if (Carrier.MOBILE.equals(device.getCarrier())) {
            deviceBuilder.setCarrier(RequestFieldConstant.CARRIER_MOBILE);
        }
        if (Carrier.UNICOM.equals(device.getCarrier())) {
            deviceBuilder.setCarrier(RequestFieldConstant.CARRIER_UNICOM);
        }
        if (Carrier.TELECOM.equals(device.getCarrier())) {
            deviceBuilder.setCarrier(RequestFieldConstant.CARRIER_TELECOM);
        }
        if (ConnType.CONN_3G.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_3G);
        }
        if (ConnType.CONN_4G.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_4G);
        }
        if (ConnType.CONN_5G.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_5G);
        }
        if (ConnType.WIFI.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_WIFI);
        }
        if (ConnType.OTHER.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_UNKNOWN);
        }
        if (ConnType.UNRECOGNIZED.equals(device.getConnType())) {
            deviceBuilder.setConnectiontype(RequestFieldConstant.CONNECTION_TYPE_UNRECOGNIZED);
        }

        if(StringUtils.isNotBlank(device.getHmsVerCode())){
            deviceBuilder.setHmsVerCode(device.getHmsVerCode());
        }
        if(StringUtils.isNotBlank(device.getRomVersion())){
            deviceBuilder.setRomVersion(device.getRomVersion());
        }
        if(StringUtils.isNotBlank(device.getAppStoreVersion())){
            deviceBuilder.setAppStoreVersion(device.getAppStoreVersion());
        }
        deviceBuilder.setDpi(device.getDpi());
        Geo geo = device.getGeo();
        cn.inmobi.pb.sun.rr.base.Device.Geo.Builder geoBuilder = deviceBuilder.getGeoBuilder();
        geoBuilder.setLon(geo.getLon())
                .setLat(geo.getLat())
                .setCountry(device.getCountryCode())
                .setType(geo.getSource());
    }

    private void parseUser(cn.inmobi.pb.sun.rr.base.User.Builder userBuilder, BidRequest bidRequest) {
        final User user = bidRequest.getUser();
        if (RequestFieldConstant.GENDER_MALE.equals(user.getGender())) {
            userBuilder.setGender(RequestFieldConstant.GENDER_MALE_STR);
        }
        if (RequestFieldConstant.GENDER_FEMALE.equals(user.getGender())) {
            userBuilder.setGender(RequestFieldConstant.GENDER_FEMALE_STR);
        }
        AppList appList = user.getAppList();
        if (appList.getBase64Flag()) {
            String content = appList.getContent();
            String contentOrigin = new String(Base64.getDecoder().decode(content));
            userBuilder.setAppList(contentOrigin);
        } else {
            userBuilder.setAppList(appList.getContent());
        }
        userBuilder.setKeywords(String.join(SymbolString.COMMA, user.getKeywordsList()))
                .setAge(user.getAge())
                .addAllTagId(user.getTagIdList());
    }


    @Override
    public List<RequestType> getRequestTypeList() {
        return Collections.singletonList(RequestType.API_4_0);
    }
}
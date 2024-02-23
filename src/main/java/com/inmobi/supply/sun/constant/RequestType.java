package com.inmobi.supply.sun.constant;

import cn.inmobi.pb.api.Version;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;


/**
 * adType枚举常量
 *
 * @author gavin.xu
 */
@Getter
@Slf4j
@SuppressWarnings("unchecked")
public enum RequestType {
    IN_VALID(null, false, false),
    SDK(null, true, true),

    API_2_0(buildVersion(2, 0), false, false),

    API_2_1(buildVersion(2, 1), false, false),

    API_2_2(buildVersion(2, 2), false, false),

    API_VAST_3_0(buildVersion(3, 0), false, false),

    API_3_0(buildVersion(3, 0), false, false),

    API_3_1(buildVersion(3, 1), false, false),

    API_3_2(buildVersion(3, 2), false, true),

    API_3_3(buildVersion(3, 3), false, true),

    API_3_3_1(buildVersion(3, 3,1), false, true, true),

    API_3_4(buildVersion(3, 4), false, true, true),

    API_3_4_1(buildVersion(3, 4, 1), false, true, true),

    API_4_0(buildVersion(4, 0), false, true, true),

    CUSTOM_API(buildVersion(3, 3), false, true),

    API_ORTB_1_0(buildVersion(1, 0), false, false);

    RequestType(@Nullable Version version, boolean isSdk, boolean supportJsonVideo) {
        this.version = version;
        this.isSdk = isSdk;
        this.supportJsonVideo = supportJsonVideo;
        this.above33 = false;
    }

    RequestType(@Nullable Version version, boolean isSdk, boolean supportJsonVideo, boolean above33) {
        this.version = version;
        this.isSdk = isSdk;
        this.supportJsonVideo = supportJsonVideo;
        this.above33 = above33;
    }

    private final Version version;

    private boolean isSdk;

    private boolean supportJsonVideo;

    private boolean above33;

    private static Version buildVersion(int major, int minor) {
        return Version.newBuilder()
                .setMajor(major)
                .setMinor(minor)
                .build();
    }

    private static Version buildVersion(int major, int minor, int micro) {
        return Version.newBuilder()
                .setMajor(major)
                .setMinor(minor)
                .setMicro(micro)
                .build();
    }

    private static boolean above34(RequestType requestType){
        int major = requestType.getVersion().getMajor();
        int minor = requestType.getVersion().getMinor();
        return major > 3 || (major == 3 && minor >= 4);
    }
}
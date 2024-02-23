package com.inmobi.supply.sun.constant;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * sun工程需要用到的常量
 *
 * @author remi.li
 */
public class RuntimeConstant {
    public static final int RUNTIME_CTX_KEY = 0;
    public static final String FIRST_PROC_CTX_KEY = "byte[]";

    public static final int BIGDECIMAL_SCALE = 4;

    public static final BigDecimal ONE_POINT_ZERO_SIX = BigDecimal.valueOf(1.06);

    public static final Charset UTF8 = Charset.forName("UTF-8");

    //empty response constant
    public static final byte[] EMPTY_BODY = "".getBytes();

    //content type constant
    public static final MediaType JSON_CONTENT_UTF8_TYPE = MediaType.APPLICATION_JSON_UTF8;
    public static final MediaType OCTET_STREAM_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    public static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    public static final int DEFAULT_ICON_WIDTH = 100;
    public static final int DEFAULT_ICON_HEIGHT = 100;
    public static final double DEFAULT_ICON_ASPECT_RATIO = 1.0D;

    //endpoint constant
    public static final String SDK_ENDPOINT = "union.w.inmobi.cn";
    public static final String SDK_ENDPOINT_JS = "js.w.inmobi.com";
    public static final String SDK_ENDPOINT_W = "w.inmobi.com";
    public static final String SDK_ENDPOINT_MK = "w.mkhoj.com";
    public static final String SDK_ENDPOINT_VAST = "vast.w.inmobi.com";
    public static final String SDK_ENDPOINT_C2S = "c2s.w.inmobi.com";

    public static final String API_ENDPOINT = "union.api.w.inmobi.cn";
    public static final String API_UAT_ENDPOINT = "uat-api-w.inmobi.cn";
    public static final String MOPUB_API_ENDPOINT = "mp.api.w.inmobi.com";
    public static final String ZHIHU_API_ENDPOINT = "zhihu.w.inmobi.com";
    public static final String DEFAULT_RAW_PATH = "###";

    //enrich getui
    public static final String GETUI_AUTH_EXPIRED = "401";
    public static final String EMPTY_STR = "empty";

    // sandbox request
    public static final String SANDBOX_REQUEST_ID = "sandboxrequestid123";
    public static final String SANDBOX_OPEN_RTB_REQUEST_ID = "sandboxopenrtbrequestid123";
    public static final String SANDBOX_OPEN_RTB_BANNER_REQUEST_ID = "sandboxopenrtbbannerrequestid123";
    public static final String SANDBOX_TOUTIAO_REQUEST_ID = "sandboxtoutiaorequestid";
    public static final String SANDBOX_VEDIO_REQUEST_ID = "sandboxjsonvideorequestid123";

    public static final BigDecimal FEN_TO_YUAN = BigDecimal.valueOf(100);

    public static final String AUDIT_ALL_STR = "AUDIT_ALL";

    public static final String IOS_ZERO_IDFA = "00000000-0000-0000-0000-000000000000";
    public static final String IDFA_ZERO_MD5 = "9F89C84A559F573636A47FF8DAED0D33";
    public static final String INMOBI_NETWORK_ID = "s4f48nyfvz.skadnetwork";

    public static final String SERVICE_PLUTO = "pluto";

    public static final List<String> MUNICIPALITY_DIRECTLY = Arrays.asList("北京", "上海", "天津", "重庆");

    public static final Integer PLACEMENT_AUDIT_STYLE_AFTER_PUTTING = 1;
    public static final Integer PLACEMENT_AUDIT_STYLE_BEFORE_PUTTING = 2;


}
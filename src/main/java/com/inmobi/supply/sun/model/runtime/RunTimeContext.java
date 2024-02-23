package com.inmobi.supply.sun.model.runtime;

import cn.inmobi.pb.EventRR;
import cn.inmobi.pb.api.*;
import cn.inmobi.pb.earth.entity.EarthResponse;
import cn.inmobi.pb.sun.rr.OriginalRequest;
import cn.inmobi.pb.sun.rr.base.BaseRequest;
import cn.inmobi.pb.sun.winnotice.WinNotice;
import com.google.common.collect.Lists;
import com.inmobi.supply.adapterlibrary.entity.RequestAdapterResponse;
import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.component.model.redis.AppModel;
import com.inmobi.supply.component.model.redis.TemplateModel;
import com.inmobi.supply.component.model.redis.placement.PlacementModel;
import com.inmobi.supply.component.model.redis.placement.TestDevice;
import com.inmobi.supply.sun.adPool.IAdPool;
import com.inmobi.supply.sun.constant.ProtocolConstant;
import com.inmobi.supply.sun.utils.SecureSession;
import com.inmobi.supply.sun.constant.ChargeAndPay;
import com.inmobi.supply.sun.constant.RequestType;
import com.inmobi.supply.sun.constant.UaSourceType;
import com.inmobi.supply.sun.model.common.Device;
import com.inmobi.supply.sun.model.common.LatLong;
import com.inmobi.supply.sun.model.response.AsyncServletResponse;
import com.inmobi.supply.sun.model.response.BaseResponse;
import com.inmobi.supply.sun.service.postProc.format.module.template.IEnvelop;
import com.inmobi.supply.sun.utils.IdGenerator;
import lombok.*;
import org.slf4j.Marker;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.util.function.Tuple2;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;

/**
 * 全局上下文
 *
 * @author remi.li
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
public class RunTimeContext {
    /**
     * 是否执行了反作弊模块
     */
    private boolean executeAntiFraud;
    /**
     * 反作弊Response
     */
    private EarthResponse earthResponse;
    /**
     * 错误信息
     */
    /**
     * 异常记录
     */
    private boolean error;
    private Throwable throwable;
    /**
     * 请求id，uuid生成
     */
    private String requestId;

    private String sdkVersion;

    private ApiContext apiContext;

    /**
     * ServerHttpRequest
     */
    @Nonnull
    private ServerRequest serverRequest;

    private String requestBody;

    private DataBuffer dataBuffer;

    private byte[] inputStream;

    /**
     * ServerHttpResponse
     */
    @Nonnull
    private ServerHttpResponse serverHttpResponse;

    /**
     * AsyncServletResponse
     */
    private AsyncServletResponse asyncServletResponse;

    /**
     * 是否跟踪代码，由header确定
     */
    private boolean isTrace;
    /**
     * 追踪代码实体
     */
    private Trace traced;
    /**
     * GUID
     */
    private IdGenerator.GUID guid;

    /**
     * 请求类型
     */
    private RequestType requestType;

    /**
     * user agent
     */
    private String userAgent;
    private boolean uAPresent;
    private UaSourceType uaSourceType;

    /**
     * 请求域名
     */
    @Nonnull
    private String host;

    /**
     * 是否是https
     */
    private boolean isHttps;

    /**
     * 是否需要https返回
     */
    private boolean isSecure;

    /**
     * rwaPath
     */
    @Nonnull
    private String rawPath;

    private String userIpV4;

    private String userIpV6;

    @NonNull
    private String serverIp;


    /**
     * 是否是预加载广告请求
     */
    private boolean isPreloadRequest;

    /**
     * 广告位id
     */
    private Long placementId;

    /**
     * 广告位
     */
    private PlacementModel placementModel;

    /**
     * app info
     */
    private AppModel appModel;

    /**
     * adType for placement
     */
    private AdType adType;

    /**
     * 网络类型
     */
    private ConnectionType connectionType;


    private RevenueShareType revenueShareType;

    /**
     * sdk请求参数
     */
    private Map<String, String> sdkRequest = new HashMap<>();


    private Map<String, String> uidMap = new HashMap<>();


    /**
     * api33请求参数
     */
    private BaseRequest baseRequest;

    /**
     * headers
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * 异常记录状态码
     */
    private List<TermCode> termCodes = new ArrayList<>();


    /**
     * device信息
     */
    private Device device;

    /**
     * geo信息
     */
    private Geo geo;

    /**
     * 用户所处经纬度信息
     */
    private LatLong latLong;

    /**
     *
     * 手机设备语言
     */
    /*public Language language;*/

    /**
     * 渠道bid
     * Map<FloorEntityType, Long>
     */
    private Map<FloorEntityType, Long> bidding = new HashMap<>();


    /**
     * 渠道底价
     * Map<FloorEntityType, RevShare>
     */
    private Map<FloorEntityType, Long> floorEntityMap = new HashMap<>();

    private Map<FloorEntityType, BigDecimal> cosRevShare = new HashMap<>();

    /**
     * 不同渠道设置不同的PE.优先级最高，默认取广告位统一设计的PE
     */
    private Map<FloorEntityType, Long> cosmosRevSharePe = new HashMap<>();

    /**
     * 真实pe
     */
    private long realRe;


    /**
     * 最终发送请求的adPool列表
     */
    private List<IAdPool> adPoolList = new ArrayList<>();

    private List<Long> includeAdvertiserIds = new ArrayList<>();

    private List<Long> excludeAdvertiserIds = new ArrayList<>();

    private List<String> includeChannelIds = new ArrayList<>();

    private List<String> excludeChannelIds = new ArrayList<>();

    private Map<String, Integer> dspChannelForwardScare = new HashMap<>();


    /**
     * 请求adPool的bidRequest
     */
    private BidRequest bidRequest;

    /**
     * adPool origin bidResponse list（有真实填充的bidResponse）
     */
    private List<BidResponse> originBidResponseList = new ArrayList<>();

    /**
     * adPool valid bidResponse list（经过校验的bidResponse）
     */
    private List<BidResponse> validBidResponseList = new ArrayList<>();

    /**
     * adPool all bidResponse list (有无填充都记录的bidResponse，可以废弃)
     */
    //private List<BidResponse> allBidResponseList = new ArrayList<>();

    /**
     * 低于底价的bidResponse集合
     */
    private List<BidResponse> lowerThanFloorBidResponseList = new ArrayList<>();

    /**
     * win bidResponse
     */
    private BidResponse winBidResponse;

    /**
     * win Ad
     */
    private Ad winAd;

    /**
     * second bidResponse
     */
    @Nullable
    private BidResponse secondBidResponse;

    /**
     * lose bidResponses
     */
    private List<BidResponse> loseBidResponses;

    /**
     * final winAd template
     */
    private TemplateModel finalTemplate;

    /**
     * charge and pay map
     */
    private Map<ChargeAndPay, Long> chargeAndPayMap = new HashMap<>();

    /**
     * adv settle price
     */
    private Long advSettlePrice;

    private String newPeDealId;

    /**
     * total discount
     * 12项折扣
     */
    private Float totalDiscount;

    /**
     * 4项折扣
     */
    private Double adpoolDeduction;

    /**
     * baconUrl/tracker etc
     */
    @Nonnull
    private AdRunTimeInfo adRunTimeInfo;

    /**
     * final envelop
     */
    @Nonnull
    private IEnvelop envelop;

    /**
     * EventRR
     */
    private EventRR eventRR;

    /*private ApiV31Response apiV31Response;

    private ApiV33Response apiV33Response;

    private ApiOrtbResponse apiOrtbResponse;*/
    private FormatterModel formatterModel;

    private BaseResponse baseResponse;

    private Map<IAdPool, TermCode> adpoolTermReason = new HashMap<>();

    private Long firstPrice;

    private Long secondPrice;

    private List<Long> virtualBids;

    private List<WinNotice> winNoticeList = new ArrayList<>();

    private Map<String, List<WinNotice>> lostNoticeList = new HashMap<>();

    private List<CreativeType> placementCreativeTypes = new ArrayList<>();

    /**
     * traceMarker
     */
    private Marker traceMarker;

    private RequestAdapterResponse requestAdapterResponse;

    private RSAPrivateKey rsaPrivateKey;
    private SecureSession secureSession;
    private boolean secureMessageFlag;
    private GoaRtt goaRtt = new GoaRtt();

    private String csids;

    private Tuple2<Integer, String> grade;
    String requestBundleId;

    //TODO 不规范！需要矫正
    //Map是规范的
    List<String> experimentFlags = new ArrayList<>();
    Map<String, Object> experimentFlagsMap = new HashMap();

    boolean isDynamicFloorTraffic;

    private String creativeAuditId;
    // 删除无用
    private Map<String, Object> extParam = new HashMap<>(); //扩展字段，可用来ABTest

    private Boolean testTraffic;
    private List<TestDevice> testDeviceList = new ArrayList<>();

    private List<String> bidSharingWinNotices = new ArrayList<>();

    private String caid;
    private String caidVersion;
    private String preCaid;
    private String preCaidVersion;

    private Integer tag;

    private int lat = 0; //限制广告追踪字段  0：未确定，开发者尚未请求用户许可 1：受限制，用户可以退出 IDFA 在设备级别的所有应用程序 2：被拒绝，用户选择退出IDFA 3：授权，可以继续获取 IDFA
    //是否需要反作弊报告给第三方公司URI,如以后需要扩展则扩展为数组
    private boolean needTraceApi;
    //当出现需要反作弊的情况进行透传的参数
    private List<Integer> nappExperiments = Lists.newArrayList();
    // 是否属于新手池
    private boolean isPool;

    private Map<String, Long> biddingAfterFilter = new HashMap<>();

    private Map<String, Integer> dspMap = new HashMap<>();

    private long baiduBrice;
    private boolean hasCpc;
    private List<String> baiduTagIdList = new ArrayList<>();
    private List<Long> besUserCategory = new ArrayList<>();
    private boolean hasBidPrice;
    private List<Integer> acceptedInteractionTypeList = new ArrayList<>();
    private String reqId;
    private long startTime;

    /**原始请求参数 begin*/
    private OriginalRequest.Builder originalRequestBuilder = OriginalRequest.newBuilder();
    private String inmobiVendor;
    private String originalModel;
    private String originalVendor;
    private String inmobiModel;
    private String originalConnType;
    private String inmobiConnType;
    private int inmobiWidth;
    private int inmobiRealHeight;
    private double inmobiLatitude;
    private double inmobiLongitude;
    /**原始请求参数 end*/

    private List<String> advTrackers=new ArrayList<>();

    private int  accountType = 1; //1 默认媒体流量  2 adx 流量
    private Integer originalWidth;
    private Integer originalRealHeight;
    private String allCsids;

    private Long ortbBidFloor; //ortb 低价
    private Integer tmax; //超时时间
    private String channel;
    /**
     * 钓鱼广告
     */
    private boolean mixAd;
    /**
     * 策略阈值
     */
    private String actionThreshold;
    /**
     * 策略类型 0 无动作 1 摇一摇 2 滑动
     */
    private Integer actionType = 0;

    /**
     * pd 模式生效dealId 默认 空串
     */
    private String dealId = "";

    /**
     * 广告主净出价，一价
     */
    private Long advBidPrice;

    /**
     * 0 normal， 1 marketPlace demand端使用
     */
    private Integer switchFlag = 0;
    /**
     * 该位置rcpm是否低于 exchange rcost
     */
    private boolean belowExchangeRcost;
    /**
     * 该位置rcpm是否低于 demand rcost
     */
    private boolean belowDemandRcost;

    /**
     * ABTest 参数
     */
    private Map<String,String> abTestTags = new HashMap<>();

    private Integer saturnProcessTime = 0;//请求saturn 耗时

    private ProtocolConstant contentType;// 请求头 类型 json  pb

    private String appMixId;//广告位 聚合id adx
    private String appMixName;//广告位 聚合id 名称
    private OsType osType;
    private String attributeInfo; //bes 支持投放的竞价方式 0 cpm; 1 cpc; 4 oCpm

    /**
     * app版本
     */
    private Integer appVersionMajor;

    /**
     * app版本
     */
    private Integer appVersionMinor;

    /**
     * app版本
     */
    private Integer appVersionMicro;
}
package com.inmobi.supply.sun.constant;

import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.inmobi.supply.sun.constant.SaturnDsp.*;

/**
 * Action constants
 *
 * @author Leo.Liao
 */
public class AuctionParam {
    public final static BigDecimal DEFAULT_MIN_REV_SHARE = new BigDecimal("0.65");
    public final static BigDecimal DEFAULT_MAX_REV_SHARE = new BigDecimal("0.95");
    public final static Integer DEFAULT_MARGIN_BAND_COUNT = 3;
    public final static BigDecimal DEFAULT_REV_SHARE = new BigDecimal("57");
    public final static BigDecimal DEFAULT_PD_REV_SHARE = new BigDecimal("80");
    public final static BigDecimal DEFAULT_M_REV_SHARE = new BigDecimal("120");
    public final static BigDecimal DEFAULT_NOISE_LEVEL = new BigDecimal(3);
    public final static BigDecimal DEFAULT_MAX_DELTA = new BigDecimal("0.25");

    public final static Long FEN = Long.valueOf("1");

    public final static BigDecimal FIFTY = BigDecimal.valueOf(50);
    public final static BigDecimal THREE = BigDecimal.valueOf(3);
    public final static BigDecimal TWO = BigDecimal.valueOf(2);
    public final static BigDecimal ONE = BigDecimal.valueOf(1);
    public final static BigDecimal ZERO = BigDecimal.valueOf(0);
    public final static BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    public final static BigDecimal ONE_HUNDRED_AND_TEN_AND_ONE = BigDecimal.valueOf(111);
    public final static BigDecimal NEGATIVE_ONE_HUNDRED = BigDecimal.valueOf(-100);
    public final static BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);
    public final static BigDecimal ONE_POINT_ONE = BigDecimal.valueOf(1.1);
    public final static BigDecimal POINT_NINE_FIVE = BigDecimal.valueOf(0.95);


    public final static List<String> CHANNEL_WHITE_LIST = Arrays.asList(PDD_CHANNEL_ID, GDT_CHANNEL_ID, GDT_CUSTOM_CHANNEL_ID, NETEASE_CHANNEL_ID, JD_CUSTOM_CHANNEL_ID, JD_FIN_CHANNEL_ID);

    public static final DateTimeFormatter apMarginBandDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    public final static Map<Integer, BigDecimal> DEFAULT_DELTA_HOUR_OF_DAY =
            new HashMap<>();

    static {
        DEFAULT_DELTA_HOUR_OF_DAY.put(0, new BigDecimal("-8.918396"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(1, new BigDecimal("-23.866901"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(2, new BigDecimal("-28.260499"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(3, new BigDecimal("-30.725188"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(4, new BigDecimal("-12.953528"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(5, new BigDecimal("-12.277762"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(6, new BigDecimal("-31.129023"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(7, new BigDecimal("-7.073277"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(8, new BigDecimal("12.325312"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(9, new BigDecimal("43.332975"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(10, new BigDecimal("76.949108"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(11, new BigDecimal("93.455556"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(12, new BigDecimal("49.540752"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(13, new BigDecimal("30.102593"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(14, new BigDecimal("19.414715"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(15, new BigDecimal("5.545702"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(16, new BigDecimal("2.753327"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(17, new BigDecimal("-17.389339"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(18, new BigDecimal("-11.224911"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(19, new BigDecimal("-13.012893"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(20, new BigDecimal("-17.494989"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(21, new BigDecimal("-21.201397"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(22, new BigDecimal("-44.629329"));
        DEFAULT_DELTA_HOUR_OF_DAY.put(23, new BigDecimal("-53.262604"));
    }

    //京东开屏出价区间
    public final static Map<Integer, Pair<Integer, Integer>> jdBidPriceLocation = new HashMap<>();
    //动态底价时间区间
    public static Map<Integer, Pair<Float, Float>> hourLocation = new HashMap<>();

    static {
        jdBidPriceLocation.put(1, Pair.of(0, 10));
        jdBidPriceLocation.put(2, Pair.of(10, 100));
        jdBidPriceLocation.put(3, Pair.of(100, 200));
        jdBidPriceLocation.put(4, Pair.of(200, 300));
        jdBidPriceLocation.put(5, Pair.of(300, 400));
        jdBidPriceLocation.put(6, Pair.of(400, 500));
        jdBidPriceLocation.put(7, Pair.of(500, 600));
        jdBidPriceLocation.put(8, Pair.of(600, 700));
        jdBidPriceLocation.put(9, Pair.of(700, 800));
        jdBidPriceLocation.put(10, Pair.of(800, 900));
        jdBidPriceLocation.put(11, Pair.of(900, 1000));
        jdBidPriceLocation.put(12, Pair.of(1000, 1500));
        jdBidPriceLocation.put(13, Pair.of(1500, 2000));
        jdBidPriceLocation.put(14, Pair.of(2000, 2500));
        jdBidPriceLocation.put(15, Pair.of(2500, 3000));
        jdBidPriceLocation.put(16, Pair.of(3000, 3500));
        jdBidPriceLocation.put(17, Pair.of(3500, 4000));
        jdBidPriceLocation.put(18, Pair.of(4000, 4500));
        jdBidPriceLocation.put(19, Pair.of(4500, 5000));
        jdBidPriceLocation.put(20, Pair.of(5000, 5500));
        jdBidPriceLocation.put(21, Pair.of(5500, 6000));
        jdBidPriceLocation.put(22, Pair.of(6000, 6500));
        jdBidPriceLocation.put(23, Pair.of(6500, 7000));
        jdBidPriceLocation.put(24, Pair.of(7000, 7500));
        jdBidPriceLocation.put(25, Pair.of(7500, 8000));
        jdBidPriceLocation.put(26, Pair.of(8000, 8500));
        jdBidPriceLocation.put(27, Pair.of(8500, 9000));
        jdBidPriceLocation.put(28, Pair.of(9000, 9500));
        jdBidPriceLocation.put(29, Pair.of(9500, 10000000));

        hourLocation.put(0, Pair.of(0F, 6F)); //redis key
        hourLocation.put(1, Pair.of(6F, 9F));
        hourLocation.put(2, Pair.of(9F, 11.5F));
        hourLocation.put(3, Pair.of(11.5F, 14F));
        hourLocation.put(4, Pair.of(14F, 17F));
        hourLocation.put(5, Pair.of(17F, 19.5F));
        hourLocation.put(6, Pair.of(19.5F, 22.5F));
        hourLocation.put(7, Pair.of(22.5F, 23F));
    }

    //TODO 变成动态可调节
    public final static double EXP_JD_FEED_MIN_CTR = 0.1;  //参与京东动态底价feed流最低CTR
    public final static double EXP_JD_FEED_MIN_CPC_FEN = 50;  //参与京东动态底价feed流最低cpc点击成本(5毛)
    public final static long EXP_JD_FEED_MIN_BID_PRICE = 2500; //参与京东动态底价feed流最低出价

    public final static double EXP_JD_SPLASH_MIN_CTR = 0.15;  //参与京东动态底价开屏最低CTR
    public final static double EXP_JD_SPLASH_MIN_CPC_FEN = 50;  //参与京东动态底价开屏最低cpc点击成本(5毛)
    public final static long EXP_JD_SPLASH_MIN_BID_PRICE = 5000; //参与京东动态底价开屏最低出价


    public final static BigDecimal RAISE_PRICE_PROPORTION = BigDecimal.valueOf(5);
}
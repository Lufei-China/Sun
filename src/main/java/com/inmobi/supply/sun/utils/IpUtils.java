package com.inmobi.supply.sun.utils;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv4;
import com.inmobi.supply.sun.model.geo.GeoLatLonIpv6;
import com.inmobi.supply.sun.model.geo.GeoModel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class IpUtils {
    private static final Pattern VALID_IP_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$");

    public static final String IP_ADDRESS_PATTERN = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
    public static final String IPV6_ADDRESS_PATTERN = "(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))";

    public static final String[] invalidIP = new String[]{"0.0.0.0","127.0.0.1","255.255.255.255"};

    enum ReservedCidr {
        CIDR1(0, 0, 0, 0, 8),
        CIDR2(10, 0, 0, 0, 8),
        CIDR3(100, 64, 0, 0, 10),
        CIDR4(127, 0, 0, 0, 8),
        CIDR5(169, 254, 0, 0, 16),
        CIDR6(172, 16, 0, 0, 12),
        CIDR7(192, 0, 0, 0, 24),
        CIDR8(192, 0, 2, 0, 24),
        CIDR9(192, 88, 99, 0, 24),
        CIDR10(192, 168, 0, 0, 16),
        CIDR11(198, 18, 0, 0, 15),
        CIDR12(198, 51, 100, 0, 24),
        CIDR13(203, 0, 113, 0, 24),
        CIDR14(224, 0, 0, 0, 4),
        CIDR15(240, 0, 0, 0, 4),
        CIDR16(255, 255, 255, 255, 32);

        private final long subnetMask;
        private final long beginIp;
        private final long endIp;
        private static final long IPSIZE = 32L;

        ReservedCidr(int firstOctet, int secondOctet, int thirdOctet, int forthOctet, int subnetMast) {
            this.subnetMask = subnetMast;
            long ipaddressInlong = forthOctet + 256L * (thirdOctet + 256L * (secondOctet + 256L * firstOctet));
            this.beginIp = (((1L << IPSIZE) - 1) - ((1L << (IPSIZE - subnetMask)) - 1)) & ipaddressInlong;
            this.endIp = ((1L << (IPSIZE - subnetMask)) - 1) | beginIp;
        }

        protected boolean isIpInRange(long inetAddress) {
            return inetAddress >= beginIp && inetAddress <= endIp;
        }
    }

    public static boolean isPublicIp(InetAddress inetAddress) {
        for (ReservedCidr cidr : ReservedCidr.values()) {
            if (cidr.isIpInRange(ipv4ToLong(inetAddress)))
                return false;
        }
        return true;
    }

    public static InetAddress ipv4ToAddr(String ip) {
        try {
            if (VALID_IP_PATTERN.matcher(ip).find()) {
                return InetAddress.getByName(ip);
            } else {
                return null;
            }
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static long ipv4ToLong(InetAddress inetAddress) {
        Preconditions.checkNotNull(inetAddress);
        byte[] ipChunks = inetAddress.getAddress();
        return ((ipChunks[0] & 0xFFl) << (24)) +
                ((ipChunks[1] & 0xFFl) << (16)) +
                ((ipChunks[2] & 0xFFl) << (8)) +
                (ipChunks[3] & 0xFFl);
    }

    public static long ipv4ToLong(String ipAddress) {
        int point1 = ipAddress.indexOf(".");
        int point2 = ipAddress.indexOf(".", point1 + 1);
        int point3 = ipAddress.indexOf(".", point2 + 1);
        long[] lon = new long[4];

        String[] ip = ipAddress.split("\\.", 4);
        lon[0] = Long.parseLong(ip[0]);
        lon[1] = Long.parseLong(ip[1]);
        lon[2] = Long.parseLong(ip[2]);
        lon[3] = Long.parseLong(ip[3]);
        return (lon[0] << 24) + (lon[1] << 16) + (lon[2] << 8) + (lon[3]);
    }

    public static BigInteger ipv6ToLong(String ipAddress) throws UnknownHostException {
        InetAddress a = InetAddress.getByName(ipAddress);
        byte[] bytes = a.getAddress();
        return new BigInteger(1, bytes);
    }

    public static GeoModel search(List<GeoModel> geoModelList, long target) {
        if (CollectionUtils.isEmpty(geoModelList)) {
            return null;
        }
        int startIndex = 0;
        int endIndex = geoModelList.size() - 1;
        int middleIndex;
        while (startIndex <= endIndex) {
            middleIndex = startIndex + (endIndex - startIndex) / 2;
            if (geoModelList.get(middleIndex).getIntRangeFrom() <= target && target <= geoModelList.get(middleIndex).getIntRangeEnd()) {
                return geoModelList.get(middleIndex);
            } else if (target < geoModelList.get(middleIndex).getIntRangeFrom()) {
                endIndex = middleIndex - 1;
            } else if (target > geoModelList.get(middleIndex).getIntRangeEnd()) {
                startIndex = middleIndex + 1;
            }
        }
        return null;
    }

    /*public static CaaGeoModel searchCaa(List<CaaGeoModel> caaGeoModelList, long target) {
        if (CollectionUtils.isEmpty(caaGeoModelList)) {
            return null;
        }
        int startIndex = 0;
        int endIndex = caaGeoModelList.size() - 1;
        int middleIndex;
        while (startIndex <= endIndex) {
            middleIndex = startIndex + (endIndex - startIndex) / 2;
            if (caaGeoModelList.get(middleIndex).getRangeFrom() <= target && target <= caaGeoModelList.get(middleIndex).getRangeEnd()) {
                return caaGeoModelList.get(middleIndex);
            } else if (target < caaGeoModelList.get(middleIndex).getRangeFrom()) {
                endIndex = middleIndex - 1;
            } else if (target > caaGeoModelList.get(middleIndex).getRangeEnd()) {
                startIndex = middleIndex + 1;
            }
        }
        return null;
    }*/

    public static GeoLatLonIpv4 searchLatLonIpv4(List<GeoLatLonIpv4> geoLatLonIpv4List, long target) {
        if (CollectionUtils.isEmpty(geoLatLonIpv4List)) {
            return null;
        }
        int startIndex = 0;
        int endIndex = geoLatLonIpv4List.size() - 1;
        int middleIndex;
        while (startIndex <= endIndex) {
            middleIndex = startIndex + (endIndex - startIndex) / 2;
            if (geoLatLonIpv4List.get(middleIndex).getMin() <= target && target <= geoLatLonIpv4List.get(middleIndex).getMax()) {
                return geoLatLonIpv4List.get(middleIndex);
            } else if (target < geoLatLonIpv4List.get(middleIndex).getMin()) {
                endIndex = middleIndex - 1;
            } else if (target > geoLatLonIpv4List.get(middleIndex).getMax()) {
                startIndex = middleIndex + 1;
            }
        }
        return null;
    }

    public static GeoLatLonIpv6 searchLatLonIpv6(List<GeoLatLonIpv6> geoLatLonIpv6List, BigInteger target) {
        if (CollectionUtils.isEmpty(geoLatLonIpv6List)) {
            return null;
        }
        int startIndex = 0;
        int endIndex = geoLatLonIpv6List.size() - 1;
        int middleIndex;
        while (startIndex <= endIndex) {
            middleIndex = startIndex + (endIndex - startIndex) / 2;
            if (geoLatLonIpv6List.get(middleIndex).getMin().compareTo(target) <= 0
                    && target.compareTo(geoLatLonIpv6List.get(middleIndex).getMax()) <= 0) {
                return geoLatLonIpv6List.get(middleIndex);
            } else if (target.compareTo(geoLatLonIpv6List.get(middleIndex).getMin()) < 0) {
                endIndex = middleIndex - 1;
            } else if (target.compareTo(geoLatLonIpv6List.get(middleIndex).getMax()) > 0) {
                startIndex = middleIndex + 1;
            }
        }
        return null;
    }

    /**
     * Validate ip address with regular expression
     * @param ip ip-address for validation
     * @return true valid ip address, false invalid ip address
     */
    public static boolean isValidIpV4(final String ip) {
        return StringUtils.isNotBlank(ip) && ip.matches(IP_ADDRESS_PATTERN) && StringUtils.indexOfAny(ip, invalidIP) == -1;
    }


    public static boolean isValidIpV6(final String ip) {
        //return InetAddresses.isInetAddress(ip);
        //return IPAddressUtil.isIPv6LiteralAddress(ip);
        return StringUtils.isNotBlank(ip) && ip.matches(IPV6_ADDRESS_PATTERN);
    }

    public static void main(String[] args) {
        System.out.println(isValidIpV6("2409:891f:9302:6c91:a94d:e579:d07:b95f"));
    }
}
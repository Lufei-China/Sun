package com.inmobi.supply.sun.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import cn.inmobi.pb.api.Version;
import com.inmobi.supply.sun.constant.SymbolString;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.lang3.tuple.Pair;

import static com.inmobi.supply.sun.constant.AuctionParam.hourLocation;

@Slf4j
public class WorkerUtils {
    private static final String IDP_EKEY="lrFia8KpYNs6zYv0";

    public static String getEncryptedCrId(String crId) throws Exception {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(IDP_EKEY.getBytes(), "Blowfish");
            Cipher cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE,
                    secretKeySpec);
            byte[] encryptedStringBytes = cipher.doFinal(crId.getBytes(StandardCharsets.UTF_8));
            return new Base64().encodeToString(encryptedStringBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new Exception(e);
        }
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static byte[] mergeBytes(byte[]... byteArrays) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            for (byte[] bytes : byteArrays) {
                outputStream.write(bytes);
            }
        } catch (IOException e) {
            log.error("an error happened on google double click, IOException");
        }
        return outputStream.toByteArray();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getLong();
    }

    public static byte[] compressResponseBytes(byte[] responseBytes) throws InvalidMessageException {
        byte[] compressedBytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)){
            gzipOutputStream.write(responseBytes);
            gzipOutputStream.close();
            compressedBytes = outputStream.toByteArray();
            outputStream.close();
        } catch (IOException ie) {
            log.error("Exception thrown while compressing response.", ie);
            throw new InvalidMessageException(ie);
        }
        return compressedBytes;
    }

    /**
     * get days before dates list
     * format:yyyyMMdd
     * @param n
     * @return
     */
    public static List<String> nDaysBefore(int n) {
        List<String> nDaysBeforeList = new ArrayList<>();
        for (int i = 1; i <= n; i ++) {
            FastDateFormat format = FastDateFormat.getInstance("yyyyMMdd", TimeZone.getDefault(), Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.DATE, -i);
            Date d = c.getTime();
            String day = format.format(d);
            nDaysBeforeList.add(day);
        }
        return nDaysBeforeList;
    }

    /**
     * get today date
     * format:yyyyMMdd
     * @return
     */
    public static String todayDate() {
        FastDateFormat format = FastDateFormat.getInstance("yyyyMMdd", TimeZone.getDefault(), Locale.getDefault());
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DATE, 0);
        Date d = c.getTime();
        return format.format(d);
    }

    /**
     * get gap between date1 and date2
     * @return
     */
    public static long dateGap(String dateStr1, String dateStr2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        try {
            Date date1 = sdf.parse(dateStr1);
            Date date2 = sdf.parse(dateStr2);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date1);
            long timeInMillis1 = calendar.getTimeInMillis();
            calendar.setTime(date2);
            long timeInMillis2 = calendar.getTimeInMillis();
            if (timeInMillis1 >= timeInMillis2) {
                return (timeInMillis1 - timeInMillis2) / (1000L * 3600L * 24L);
            } else {
                return (timeInMillis2 - timeInMillis1) / (1000L * 3600L * 24L);
            }
        } catch (ParseException e) {
            return 0L;
        }
    }

    /**
     * 获取当前时刻的小时
     */
    public static int hourOfCurrent() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    public static int hourLocation(int hour) {
        for (Map.Entry<Integer, Pair<Float, Float>> entry : hourLocation.entrySet()) {
            if (hour >= entry.getValue().getLeft() && hour < entry.getValue().getRight()) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public static String httpsTrans(RunTimeContext runTimeContext, String url) {
        if (runTimeContext.isSecure()
                && !runTimeContext.getRequestType().isSdk()) {
            //replace http to https if needed
            return StringUtils.replace(url, SymbolString.HTTP_SEP, SymbolString.HTTPS_SEP);
        }
        return url;
    }

    public static int versionCompare(Version version1, Version version2) {
        String major1 = String.valueOf(version1.getMajor());
        String minor1 = String.valueOf(version1.getMinor());
        String micro1 = String.valueOf(version1.getMicro());

        String major2 = String.valueOf(version2.getMajor());
        String minor2 = String.valueOf(version2.getMinor());
        String micro2 = String.valueOf(version2.getMicro());
        return (major1 + minor1 + micro1).compareTo(major2 + minor2 + micro2);
    }
}

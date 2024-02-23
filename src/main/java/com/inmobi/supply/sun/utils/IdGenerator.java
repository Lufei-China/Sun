package com.inmobi.supply.sun.utils;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.inmobi.supply.framework.common.utils.sfidgen.SfIdGen;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * requestId generator
 */
public class IdGenerator {

    private static final AtomicInteger byteCounter = new AtomicInteger();
    private static final AtomicInteger shortCounter = new AtomicInteger();

    private static byte nextByte() {
        return (byte) (Math.abs(byteCounter.incrementAndGet()) % Byte.MAX_VALUE);
    }

    /*private static int randomIntKey(DemandSource dst) {
        int nextShort = Math.abs(shortCounter.incrementAndGet()) % Short.MAX_VALUE;
        return (dst.getValue() << 16) | nextShort;
    }*/
    /*
    public static GUID generateAdUnitId(String requestGuid, DemandSource dst) {
        UUID renderUnitId = WilburyUUID.setIntKey(requestGuid, randomIntKey(dst));
        renderUnitId = WilburyUUID.setCyclicCounter(renderUnitId.toString(), nextByte());
        return new GUID(renderUnitId.getMostSignificantBits(), renderUnitId.getLeastSignificantBits());
    }*/
    public static String genRequestId() {
        return SfIdGen.nextId() + String.format("%04d", new Random().nextInt(10000));
    }

    public static UUID generateRequestId( ) {
        UUID uuid = getUUID(System.currentTimeMillis());
        return cyclicCounter(uuid.toString(), nextByte());
    }

    public static UUID getUUID(long time) {
        Random rand = new Random();
        long clock_seq = rand.nextLong() & 16128L;
        long lsig = -4611686018427387904L | clock_seq << 48;
        long msig = (time & 4294967295L) << 32 | (time & 281470681743360L) >> 16 | 4096L | (time & 1152640029630136320L) >> 48;
        return new UUID(msig, lsig);
    }

    public static UUID cyclicCounter(String uuid, byte value) {
        UUID u = UUID.fromString(uuid);
        long msig = u.getMostSignificantBits();
        long lsig = u.getLeastSignificantBits();
        lsig &= -64424509448L;
        long lower4BitsValue = (long)value << 32 & 64424509440L;
        long new_lsig = lsig | lower4BitsValue;
        new_lsig |= (long)(value >>> 4);
        return new UUID(msig, new_lsig);
    }
    /*public static GUID generateImpressionId(String requestGuid, int adIncId) {
        UUID impressionId = WilburyUUID.setIntKey(requestGuid, adIncId);
        impressionId = WilburyUUID.setCyclicCounter(impressionId.toString(), nextByte());
        return new GUID(impressionId.getMostSignificantBits(), impressionId.getLeastSignificantBits());
    }*/

    @Data
    @AllArgsConstructor
    public static class GUID {
        private long idHigh;
        private long idLow;

    }
}
package com.inmobi.supply.sun.utils;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.inject.Inject;

import com.google.common.base.MoreObjects;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.min;

@Slf4j
public class JdCryptoUtils {
    public static final int INITV_BASE = 0;
    public static final int INITV_SIZE = 16;
    public static final int INITV_TIMESTAMP_OFFSET = 0;
    public static final int INITV_SERVERID_OFFSET = 8;
    public static final int PAYLOAD_BASE = INITV_BASE + INITV_SIZE;
    public static final int SIGNATURE_SIZE = 4;
    public static final int OVERHEAD_SIZE = INITV_SIZE + SIGNATURE_SIZE;
    private static final int COUNTER_PAGESIZE = 20;
    private static final int COUNTER_SECTIONS = 3*256 + 1;
    private static final int MICROS_PER_CURRENCY_UNIT = 1;

    private final Keys keys;
    private final ThreadLocalRandom fastRandom = ThreadLocalRandom.current();

    public JdCryptoUtils(Keys keys) {
        this.keys = keys;
    }

    protected @Nullable String encode(@Nullable byte[] data) {
        return data == null ? null : BaseEncoding.base64Url().encode(data);
    }

    public byte[] encrypt(byte[] plainData) {
        checkArgument(plainData.length >= OVERHEAD_SIZE,
                "Invalid plainData, %s bytes", plainData.length);
        byte[] workBytes = plainData.clone();
        ByteBuffer workBuffer = ByteBuffer.wrap(workBytes);
        boolean success = false;

        try {
            int signature = hmacSignature(workBytes);
            workBuffer.putInt(workBytes.length - SIGNATURE_SIZE, signature);
            xorPayloadToHmacPad(workBytes);
            if (log.isDebugEnabled()) {
                //log.debug(dump("Encrypted", plainData, workBytes));
            }
            success = true;
            return workBytes;
        } finally {
            if (!success && log.isDebugEnabled()) {
                //log.debug(dump("Encrypted (failed)", plainData, workBytes));
            }
        }
    }

    protected byte[] initPlainData(int payloadSize, @Nullable byte[] initVector) {
        byte[] plainData = new byte[OVERHEAD_SIZE + payloadSize];

        if (initVector == null) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(plainData);
            byteBuffer.putLong(INITV_TIMESTAMP_OFFSET, System.nanoTime());
            byteBuffer.putLong(INITV_SERVERID_OFFSET, fastRandom.nextLong());
        } else {
            System.arraycopy(initVector, 0, plainData, INITV_BASE, min(INITV_SIZE, initVector.length));
        }

        return plainData;
    }

    private void xorPayloadToHmacPad(byte[] workBytes) {
        int payloadSize = workBytes.length - OVERHEAD_SIZE;
        int sections = (payloadSize + COUNTER_PAGESIZE - 1) / COUNTER_PAGESIZE;
        checkArgument(sections <= COUNTER_SECTIONS, "Payload is %s bytes, exceeds limit of %s",
                payloadSize, COUNTER_PAGESIZE * COUNTER_SECTIONS);

        Mac encryptionHmac = createMac();

        byte[] pad = new byte[COUNTER_PAGESIZE + 3];
        int counterSize = 0;

        for (int section = 0; section < sections; ++section) {
            int sectionBase = section * COUNTER_PAGESIZE;
            int sectionSize = min(payloadSize - sectionBase, COUNTER_PAGESIZE);

            try {
                encryptionHmac.reset();
                encryptionHmac.init(keys.getEncryptionKey());
                encryptionHmac.update(workBytes, INITV_BASE, INITV_SIZE);
                if (counterSize != 0) {
                    encryptionHmac.update(pad, COUNTER_PAGESIZE, counterSize);
                }
                encryptionHmac.doFinal(pad, 0);
            } catch (ShortBufferException | InvalidKeyException e) {
                throw new IllegalStateException(e);
            }

            for (int i = 0; i < sectionSize; ++i) {
                workBytes[PAYLOAD_BASE + sectionBase + i] ^= pad[i];
            }

            Arrays.fill(pad, 0, COUNTER_PAGESIZE, (byte) 0);

            if (counterSize == 0 || ++pad[COUNTER_PAGESIZE + counterSize - 1] == 0) {
                ++counterSize;
            }
        }
    }

    private static Mac createMac() {
        try {
            return Mac.getInstance("HmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@code signature = hmac(integrityKey, payload || initVector)}
     */
    private int hmacSignature(byte[] workBytes) {
        try {
            Mac integrityHmac = createMac();
            integrityHmac.init(keys.getIntegrityKey());
            integrityHmac.update(workBytes, PAYLOAD_BASE, workBytes.length - OVERHEAD_SIZE);
            integrityHmac.update(workBytes, INITV_BASE, INITV_SIZE);
            return Ints.fromByteArray(integrityHmac.doFinal());
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dump(String header, byte[] inData, byte[] workBytes) {
        ByteBuffer initvBuffer = ByteBuffer.wrap(workBytes, INITV_BASE, INITV_SIZE);
        Date timestamp = new Date(initvBuffer.getLong(INITV_BASE + INITV_TIMESTAMP_OFFSET));
        long serverId = initvBuffer.getLong(INITV_BASE + INITV_SERVERID_OFFSET);
        return new StringBuilder()
                .append(header)
                .append(": initVector={timestamp ")
                .append(DateFormat.getDateTimeInstance().format(timestamp))
                .append(", serverId ").append(serverId)
                .append("}, input =").append(BaseEncoding.base16().encode(inData))
                .append(", output =").append(BaseEncoding.base16().encode(workBytes))
                .toString();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues()
                .add("keys", keys)
                .toString();
    }

    public static class Keys {
        private final SecretKey encryptionKey;
        private final SecretKey integrityKey;

        public Keys(SecretKey encryptionKey, SecretKey integrityKey) throws InvalidKeyException {
            this.encryptionKey = encryptionKey;
            this.integrityKey = integrityKey;
            Mac hmac = JdCryptoUtils.createMac();
            hmac.init(encryptionKey);
            hmac.reset();
            hmac.init(integrityKey);
            hmac.reset();
        }

        public SecretKey getEncryptionKey() {
            return encryptionKey;
        }

        public SecretKey getIntegrityKey() {
            return integrityKey;
        }

        @Override
        public int hashCode() {
            return encryptionKey.hashCode() ^ integrityKey.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Keys)) {
                return false;
            }
            Keys other = (Keys) obj;
            return encryptionKey.equals(other.encryptionKey) && integrityKey.equals(other.integrityKey);
        }

        @Override public String toString() {
            return MoreObjects.toStringHelper(this).omitNullValues()
                    .add("encryptionKey", encryptionKey.getAlgorithm() + '/' + encryptionKey.getFormat())
                    .add("integrityKey", integrityKey.getAlgorithm() + '/' + integrityKey.getFormat())
                    .toString();
        }
    }

    public static class Price extends JdCryptoUtils {
        private static final int PAYLOAD_SIZE = 8;

        @Inject
        public Price(Keys keys) {
            super(keys);
        }

        public byte[] encryptPriceMicros(long priceValue, @Nullable byte[] initVector) {
            byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
            ByteBuffer.wrap(plainData).putLong(PAYLOAD_BASE, priceValue);
            return encrypt(plainData);
        }


        public String encodePriceMicros(long priceMicros, @Nullable byte[] initVector) {
            return encode(encryptPriceMicros(priceMicros, initVector));
        }

        public String encodePriceValue(double priceValue, @Nullable byte[] initVector) {
            return encodePriceMicros((long) (priceValue * MICROS_PER_CURRENCY_UNIT), initVector);
        }
    }
}

package com.inmobi.supply.sun.utils;

import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.inmobi.supply.sun.exception.InmobiSecureException;
import com.inmobi.supply.sun.exception.InvalidMessageException;
import org.apache.commons.codec.binary.Base64;

/**
 * SecureSession
 */
public class SecureSession {
    public byte[] initializationVector;
    public byte[] aesKey;
    private byte[] hash;
    private byte[] hMacKey;
    private final byte[] secureMessage;
    private final RSAPrivateKey privateKey;
    public SecureSession(byte[] secureMessage, RSAPrivateKey privateKey) {
        this.secureMessage = secureMessage;
        this.privateKey = privateKey;
    }

    /**
     * Constructor to extract only secure keys from encrypted message
     * @param keys : AES + Hmac + IV
     * @param privateKey RSAKey
     * @param encryptOnly Flag to determine whether keys are to be extracted or message decrypted
     * @throws InmobiSecureException
     * @throws InvalidMessageException
     */
    public SecureSession(byte[] keys, RSAPrivateKey privateKey, boolean encryptOnly) throws InmobiSecureException, InvalidMessageException {

        this.privateKey = privateKey;
        if (encryptOnly){
            setKeys(keys);
            this.secureMessage = null;
        } else{
            this.secureMessage = keys;
        }

    }

    private void setKeys(byte[] keys) throws InmobiSecureException, InvalidMessageException {
        //Decode the keys
        byte[] decodedSecureMessage = Base64.decodeBase64(keys);

        try {
            byte[] secretBytes = rsaDecryption(decodedSecureMessage);
            //decryptedEss will have aesKey_L + hmacKey_L
            this.hMacKey = extractSecretKeys(secretBytes);

        } catch (BufferUnderflowException | NegativeArraySizeException bue) {
            throw new InvalidMessageException(bue);
        }
    }

    public byte[] getInitializationVector() throws InmobiSecureException {
        if (this.initializationVector != null && this.initializationVector.length != 0) {
            return this.initializationVector;
        } else {
            throw new InmobiSecureException("Null or empty InitializationVector");
        }
    }

    public byte[] getAESKey() throws InmobiSecureException {
        if (this.aesKey != null && this.aesKey.length != 0) {
            return this.aesKey;
        } else {
            throw new InmobiSecureException("Null or empty AESKey");
        }
    }

    public String getDecodedMessage() throws InmobiSecureException, InvalidMessageException {
        String plainText = null;
        if (null == secureMessage) {
            throw new InvalidMessageException("secure message is null");
        }
        //Decode the secure message
        byte[] decodedSecureMessage = Base64.decodeBase64(secureMessage);
        /**
         * Please refer the following
         * document for the encodin/decoding scheme
         * https://docs.google.com/a/inmobi.com/document/d/1kjnC_W44kWPvW-a-CF5NZ6G_ZES-njJgyElbp9gJXiw/edit#
         */

        try {
            ByteBuffer secureMessageByteBuffer = ByteBuffer.allocate(decodedSecureMessage.length);

            secureMessageByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            secureMessageByteBuffer.put(decodedSecureMessage);
            secureMessageByteBuffer.flip();
            int lenESS = (int) secureMessageByteBuffer.getLong();
            byte[] ess = new byte[lenESS];
            secureMessageByteBuffer.get(ess);

            int lenAESBytes = (int) secureMessageByteBuffer.getLong();

            byte[] aesBytes = new byte[lenAESBytes];
            secureMessageByteBuffer.get(aesBytes);
            int lenHash = (int) secureMessageByteBuffer.getLong();
            byte[] hash = new byte[lenHash];
            secureMessageByteBuffer.get(hash);
            byte[] secretBytes = rsaDecryption(ess);
            //decryptedEss will have aesKey_L + hmacKey_L
            byte[] hmacKey = extractSecretKeys(secretBytes);
            /**
             * Recalculate hash using hmac(aeskey, hmacKey)
             *  and compare with the hash received in request
             *  If they match, get plain text using the aesKey.
             */
            byte[] recalculatedHash = recalculateHash(aesBytes, hmacKey);
            if (Arrays.equals(hash, recalculatedHash)) {
                //Fetch plainText
                try {
                    SecretKeySpec secretKeySpec = new SecretKeySpec(aesKey, "AES");
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE,
                            secretKeySpec,
                            new IvParameterSpec(initializationVector));
                    byte[] plainTextBytes = cipher.doFinal(aesBytes);
                    plainText = new String(plainTextBytes, Charset.forName("UTF-8"));
                } catch (NoSuchAlgorithmException e) {
                    throw new InmobiSecureException(e);
                } catch (NoSuchPaddingException e) {
                    throw new InmobiSecureException(e);
                } catch (InvalidKeyException e) {
                    throw new InmobiSecureException(e);
                } catch (InvalidAlgorithmParameterException e) {
                    throw new InmobiSecureException(e);
                } catch (IllegalBlockSizeException e) {
                    throw new InvalidMessageException(e);
                } catch (BadPaddingException e) {
                    throw new InvalidMessageException(e);
                }
            } else {
                throw new InvalidMessageException("Hash is not equal. Aborting");
            }
        } catch (BufferUnderflowException bue) {
            throw new InvalidMessageException(bue);
        } catch (NegativeArraySizeException e) {
            throw new InvalidMessageException(e);
        }
        return plainText;
    }

    private byte[] extractSecretKeys(byte[] secretBytes) throws BufferUnderflowException {
        ByteBuffer buffer = ByteBuffer.allocate(secretBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(secretBytes);
        ((Buffer) buffer).flip();
        //Extract the AES Key and HMACKey
        int lenAESKey = (int) buffer.getLong();
        byte[] aesKey = new byte[lenAESKey];
        buffer.get(aesKey);

        this.aesKey = aesKey;

        int lenHashKey = (int) buffer.getLong();
        byte[] hmacKey = new byte[lenHashKey];
        buffer.get(hmacKey);

        int ivLenInLE = (int) buffer.getLong();
        byte[] initializationVector = new byte[ivLenInLE];
        buffer.get(initializationVector);
        this.initializationVector = initializationVector;

        return hmacKey;
    }

    private byte[] recalculateHash(byte[] aesKey, byte[] hmacKey)
            throws InmobiSecureException, InvalidMessageException {
        byte[] retVal = null;
        String hashAlgo = "HmacSHA1";
        SecretKeySpec key = new SecretKeySpec(hmacKey, 0, hmacKey.length, hashAlgo);

        Mac mac;
        try {
            mac = Mac.getInstance(hashAlgo);
            mac.init(key);
            retVal = mac.doFinal(aesKey);
        } catch (NoSuchAlgorithmException e) {
            throw new InmobiSecureException(e);
        } catch (InvalidKeyException e) {
            throw new InmobiSecureException(e);
        }
        return retVal;
    }

    private byte[] rsaDecryption(byte[] ess) throws InmobiSecureException,
            InvalidMessageException {
        byte[] retVal = null;
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/nopadding");
            c.init(Cipher.DECRYPT_MODE, privateKey);
            retVal = c.doFinal(ess);
        } catch (NoSuchPaddingException e) {
            throw new InmobiSecureException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new InmobiSecureException(e);
        } catch (InvalidKeyException e) {
            throw new InmobiSecureException(e);
        } catch (IllegalBlockSizeException e) {
            throw new InvalidMessageException(e);
        } catch (BadPaddingException e) {
            throw new InvalidMessageException(e);
        }
        return retVal;
    }

    public byte[] write(byte[] responseBytes) throws InmobiSecureException,
            InvalidMessageException {
        return write(responseBytes, this.aesKey, this.initializationVector);
    }


    public byte[] write(byte[] responseBytes, byte[] aesKey, byte[] initVector) throws InmobiSecureException,
            InvalidMessageException {
        byte[] msgBytes = null;
        try {
            SecretKeySpec sks = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(initVector);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, sks, ivSpec);
            msgBytes = c.doFinal(responseBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InmobiSecureException(e);
        } catch (InvalidKeyException e) {
            throw new InmobiSecureException(e);
        } catch (NoSuchPaddingException e) {
            throw new InmobiSecureException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InmobiSecureException(e);
        } catch (IllegalBlockSizeException e) {
            throw new InvalidMessageException(e);
        } catch (BadPaddingException e) {
            throw new InvalidMessageException(e);
        }
        return Base64.encodeBase64(msgBytes);
    }

}

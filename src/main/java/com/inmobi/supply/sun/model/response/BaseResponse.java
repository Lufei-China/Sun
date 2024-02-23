package com.inmobi.supply.sun.model.response;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

/**
 * BaseResponse
 * @author remi.li
 */
@ToString
@EqualsAndHashCode
public final class BaseResponse {

    private final byte[] outputBytes;

    @Getter
    private final String outputString;

    @Getter
    private final MediaType contentType;

    @Getter
    private final int statusCode;

    @Getter
    private final Charset charset;

    @Getter
    private final ImmutableMap<String, String> headerMap;

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] outputBytes;

        private String outputString;

        private MediaType contentType;

        private Charset charset;

        private int statusCode = HttpServletResponse.SC_OK;

        private final ImmutableMap.Builder<String, String> headerMapBuilder
                = new ImmutableMap.Builder<String, String>();

        private Builder() {
        }

        public Builder setOutputBytes(byte[] outputBytes) {
            // Intentionally not making a defensive copy.
            this.outputBytes = outputBytes;
            return this;
        }

        public Builder setContentType(MediaType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder setCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder setStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder setOutputString(String outputString) {
            this.outputString = outputString;
            return this;
        }

        public Builder setHeader(String key, String value) {
            headerMapBuilder.put(key, value);
            return this;
        }

        public BaseResponse build() {
            return new BaseResponse(this);
        }
    }

    private BaseResponse(Builder builder) {
        this.outputBytes = builder.outputBytes;
        this.outputString = builder.outputString;
        this.charset = builder.charset;
        this.contentType = builder.contentType;
        this.statusCode = builder.statusCode;
        this.headerMap = builder.headerMapBuilder.build();
    }

    /**
     * @return a defensive copy of output bytes to ensure BaseResponse is immutable.
     */
    public byte[] getOutputBytes() {
        return outputBytes;
    }
}
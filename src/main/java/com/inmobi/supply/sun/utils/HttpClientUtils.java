package com.inmobi.supply.sun.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
public class HttpClientUtils {
    private CloseableHttpClient httpClient;

    private RequestConfig requestConfig;

    public HttpClientUtils() {
        init();
    }

    public void init() {
        PoolingHttpClientConnectionManager httpClientConnectionManager = new PoolingHttpClientConnectionManager();
        // 创建全局的requestConfig
        this.requestConfig = RequestConfig.custom().build();
        // 声明重定向策略对象
        LaxRedirectStrategy redirectStrategy = new LaxRedirectStrategy();
        this.httpClient = HttpClients.custom().setConnectionManager(httpClientConnectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRedirectStrategy(redirectStrategy)
                .build();
    }

    public void doPost4ProtocleBuffer(String url, byte[] bytes) throws Exception {
        // 创建http POST请求
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(this.requestConfig);
        httpPost.setHeader("Connection", "keep-alive");
        httpPost.setHeader("Content-type", "application/x-protobuf");
        httpPost.setHeader("Accept-Encoding", "gzip");
        httpPost.setHeader("Accept-Charset", "utf-8");
        httpPost.setHeader("X-Inmobi-Host", "union.api.w.inmobi.cn");

        if (bytes != null) {
            // 构造一个请求实体
            ByteArrayEntity byteArrayEntity = new ByteArrayEntity(bytes);
            byteArrayEntity.setContentType("application/x-protobuf");
            // 将请求实体设置到httpPost对象中
            httpPost.setEntity(byteArrayEntity);
        }

        CloseableHttpResponse response = null;
        try {
            // 执行请求
            response = this.httpClient.execute(httpPost);

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public static String methodGet(String urlStr) {
        StringBuffer sb = new StringBuffer();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            /* 获取网址对应的输入流和输出流 */
            InputStream is = connection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            log.error("HttpClientUtils methodGet error!",e);
            return "";
        }
        return sb.toString();
    }
}

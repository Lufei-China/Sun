package com.inmobi.supply.sun.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * url utils
 */
@Slf4j
public class UrlUtils {
    public static String addUrlParams(String clickUrl, String ...querySnippet) {
        URI uri;
        try {
            uri = URI.create(clickUrl);
        } catch (RuntimeException e) {
            log.error("Unable to parse click url", e);
            return clickUrl;
        }
        StringBuilder sb = new StringBuilder();
        String query = uri.getQuery();
        if (query != null) {
            sb.append("&");
        }
        if (querySnippet.length > 0) {
            Arrays.asList(querySnippet).forEach(sb::append);
        }
        try {
            return new URI(uri.getScheme(),
                    uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), sb.toString(),
                    uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            log.error("Unable to add async parameters to click url.", e);
        }
        return clickUrl;
    }

    public static Map<String, String> URLRequest(String URL) {
        Map<String, String> mapRequest = new HashMap<>();

        String[] arrSplit = null;

        String strUrlParam = TruncateUrlPage(URL);
        if(strUrlParam == null) {
            return mapRequest;
        }
        //每个键值为一组
        arrSplit = strUrlParam.split("[&]");
        for(String strSplit : arrSplit) {
            String[] arrSplitEqual = null;
            arrSplitEqual = strSplit.split("[=]");

            //解析出键值
            if(arrSplitEqual.length > 1) {
                //正确解析
                mapRequest.put(arrSplitEqual[0], arrSplitEqual[1]);

            }
            else {
                if(!arrSplitEqual[0].equals("")) {
                    //只有参数没有值，不加入
                    mapRequest.put(arrSplitEqual[0], "");
                }
            }
        }
        return mapRequest;
    }

    /**
     * 去掉url中的路径，留下请求参数部分
     * @param strURL url地址
     * @return url请求参数部分
     */
    private static String TruncateUrlPage(String strURL)
    {
        String strAllParam = null;
        String[] arrSplit = null;

        strURL = strURL.trim().toLowerCase();

        arrSplit=strURL.split("[?]");
        if(strURL.length() > 1) {
            if(arrSplit.length > 1) {
                if(arrSplit[1] != null) {
                    strAllParam = arrSplit[1];
                }
            }
        }
        return strAllParam;
    }
}

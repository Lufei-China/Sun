package com.inmobi.supply.sun.model.response;

import com.inmobi.supply.sun.constant.RequestType;

public class NoFillResponse {
    public static byte[] jsonNoFillResponse(String requestId, RequestType requestType) {
        if (requestType == RequestType.API_ORTB_1_0) {
            return ortbNoFillResponse();
        }
        return ("{\"ads\": [],\"requestId\":" + "\"" + requestId + "\"" + "}").getBytes();
    }

    public static byte[] jsonNoFillResponse(String requestId) {
        return ("{\"ads\": [],\"requestId\":" +  "\"" + requestId + "\"" + "}").getBytes();
    }


    public static byte[] jsonNoFillResponse() {
        return "{\"ads\": [] }".getBytes();
    }

    public static byte[] jsonNoFillResponseNoRequestIdWithTermReason(String reason) {
        return ("{\"ads\": []," + ",\"reason\":\"" + reason +  "\"" + "}").getBytes();
    }
    public static byte[] jsonNoFillResponseWithTermReason(String requestId, String reason) {
        return ("{\"ads\": [],\"requestId\":" + "\"" + requestId + "\"" + ",\"reason\":\"" + reason + "\"" + "}").getBytes();
    }

    public static byte[] jsonNoFillResponse(RequestType requestType) {
        if (requestType == RequestType.API_ORTB_1_0) {
            return ortbNoFillResponse();
        }
        return "{\"ads\": [] }".getBytes();
    }

    public static byte[] jsonNoFillResponseNoRequestIdWithTermReason(String reason, RequestType requestType) {
        if (requestType == RequestType.API_ORTB_1_0) {
            return ortbNoFillResponse();
        }
        return ("{\"ads\": []," + ",\"reason\":\"" + reason + "\"" + "}").getBytes();
    }

    public static byte[] ortbNoFillResponse() {
        return ("").getBytes();
    }
}
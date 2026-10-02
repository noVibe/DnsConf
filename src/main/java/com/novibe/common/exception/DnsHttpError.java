package com.novibe.common.exception;

import com.novibe.common.util.Jsonable;
import lombok.Getter;

import java.net.http.HttpResponse;

import static java.util.Objects.isNull;

@Getter
public class DnsHttpError extends RuntimeException {

    private final int code;
    private final String reason;
    private final String requestUrl;
    private final Jsonable requestPayload;

    public DnsHttpError(HttpResponse<?> response, Jsonable requestPayload) {
        super(messageBuilder(
                response.request().method(),
                response.request().uri().toString(),
                isNull(requestPayload) ? null : requestPayload.toJson(),
                response.statusCode(),
                response.body().toString()));
        this.code = response.statusCode();
        this.requestUrl = response.request().uri().toString();
        this.reason = response.body().toString();
        this.requestPayload = requestPayload;
    }

    private static String messageBuilder(String requestMethod, String requestUrl, String requestPayload,
                                         int statusCode, String responsePayload) {
        return "Failed request on %s %s with payload:\n%s\nCode: %s, reason:\n%s".formatted(
                requestMethod, requestUrl, requestPayload,
                statusCode, responsePayload);
    }

}

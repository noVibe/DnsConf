package com.novibe.common;

import com.google.gson.Gson;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.util.Jsonable;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Sends authorized json requests to a DNS provider api.
 */
@Setter(onMethod_ = @Autowired)
public abstract class ApiRequestSender extends HttpRequestSender {

    protected Gson jsonMapper;
    protected DnsProfile dnsProfile;

    protected abstract String apiUrl();

    protected abstract String authHeaderName();

    protected abstract String authHeaderValue();

    protected abstract void react401();

    protected abstract void react403();

    protected abstract void react404(DnsHttpError dnsHttpError);

    @Override
    protected String requestUrl(String path) {
        return apiUrl() + (isNull(path) ? "" : path);
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of(authHeaderName(), authHeaderValue(), "Content-Type", "application/json");
    }

    @Override
    protected void reactOnError(DnsHttpError dnsHttpError) {
        switch (dnsHttpError.getCode()) {
            case 401 -> react401();
            case 403 -> react403();
            case 404 -> react404(dnsHttpError);
            default -> throw dnsHttpError;
        }
    }

    public <T> T get(String path, Class<T> responseType) {
        return mapResponse(sendRequest(GET, path, null), responseType);
    }

    public <T, R extends Jsonable> T post(String path, R requestBody, Class<T> responseType) {
        return mapResponse(sendRequest(POST, path, requestBody), responseType);
    }

    public <T> T delete(String path, Class<T> responseType) {
        return mapResponse(sendRequest(DELETE, path, null), responseType);
    }

    private <T> T mapResponse(HttpResponse<String> response, Class<T> responseType) {
        if (response.body().isEmpty()) {
            return null;
        }
        return jsonMapper.fromJson(response.body(), responseType);
    }

}

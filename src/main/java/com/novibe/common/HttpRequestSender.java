package com.novibe.common;

import com.google.gson.Gson;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.util.Jsonable;
import com.novibe.common.util.RetryUtils;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import static java.util.Objects.isNull;

@Setter(onMethod_ = @Autowired)
public abstract class HttpRequestSender {

    private final Semaphore semaphore = new Semaphore(100);

    protected static final String GET = "GET";
    protected static final String POST = "POST";
    protected static final String DELETE = "DELETE";

    protected abstract String apiUrl();

    protected abstract String authHeaderName();

    protected abstract String authHeaderValue();

    protected abstract void react401();

    protected abstract void react403();

    protected abstract void react404(DnsHttpError dnsHttpError);

    protected abstract int retryAttempts();

    protected abstract Duration retryDelay();

    protected HttpClient httpClient;
    protected Gson jsonMapper;
    protected DnsProfile dnsProfile;

    public <T> T get(String path, Class<T> responseType) {
        return sendRequest(GET, path, null, responseType);
    }

    public <T, R extends Jsonable> T post(String path, R requestBody, Class<T> responseType) {
        return sendRequest(POST, path, requestBody, responseType);
    }

    public <T> T delete(String path, Class<T> responseType) {
        return sendRequest(DELETE, path, null, responseType);

    }

    protected <T, R extends Jsonable> T sendRequest(String method, String path, R body, Class<T> responseBody) {
        URI uri = URI.create(apiUrl() + (isNull(path) ? "" : path));
        HttpRequest.BodyPublisher requestBody;
        if (isNull(body)) {
            requestBody = HttpRequest.BodyPublishers.noBody();
        } else {
            requestBody = HttpRequest.BodyPublishers.ofString(body.toJson());
        }
        try {
            for (int attempt = 1; ; attempt++) {
                HttpResponse<String> response = send(uri, method, requestBody);
                int responseCode = response.statusCode();

                if (responseCode > 299) {
                    DnsHttpError httpError = new DnsHttpError(response, body);
                    switch (responseCode) {
                        case 401 -> react401();
                        case 403 -> react403();
                        case 404 -> react404(httpError);
                        case int code when RetryUtils.isTemporaryError(code) -> {
                            waitOrThrow(httpError, attempt);
                            continue;
                        }
                        default -> throw httpError;
                    }
                }
                if (response.body().isEmpty()) {
                    return null;
                }
                return jsonMapper.fromJson(response.body(), responseBody);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitOrThrow(DnsHttpError httpError, int attempt) {
        if (attempt >= retryAttempts()) {
            throw httpError;
        }
        RetryUtils.waitBeforeRetry(retryDelay(), httpError.getCode(), attempt, retryAttempts());
    }

    private HttpResponse<String> send(URI uri, String method, HttpRequest.BodyPublisher requestBody)
            throws IOException, InterruptedException {
        semaphore.acquire();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header(authHeaderName(), authHeaderValue())
                    .header("Content-Type", "application/json")
                    .method(method, requestBody)
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } finally {
            semaphore.release();
        }
    }

}

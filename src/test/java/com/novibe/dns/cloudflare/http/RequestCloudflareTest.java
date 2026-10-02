package com.novibe.dns.cloudflare.http;

import com.google.gson.Gson;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.dns.cloudflare.http.dto.response.list.MultiListApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestCloudflareTest {

    private static final int RETRY_ATTEMPTS = 3;

    @Mock
    private HttpClient httpClient;

    @Spy
    private Gson jsonMapper = new Gson();

    @Spy
    private DnsProfile dnsProfile = DnsProfile.builder().clientId("account").authSecret("token").build();

    @Spy
    @InjectMocks
    private RequestCloudflare requestCloudflare;

    @Test
    void retriesRateLimitedRequestUntilItSucceeds() throws IOException, InterruptedException {
        withoutRetryDelay();
        doReturn(response(429, "rate limited"), response(200, "{\"success\":true}"))
                .when(httpClient).send(any(), any());

        MultiListApiResponse response = requestCloudflare.get("/lists", MultiListApiResponse.class);

        assertTrue(response.isSuccess());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void retriesServerErrorsAsWell() throws IOException, InterruptedException {
        withoutRetryDelay();
        doReturn(response(503, "unavailable"), response(200, "{\"success\":true}"))
                .when(httpClient).send(any(), any());

        assertTrue(requestCloudflare.get("/lists", MultiListApiResponse.class).isSuccess());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void givesUpWhenTemporaryErrorKeepsRepeating() throws IOException, InterruptedException {
        withoutRetryDelay();
        doReturn(response(429, "rate limited")).when(httpClient).send(any(), any());

        DnsHttpError error = assertThrows(DnsHttpError.class,
                () -> requestCloudflare.get("/lists", MultiListApiResponse.class));

        assertEquals(429, error.getCode());
        verify(httpClient, times(RETRY_ATTEMPTS)).send(any(), any());
    }

    @Test
    void doesNotRetryOnNotFound() throws IOException, InterruptedException {
        doReturn(response(404, "not found")).when(httpClient).send(any(), any());

        assertThrows(DnsHttpError.class, () -> requestCloudflare.get("/lists", MultiListApiResponse.class));

        verify(httpClient, times(1)).send(any(), any());
    }

    private void withoutRetryDelay() {
        doReturn(Duration.ZERO).when(requestCloudflare).retryDelay();
    }

    private HttpResponse<String> response(int statusCode, String body) {
        HttpResponse<String> response = mock();
        lenient().when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        lenient().when(response.request())
                .thenReturn(HttpRequest.newBuilder(URI.create("https://api.cloudflare.test/lists")).build());
        return response;
    }
}

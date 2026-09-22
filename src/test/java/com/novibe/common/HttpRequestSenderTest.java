package com.novibe.common;

import com.google.gson.Gson;
import com.novibe.common.exception.DnsHttpError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class HttpRequestSenderTest {

    private static final int RETRY_ATTEMPTS = 3;

    @Mock
    private HttpClient httpClient;

    private HttpRequestSender sender;

    @BeforeEach
    void setUp() {
        sender = mock(HttpRequestSender.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        sender.setHttpClient(httpClient);
        sender.setJsonMapper(new Gson());
        lenient().when(sender.apiUrl()).thenReturn("https://api.test");
        lenient().when(sender.authHeaderName()).thenReturn("X-Api-Key");
        lenient().when(sender.authHeaderValue()).thenReturn("secret");
        lenient().when(sender.retryAttempts()).thenReturn(RETRY_ATTEMPTS);
        lenient().when(sender.retryDelay()).thenReturn(Duration.ZERO);
    }

    @Test
    void retriesRateLimitedRequestUntilItSucceeds() throws IOException, InterruptedException {
        doReturn(response(429, "Rate exceeded"), response(200, "{\"value\":\"ok\"}"))
                .when(httpClient).send(any(), any());

        TestResponse result = sender.get("/rewrites", TestResponse.class);

        assertEquals("ok", result.value);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void retriesServerErrorsAsWell() throws IOException, InterruptedException {
        doReturn(response(503, "Service Unavailable"), response(200, "{\"value\":\"ok\"}"))
                .when(httpClient).send(any(), any());

        assertEquals("ok", sender.get("/rewrites", TestResponse.class).value);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void givesUpWhenTemporaryErrorKeepsRepeating() throws IOException, InterruptedException {
        doReturn(response(429, "Rate exceeded")).when(httpClient).send(any(), any());

        DnsHttpError error = assertThrows(DnsHttpError.class, () -> sender.get("/rewrites", TestResponse.class));

        assertEquals(429, error.getCode());
        verify(httpClient, times(RETRY_ATTEMPTS)).send(any(), any());
    }

    @Test
    void doesNotRetryOnNotFound() throws IOException, InterruptedException {
        doReturn(response(404, "")).when(httpClient).send(any(), any());

        sender.get("/rewrites", TestResponse.class);

        verify(sender).react404(any());
        verify(httpClient, times(1)).send(any(), any());
    }

    private HttpResponse<String> response(int statusCode, String body) {
        HttpResponse<String> response = mock();
        lenient().when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        lenient().when(response.request()).thenReturn(HttpRequest.newBuilder(URI.create("https://api.test/rewrites")).build());
        return response;
    }

    private static class TestResponse {
        private String value;
    }
}

package com.novibe.dns.next_dns.http;

import com.google.gson.Gson;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NextDnsRewriteClientTest {

    @Mock
    private HttpClient httpClient;

    @Spy
    private Gson jsonMapper = new Gson();

    @Spy
    private DnsProfile dnsProfile = DnsProfile.builder().clientId("profile").authSecret("api-key").build();

    @Spy
    @InjectMocks
    private NextDnsRewriteClient nextDnsRewriteClient;

    @Test
    void sendsRequestPerRewrite() throws IOException, InterruptedException {
        doReturn(response(200, "{}")).when(httpClient).send(any(), any());

        nextDnsRewriteClient.saveRewrites(List.of(
                new CreateRewriteDto("chatgpt.com", "1.2.3.4"),
                new CreateRewriteDto("openai.com", "1.2.3.4")));

        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void keepsSendingWhenApiAnswersWithErrors() throws IOException, InterruptedException {
        doReturn(response(200, "{\"errors\":[{\"code\":\"invalidContent\"}]}")).when(httpClient).send(any(), any());

        nextDnsRewriteClient.deleteRewritesByIds(List.of("first", "second", "third"));

        verify(httpClient, times(3)).send(any(), any());
    }

    @Test
    void retriesRateLimitedRequest() throws IOException, InterruptedException {
        doReturn(Duration.ZERO).when(nextDnsRewriteClient).retryDelay();
        doReturn(response(429, "Rate exceeded"), response(200, "{\"data\":[]}"))
                .when(httpClient).send(any(), any());

        assertEquals(List.of(), nextDnsRewriteClient.fetchRewrites());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void countsOnlySuccessfulRequestsInProgress() throws IOException, InterruptedException {
        doReturn(response(200, "{\"errors\":[{\"code\":\"conflict\"}]}"), response(200, "{}"))
                .when(httpClient).send(any(), any());

        String log = captureLog(() -> nextDnsRewriteClient.saveRewrites(List.of(
                new CreateRewriteDto("chatgpt.com", "1.2.3.4"),
                new CreateRewriteDto("openai.com", "1.2.3.4"))));

        assertTrue(log.contains("Current success progress: 1/2"));
        assertFalse(log.contains("2/2"));
        assertTrue(log.contains("Completed with 1 requests failed of 2"));
    }

    private String captureLog(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return captured.toString();
    }

    private HttpResponse<String> response(int statusCode, String body) {
        HttpResponse<String> response = mock();
        lenient().when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        lenient().when(response.request())
                .thenReturn(HttpRequest.newBuilder(URI.create("https://api.nextdns.test/rewrites")).build());
        return response;
    }
}

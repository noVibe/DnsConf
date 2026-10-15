package com.novibe.common.data_sources;

import com.novibe.common.base_structures.BypassRoute;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListLoaderTest {

    private static final int RETRY_ATTEMPTS = 3;

    private static final String HOSTS_URL = "https://hosts.test/hosts";

    private static final String BROKEN_URL = "https://hosts.test/broken";

    private static final String HOSTS_BODY = """
            1.2.3.4 chatgpt.com
            1.2.3.4 openai.com
            """;

    @Mock
    private HttpClient httpClient;

    @Spy
    @InjectMocks
    private HostsOverrideListsLoader loader;

    @Test
    void loadsRoutesFromRespondingSource() throws IOException, InterruptedException {
        doReturn(response(200, HOSTS_BODY, HOSTS_URL)).when(httpClient).send(any(), any());

        List<BypassRoute> routes = loader.fetchWebsites(List.of(HOSTS_URL));

        assertEquals(List.of("chatgpt.com", "openai.com"), routes.stream().map(BypassRoute::website).toList());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void loadsOnlySelectedRedirectSection() throws IOException, InterruptedException {
        String body = """
                1.2.3.4 claude.ai
                # Google Gemini AI
                2.3.4.5 gemini.google.com
                2.3.4.5 generativelanguage.googleapis.com
                # Grok AI
                3.4.5.6 grok.com
                """;
        doReturn(response(200, body, HOSTS_URL)).when(httpClient).send(any(), any());
        HostsOverrideListsLoader restrictedLoader = spy(new HostsOverrideListsLoader());
        doReturn("Google Gemini AI").when(restrictedLoader).selectedSection();
        restrictedLoader.setHttpClient(httpClient);

        List<BypassRoute> routes = restrictedLoader.fetchWebsites(List.of(HOSTS_URL));

        assertEquals(List.of("gemini.google.com", "generativelanguage.googleapis.com"),
                routes.stream().map(BypassRoute::website).toList());
    }

    @Test
    void stopsWhenSelectedRedirectSectionIsMissing() throws IOException, InterruptedException {
        doReturn(response(200, HOSTS_BODY, HOSTS_URL)).when(httpClient).send(any(), any());
        HostsOverrideListsLoader restrictedLoader = spy(new HostsOverrideListsLoader());
        doReturn("Google Gemini AI").when(restrictedLoader).selectedSection();
        restrictedLoader.setHttpClient(httpClient);

        assertTrue(assertThrows(Exception.class, () -> restrictedLoader.fetchWebsites(List.of(HOSTS_URL)))
                .getMessage().contains("Redirect section not found"));
    }

    @Test
    void stopsWhenSelectedRedirectSectionHasNoRoutes() throws IOException, InterruptedException {
        doReturn(response(200, "# Google Gemini AI\n# Grok AI\n3.4.5.6 grok.com", HOSTS_URL))
                .when(httpClient).send(any(), any());
        HostsOverrideListsLoader restrictedLoader = spy(new HostsOverrideListsLoader());
        doReturn("Google Gemini AI").when(restrictedLoader).selectedSection();
        restrictedLoader.setHttpClient(httpClient);

        assertTrue(assertThrows(Exception.class, () -> restrictedLoader.fetchWebsites(List.of(HOSTS_URL)))
                .getMessage().contains("no valid routes"));
    }

    @Test
    void retriesWhenSourceIsTemporarilyUnavailable() throws IOException, InterruptedException {
        withoutRetryDelay();
        doReturn(response(429, "too many requests", HOSTS_URL),
                response(503, "unavailable", HOSTS_URL),
                response(200, HOSTS_BODY, HOSTS_URL))
                .when(httpClient).send(any(), any());

        List<BypassRoute> routes = loader.fetchWebsites(List.of(HOSTS_URL));

        assertEquals(2, routes.size());
        verify(httpClient, times(RETRY_ATTEMPTS)).send(any(), any());
    }

    @Test
    void givesUpAfterThreeAttempts() throws IOException, InterruptedException {
        withoutRetryDelay();
        doReturn(response(503, "unavailable", HOSTS_URL)).when(httpClient).send(any(), any());

        assertTrue(messageOfFailure(HOSTS_URL).contains("response code 503"));
        verify(httpClient, times(RETRY_ATTEMPTS)).send(any(), any());
    }

    @Test
    void doesNotRetryWhenSourceIsNotFound() throws IOException, InterruptedException {
        doReturn(response(404, "404: Not Found", BROKEN_URL)).when(httpClient).send(any(), any());

        assertTrue(messageOfFailure(BROKEN_URL).contains("response code 404"));
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void failsWhenOnlyOneOfSourcesIsBroken() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            return request.uri().toString().equals(BROKEN_URL)
                    ? response(404, "404: Not Found", BROKEN_URL)
                    : response(200, HOSTS_BODY, HOSTS_URL);
        }).when(httpClient).send(any(), any());

        assertTrue(messageOfFailure(HOSTS_URL, BROKEN_URL).contains("response code 404"));
    }

    private void withoutRetryDelay() {
        doReturn(Duration.ZERO).when(loader).retryDelay();
    }

    private String messageOfFailure(String... urls) {
        Exception exception = assertThrows(Exception.class, () -> loader.fetchWebsites(List.of(urls)));
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("response code")) {
                return cause.getMessage();
            }
        }
        return "";
    }

    private HttpResponse<String> response(int statusCode, String body, String url) {
        HttpResponse<String> response = mock();
        lenient().when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        lenient().when(response.request()).thenReturn(HttpRequest.newBuilder(URI.create(url)).build());
        return response;
    }
}

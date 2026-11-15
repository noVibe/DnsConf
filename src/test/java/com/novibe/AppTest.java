package com.novibe;

import com.novibe.common.exception.CredentialsException;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.exception.ProfileSetupException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class AppTest {

    @Test
    void findsTheProfileFailureUnderExecutorWrappers() {
        ProfileSetupException refused = new CredentialsException("Invalid api key!");

        ProfileSetupException found = App.profileSetupCause(new RuntimeException(new ExecutionException(refused)));

        assertEquals(refused, found);
    }

    @Test
    void findsTheProfileFailureItself() {
        ProfileSetupException refused = new CredentialsException("Invalid api key!");

        assertEquals(refused, App.profileSetupCause(refused));
    }

    @Test
    void treatsAnApiRefusalAsAProfileFailure() {
        DnsHttpError refused = new DnsHttpError(refusedResponse(), null);

        assertEquals(refused, App.profileSetupCause(new RuntimeException(new ExecutionException(refused))));
    }

    @Test
    void keepsUnrelatedExceptionsUnexpected() {
        assertNull(App.profileSetupCause(new IllegalStateException(new IllegalArgumentException("nothing to do with a profile"))));
    }

    @Test
    void survivesCausesThatLoopBack() {
        RuntimeException itsOwnCause = new RuntimeException("itself") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        RuntimeException second = new RuntimeException("second");
        RuntimeException first = new RuntimeException("first", second);
        second.initCause(first);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertNull(App.profileSetupCause(itsOwnCause));
            assertNull(App.profileSetupCause(first));
        });
    }

    private HttpResponse<String> refusedResponse() {
        HttpResponse<String> response = mock();
        lenient().when(response.statusCode()).thenReturn(400);
        lenient().when(response.body()).thenReturn("{\"errors\":[{\"message\":\"invalid domain name: *.0-02.net\"}]}");
        lenient().when(response.request())
                .thenReturn(HttpRequest.newBuilder(URI.create("https://api.cloudflare.com/client/v4/accounts/a/gateway/lists")).build());
        return response;
    }
}

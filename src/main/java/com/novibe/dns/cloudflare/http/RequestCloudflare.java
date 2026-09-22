package com.novibe.dns.cloudflare.http;

import com.novibe.common.HttpRequestSender;
import com.novibe.common.exception.CredentialsException;
import com.novibe.common.exception.DnsHttpError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RequestCloudflare extends HttpRequestSender {

    private static final int RETRY_ATTEMPTS = 3;

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    @Override
    protected int retryAttempts() {
        return RETRY_ATTEMPTS;
    }

    @Override
    protected Duration retryDelay() {
        return RETRY_DELAY;
    }

    @Override
    protected String apiUrl() {
        return "https://api.cloudflare.com/client/v4/accounts/%s/gateway".formatted(dnsProfile.clientId());
    }

    @Override
    protected String authHeaderName() {
        return "Authorization";
    }

    @Override
    protected String authHeaderValue() {
        return "Bearer " + dnsProfile.authSecret();
    }

    @Override
    protected final void react401() {
        throw new CredentialsException("Invalid API Token!");
    }

    @Override
    protected void react403() {
        throw new CredentialsException("""
                Token doesn't have necessary permissions!
                Generate a token with permissions:
                1) Zero Trust:Edit
                2) Account Firewall Access Rules:Edit""");
    }

    @Override
    protected void react404(DnsHttpError httpError) {
        throw httpError;
    }

}

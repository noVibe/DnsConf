package com.novibe.dns.next_dns.http;

import com.novibe.common.HttpRequestSender;
import com.novibe.common.exception.CredentialsException;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.util.Log;

import java.time.Duration;

public abstract class AbstractNextDnsHttpClient extends HttpRequestSender {

    private static final int RETRY_ATTEMPTS = 10;

    // NextDNS api rate limiter resets 60 seconds after the last request
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    protected abstract String path();

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
        return "https://api.nextdns.io/profiles/%s".formatted(dnsProfile.clientId());
    }

    @Override
    protected String authHeaderName() {
        return "X-Api-Key";
    }

    @Override
    protected String authHeaderValue() {
        return dnsProfile.authSecret();
    }

    @Override
    protected final void react401() {
        throw new CredentialsException("Invalid api key");
    }

    @Override
    protected void react403() {
        throw new CredentialsException("Invalid api key!");
    }

    @Override
    protected void react404(DnsHttpError dnsHttpError) {
        if (dnsProfile.clientId() != null) {
            Log.fail("Make sure that values of AUTH_SECRET and CLIENT_ID belongs to same account! Providing a new API Token to AUTH_SECRET may help.");
        }
        throw dnsHttpError;
    }
}

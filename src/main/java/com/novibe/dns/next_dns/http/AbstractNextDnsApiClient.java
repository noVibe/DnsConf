package com.novibe.dns.next_dns.http;

import com.novibe.common.http.ApiRequestSender;
import com.novibe.common.exception.CredentialsException;
import com.novibe.common.exception.ProfileSetupException;
import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.dto.response.NextDnsResponse;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public abstract class AbstractNextDnsApiClient extends ApiRequestSender {

    private static final int RETRY_ATTEMPTS = 3;

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

    /**
     * Sends a request per element, so the progress of long lists is visible in the log.
     * A refused element stops the profile: a half applied configuration must not end up as a successful run.
     */
    protected <D, R extends NextDnsResponse<?>> void callApiForEach(List<D> requestList, Function<D, R> request) {
        for (int i = 0; i < requestList.size(); i++) {
            R response = request.apply(requestList.get(i));
            if (ofNullable(response).map(NextDnsResponse::getErrors).isPresent()) {
                throw new ProfileSetupException("Failed request %s of %s: %s"
                        .formatted(i + 1, requestList.size(), response.getErrors()));
            }
            Log.progress("Current success progress: " + (i + 1) + "/" + requestList.size());
        }
        Log.common("\nCompleted");
    }

}

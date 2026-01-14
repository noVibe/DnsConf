package com.novibe.dns.next_dns.http;

import com.novibe.common.exception.NextDnsHttpError;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.dto.response.NextDnsResponse;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

import static com.novibe.common.config.EnvironmentVariables.*;
import static java.util.Optional.ofNullable;

@UtilityClass
public class NextDnsRateLimitedApiProcessor {

    // Rate limiting configuration - configurable via environment variables
    // Conservative defaults based on observed NextDNS API behavior (allows ~60 requests before rate limiting)
    private static final double MAX_REQUESTS_PER_SECOND = parseDoubleEnv(NEXTDNS_MAX_REQUESTS_PER_SECOND, 0.25); // 15 requests per minute (conservative)
    private static final int MAX_REQUESTS_PER_MINUTE = parseIntEnv(NEXTDNS_MAX_REQUESTS_PER_MINUTE, 12);
    private static final int RATE_LIMIT_BACKOFF_SECONDS = parseIntEnv(NEXTDNS_RATE_LIMIT_BACKOFF_SECONDS, 60);

    private static double parseDoubleEnv(String envVar, double defaultValue) {
        if (envVar == null || envVar.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(envVar.trim());
        } catch (NumberFormatException e) {
            Log.common("Warning: Invalid value for %s, using default %.2f".formatted(envVar, defaultValue));
            return defaultValue;
        }
    }

    private static int parseIntEnv(String envVar, int defaultValue) {
        if (envVar == null || envVar.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(envVar.trim());
        } catch (NumberFormatException e) {
            Log.common("Warning: Invalid value for %s, using default %d".formatted(envVar, defaultValue));
            return defaultValue;
        }
    }

    // Rate tracking
    private static final LinkedList<Instant> requestTimestamps = new LinkedList<>();
    private static Instant lastRateLimitHit = null;

    @SneakyThrows
    public <D, R extends NextDnsResponse<?>> void callApi(List<D> requestList, Function<D, R> request) {
        Queue<D> requestQueue = new ArrayDeque<>(requestList);
        int successCounter = 0;
        int totalRequests = requestList.size();

        Log.common("Rate limiting: max %.2f requests/second (%d requests/minute) - configured conservatively for NextDNS API"
                .formatted(MAX_REQUESTS_PER_SECOND, MAX_REQUESTS_PER_MINUTE));

        while (!requestQueue.isEmpty()) {
            enforceRateLimit();

            D requestDto = requestQueue.poll();
            try {
                R response = request.apply(requestDto);
                if (ofNullable(response).map(r -> r.getErrors()).isPresent()) {
                    Log.fail("Failed request: " + response.getErrors());
                } else {
                    recordSuccessfulRequest();
                    Log.progress("Current success progress: " + ++successCounter + "/" + totalRequests);
                }
            } catch (NextDnsHttpError e) {
                if (e.getCode() == 524 || e.getCode() == 429) {
                    handleRateLimitError(requestDto, requestQueue, e);
                } else {
                    Log.fail(e.toString());
                    System.exit(1);
                }
            }
        }
    }

    private void enforceRateLimit() throws InterruptedException {
        // Clean old timestamps (older than 1 minute)
        Instant oneMinuteAgo = Instant.now().minus(60, ChronoUnit.SECONDS);
        requestTimestamps.removeIf(timestamp -> timestamp.isBefore(oneMinuteAgo));

        // Always enforce minimum interval between requests to prevent bursts
        // Even with empty timestamp list, we start with conservative rate limiting
        double minIntervalMs = 1000.0 / MAX_REQUESTS_PER_SECOND;

        if (!requestTimestamps.isEmpty()) {
            Instant lastRequest = requestTimestamps.getLast();
            Duration timeSinceLastRequest = Duration.between(lastRequest, Instant.now());

            if (timeSinceLastRequest.toMillis() < minIntervalMs) {
                long waitMs = (long) (minIntervalMs - timeSinceLastRequest.toMillis());
                if (waitMs > 0) {
                    if (waitMs > 100) { // Only log significant waits
                        Log.common("Rate limiting: waiting %d ms to maintain %.2f requests/second"
                                .formatted(waitMs, MAX_REQUESTS_PER_SECOND));
                    }
                    Thread.sleep(waitMs);
                }
            }
        } else {
            // First request or after clearing timestamps - add a small initial delay to be safe
            long initialDelayMs = (long) (minIntervalMs * 0.1); // 10% of minimum interval
            if (initialDelayMs > 0) {
                Thread.sleep(initialDelayMs);
            }
        }

        // Check per-minute limit
        if (requestTimestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            Instant oldestRequest = requestTimestamps.getFirst();
            Duration timeToWait = Duration.between(Instant.now(), oldestRequest.plus(60, ChronoUnit.SECONDS));
            if (!timeToWait.isNegative()) {
                Log.common("Rate limit: reached %d requests/minute, waiting %d seconds"
                        .formatted(MAX_REQUESTS_PER_MINUTE, timeToWait.getSeconds()));
                Thread.sleep(timeToWait.toMillis());
            }
        }
    }

    private void recordSuccessfulRequest() {
        requestTimestamps.add(Instant.now());
    }

    private void handleRateLimitError(Object requestDto, Queue<Object> requestQueue, NextDnsHttpError e) {
        // Put the failed request back in queue
        requestQueue.add(requestDto);

        // Calculate current rate for logging
        long requestsInLastMinute = requestTimestamps.stream()
                .filter(timestamp -> timestamp.isAfter(Instant.now().minus(60, ChronoUnit.SECONDS)))
                .count();

        double currentRate = (double) requestsInLastMinute / 60.0;

        Log.common("Sending speed: %.2f requests per second (%d requests in last minute)"
                .formatted(currentRate, requestsInLastMinute));
        Log.common("Code %s. API rate limit reached. Waiting for reset: %d seconds"
                .formatted(e.getCode(), RATE_LIMIT_BACKOFF_SECONDS));

        // Don't clear timestamps - instead wait for the rate limit to reset
        // This preserves the rate limiting history and makes the system more conservative
        lastRateLimitHit = Instant.now();

        runWaitTimer(RATE_LIMIT_BACKOFF_SECONDS);
        Log.common("Continue...");
    }

    @SneakyThrows
    private void runWaitTimer(int seconds) {
        Thread.sleep(Duration.of(seconds, ChronoUnit.SECONDS));
    }

}

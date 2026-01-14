package com.novibe.common.config;

import java.util.Objects;

public class EnvironmentVariables {

    public static final String DNS = Objects.requireNonNull(System.getenv("DNS"));

    public static final String CLIENT_ID = Objects.requireNonNull(System.getenv("CLIENT_ID"));

    public static final String AUTH_SECRET = Objects.requireNonNull(System.getenv("AUTH_SECRET"));

    public static final String BLOCK = System.getenv("BLOCK");

    public static final String REDIRECT = System.getenv("REDIRECT");

    public static final String NEXTDNS_MAX_REQUESTS_PER_SECOND = System.getenv("NEXTDNS_MAX_REQUESTS_PER_SECOND");

    public static final String NEXTDNS_MAX_REQUESTS_PER_MINUTE = System.getenv("NEXTDNS_MAX_REQUESTS_PER_MINUTE");

    public static final String NEXTDNS_RATE_LIMIT_BACKOFF_SECONDS = System.getenv("NEXTDNS_RATE_LIMIT_BACKOFF_SECONDS");

}

package com.novibe.dns.next_dns.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record NextDnsRewriteExclusionConfig(List<String> patterns, boolean cleanupExisting) {

    public NextDnsRewriteExclusionConfig {
        patterns = List.copyOf(Optional.ofNullable(patterns).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(pattern -> !pattern.isBlank())
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .toList());

        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("NEXTDNS_REWRITE_EXCLUSIONS.patterns must contain at least one pattern");
        }
    }
}

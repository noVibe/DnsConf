package com.novibe.dns.next_dns.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildcardDomainMatcherTest {

    private final WildcardDomainMatcher matcher = new WildcardDomainMatcher(
            List.of("*.instagram.com", "*.facebook.com")
    );

    @Test
    void matchesApexAndSubdomainsForLeadingWildcardPatterns() {
        assertTrue(matcher.matches("instagram.com"));
        assertTrue(matcher.matches("www.instagram.com"));
        assertTrue(matcher.matches("b.i.instagram.com"));
        assertTrue(matcher.matches("z-p42-chat-e2ee-ig.facebook.com"));
    }

    @Test
    void leavesNonMatchingDomainsUntouched() {
        assertFalse(matcher.matches("google.com"));
        assertFalse(matcher.matches("example.org"));
    }
}

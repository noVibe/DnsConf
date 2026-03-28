package com.novibe.dns.next_dns.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextDnsRewriteExclusionParserTest {

    private final NextDnsRewriteExclusionParser parser = new NextDnsRewriteExclusionParser(new Gson());

    @Test
    void returnsEmptyWhenConfigIsBlank() {
        assertTrue(parser.parse("   ").isEmpty());
    }

    @Test
    void defaultsCleanupExistingToFalse() {
        Optional<NextDnsRewriteExclusionConfig> config = parser.parse("""
                {
                  "patterns": ["*.instagram.com"]
                }
                """);

        assertTrue(config.isPresent());
        assertEquals(1, config.get().patterns().size());
        assertFalse(config.get().cleanupExisting());
    }

    @Test
    void respectsCleanupExistingTrue() {
        Optional<NextDnsRewriteExclusionConfig> config = parser.parse("""
                {
                  "patterns": ["*.instagram.com", "*.facebook.com"],
                  "cleanupExisting": true
                }
                """);

        assertTrue(config.isPresent());
        assertEquals(2, config.get().patterns().size());
        assertTrue(config.get().cleanupExisting());
    }

    @Test
    void failsForMalformedJson() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{invalid json}")
        );

        assertTrue(error.getMessage().contains("Invalid NEXTDNS_REWRITE_EXCLUSIONS JSON"));
    }

    @Test
    void failsWhenCleanupExistingIsNotBoolean() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("""
                        {
                          "patterns": ["*.instagram.com"],
                          "cleanupExisting": "false"
                        }
                        """)
        );

        assertEquals("NEXTDNS_REWRITE_EXCLUSIONS.cleanupExisting must be a boolean", error.getMessage());
    }
}

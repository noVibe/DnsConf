package com.novibe.dns.next_dns.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.novibe.common.util.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteExclusionParser {

    private final Gson gson;

    public Optional<NextDnsRewriteExclusionConfig> parse(String envValue) {
        if (isNull(envValue) || envValue.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonObject json = gson.fromJson(envValue, JsonObject.class);
            if (isNull(json)) {
                throw invalidConfiguration("NEXTDNS_REWRITE_EXCLUSIONS must be a JSON object");
            }

            if (!json.has("patterns") || json.get("patterns").isJsonNull()) {
                throw invalidConfiguration("NEXTDNS_REWRITE_EXCLUSIONS.patterns is required");
            }

            JsonElement patternsElement = json.get("patterns");
            if (!patternsElement.isJsonArray()) {
                throw invalidConfiguration("NEXTDNS_REWRITE_EXCLUSIONS.patterns must be an array of strings");
            }

            List<String> patterns = new ArrayList<>();
            for (JsonElement patternElement : patternsElement.getAsJsonArray()) {
                if (!patternElement.isJsonPrimitive() || !patternElement.getAsJsonPrimitive().isString()) {
                    throw invalidConfiguration("NEXTDNS_REWRITE_EXCLUSIONS.patterns must contain only strings");
                }
                patterns.add(patternElement.getAsString());
            }

            boolean cleanupExisting = false;
            if (json.has("cleanupExisting")) {
                JsonElement cleanupExistingElement = json.get("cleanupExisting");
                if (!cleanupExistingElement.isJsonPrimitive() || !cleanupExistingElement.getAsJsonPrimitive().isBoolean()) {
                    throw invalidConfiguration("NEXTDNS_REWRITE_EXCLUSIONS.cleanupExisting must be a boolean");
                }
                cleanupExisting = cleanupExistingElement.getAsBoolean();
            }

            return Optional.of(new NextDnsRewriteExclusionConfig(patterns, cleanupExisting));
        } catch (IllegalArgumentException e) {
            Log.fail(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            IllegalArgumentException invalidConfiguration = invalidConfiguration(
                    "Invalid NEXTDNS_REWRITE_EXCLUSIONS JSON: " + e.getMessage()
            );
            Log.fail(invalidConfiguration.getMessage());
            throw invalidConfiguration;
        }
    }

    private IllegalArgumentException invalidConfiguration(String message) {
        return new IllegalArgumentException(message);
    }
}

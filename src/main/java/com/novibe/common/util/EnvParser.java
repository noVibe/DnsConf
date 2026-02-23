package com.novibe.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public class EnvParser {
    public static List<String> parse(String envValue) {
        if (isNull(envValue)) return List.of();
        envValue = envValue.strip();
        if (envValue.isEmpty()) return List.of();
        return Arrays.asList(envValue.strip().split(","));
    }


    public static Set<String> parseExcludedDomains(String envValue) {
        return parse(envValue).stream()
                .map(String::toLowerCase)
                .map(String::strip)
                .map(domain -> domain.startsWith("*.") ? domain.substring(2) : domain)
                .map(domain -> domain.startsWith("www.") ? domain.substring(4) : domain)
                .filter(domain -> !domain.isBlank())
                .collect(Collectors.toSet());
    }
}

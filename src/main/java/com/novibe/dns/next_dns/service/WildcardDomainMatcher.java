package com.novibe.dns.next_dns.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WildcardDomainMatcher {

    private static final String REGEX_META_CHARACTERS = "\\.[]{}()+-^$|?";

    private final List<Pattern> compiledPatterns;

    public WildcardDomainMatcher(List<String> patterns) {
        compiledPatterns = patterns.stream()
                .map(this::compilePattern)
                .toList();
    }

    public boolean matches(String domain) {
        String normalizedDomain = normalize(domain);
        return compiledPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedDomain).matches());
    }

    private Pattern compilePattern(String pattern) {
        String normalizedPattern = normalize(pattern);
        if (normalizedPattern.startsWith("*.")) {
            return Pattern.compile("^(?:.*\\.)?" + toRegex(normalizedPattern.substring(2)) + "$");
        }
        return Pattern.compile("^" + toRegex(normalizedPattern) + "$");
    }

    private String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char symbol : pattern.toCharArray()) {
            if (symbol == '*') {
                regex.append(".*");
                continue;
            }

            if (REGEX_META_CHARACTERS.indexOf(symbol) >= 0) {
                regex.append('\\');
            }
            regex.append(symbol);
        }
        return regex.toString();
    }

    private String normalize(String domain) {
        String normalizedDomain = domain.toLowerCase(Locale.ROOT).strip();
        if (normalizedDomain.startsWith("www.")) {
            return normalizedDomain.substring("www.".length());
        }
        return normalizedDomain;
    }
}

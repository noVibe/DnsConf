package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EOL = Pattern.compile("\\r?\\n");

    public static String removeWWW(String domain) {
        if (domain.startsWith("www.")) {
            return domain.substring("www.".length());
        }
        return domain;
    }

    public static boolean isComment(String line) {
        return line.startsWith("#");
    }

    public static List<HostsLine> parseHostsLine(String line) {
        String sanitizedLine = stripInlineComment(line);
        if (sanitizedLine.isBlank()) {
            return List.of();
        }
        String[] columns = WHITESPACE.split(sanitizedLine);
        if (columns.length == 1) {
            String value = columns[0];
            return List.of(isValidIP(value) ? HostsLine.ipOnly(value) : HostsLine.domainOnly(removeWWW(value)));
        } else if (isValidIP(columns[0])) {
            String ip = columns[0];
            return Arrays.stream(columns, 1, columns.length)
                    .map(domain -> new HostsLine(ip, removeWWW(domain)))
                    .toList();
        }
        Log.fail("Failed to parse hosts line: " + line);
        return List.of();
    }

    public static Stream<String> splitByEol(String data) {
        return EOL.splitAsStream(data);
    }

    private static String stripInlineComment(String line) {
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            return line.substring(0, commentIndex).strip();
        }
        return line;
    }

    private static boolean isValidIP(String ip) {
        try {
            return !InetAddress.ofLiteral(ip).getHostAddress().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}

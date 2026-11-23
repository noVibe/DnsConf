package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
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

    public static @Nullable HostsLine parseHostsLine(String line) {
        String sanitizedLine = stripInlineComment(line);
        if (sanitizedLine.isBlank()) {
            return null;
        }
        String[] columns = WHITESPACE.split(sanitizedLine, 3);
        if (columns.length == 1) {
            String value = columns[0];
            return isValidIP(value) ? HostsLine.ipOnly(value) : HostsLine.domainOnly(removeWWW(value));
        } else if (columns.length == 2) {
            String ip = columns[0];
            String domain = removeWWW(columns[1]);
            if (isValidIP(ip)) {
                return new HostsLine(ip, domain);
            }
        }
        Log.fail("Failed to parse hosts line: " + line);
        return null;
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

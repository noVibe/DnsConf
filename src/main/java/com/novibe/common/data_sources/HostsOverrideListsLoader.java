package com.novibe.common.data_sources;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class HostsOverrideListsLoader extends ListLoader<HostsOverrideListsLoader.BypassRoute> {

    private static final Set<String> EXCLUDED_DOMAIN_ROOTS = Set.of(
            "instagram.com", "cdninstagram.com",
            "facebook.com", "facebook.net", "fbcdn.net", "fb.com", "fbsbx.com",
            "canva.com", "canva-apps.com", "canva.dev"
    );

    public record BypassRoute(String ip, String website) {
    }

    @Override
    protected String listType() {
        return "Override";
    }

    @Override
    protected Predicate<String> filterRelatedLines() {
        return line -> isIpv4HostsEntry(line)
                && !HostsBlockListsLoader.isBlock(line)
                && !isExcludedDomain(extractWebsite(line));
    }

    private static boolean isIpv4HostsEntry(String line) {
        String[] fields = line.strip().split("\\s+", 3);
        if (fields.length < 2) return false;
        String[] octets = fields[0].split("\\.", -1);
        if (octets.length != 4) return false;
        try {
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (octet.isEmpty() || value < 0 || value > 255) return false;
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    protected BypassRoute toObject(String line) {
        String[] fields = line.strip().split("\\s+", 3);
        if (fields.length < 2) {
            throw new IllegalArgumentException("Invalid hosts entry: " + line);
        }
        String ip = fields[0];
        String website = removeWWW(fields[1]);
        return new BypassRoute(ip, website);
    }

    public static boolean isExcludedDomain(String domain) {
        if (domain == null || domain.isBlank()) return false;
        String normalized = domain.strip().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("*.")) normalized = normalized.substring(2);
        while (normalized.startsWith("www.")) normalized = normalized.substring(4);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        final String hostname = normalized;
        return EXCLUDED_DOMAIN_ROOTS.stream().anyMatch(root ->
                hostname.equals(root) || hostname.endsWith("." + root));
    }

    private static String extractWebsite(String hostsEntry) {
        String[] fields = hostsEntry.strip().split("\\s+", 3);
        return fields.length >= 2 ? fields[1] : "";
    }
}

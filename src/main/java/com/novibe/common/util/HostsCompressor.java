package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HostsCompressor {

    /**
     * Drops duplicate domains and subdomains of domains that are listed too.
     * Parents are visited first, so the first route of each kept domain wins.
     */
    public static void compress(List<BypassRoute> routes) {
        routes.sort(Comparator.comparingInt(route -> StringUtils.countOccurrencesOf(route.website(), ".")));
        Set<String> kept = new HashSet<>();
        routes.removeIf(route -> isCovered(route.website(), kept) || !kept.add(route.website()));
    }

    private static boolean isCovered(String website, Set<String> kept) {
        for (int dot = website.indexOf('.'); dot >= 0; dot = website.indexOf('.', dot + 1)) {
            if (kept.contains(website.substring(dot + 1))) {
                return true;
            }
        }
        return false;
    }

}

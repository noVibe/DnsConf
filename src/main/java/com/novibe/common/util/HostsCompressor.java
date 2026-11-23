package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import org.springframework.util.StringUtils;

import java.util.*;

import static java.util.Objects.nonNull;

public class HostsCompressor {

    public static void compress(List<BypassRoute> routes) {

        Map<String, BypassRoute> parentDomains = new HashMap<>();

        routes.sort(Comparator.comparing(
                route -> StringUtils.countOccurrencesOf(route.website(), ".")));

        Iterator<BypassRoute> routeIterator = routes.iterator();
        while (routeIterator.hasNext()) {
            BypassRoute route = routeIterator.next();
            String website = route.website();
            String trimmedDomain;
            for (int level = 2; level < 10; level++) {
                trimmedDomain = DataParser.trimWebsiteToDomainLevel(website, level);
                boolean alreadyAdded = nonNull(parentDomains.putIfAbsent(trimmedDomain, route));

                if (alreadyAdded) {
                    routeIterator.remove();
                    break;
                }
                if (trimmedDomain.equals(website)) {
                    break;
                }
            }
        }
    }

}

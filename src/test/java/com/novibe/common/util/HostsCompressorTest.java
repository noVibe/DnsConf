package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostsCompressorTest {

    @Test
    void dropsDuplicatesAndSubdomainsOfListedDomains() {
        List<String> compressed = compress(
                "gemini.google.com", "v1.gemini.google.com", "google.com",
                "openai.com", "openai.com", "v2.openai.com",
                "app.app.com",
                "app.app.net",
                "app.org",
                "app.xxx.com",
                "many.levels.here.to"
        );

        assertEquals(List.of("app.app.com", "app.app.net", "app.org", "app.xxx.com",
                "google.com", "many.levels.here.to", "openai.com"), compressed);
    }

    @Test
    void keepsSiblingsWhoseParentIsNotListed() {
        assertEquals(List.of("a.example.com", "b.example.com", "bbc.co.uk", "news.co.uk"),
                compress("a.example.com", "b.example.com", "bbc.co.uk", "news.co.uk"));
    }

    @Test
    void doesNotTreatPartialLabelMatchAsParent() {
        assertEquals(List.of("ample.com", "example.com"), compress("example.com", "ample.com"));
    }

    @Test
    void handlesDeepSubdomains() {
        assertEquals(List.of("example.com"),
                compress("a.b.c.d.e.f.g.h.i.j.k.example.com", "example.com"));
    }

    @Test
    void keepsFirstIpForDuplicateDomain() {
        List<BypassRoute> routes = new ArrayList<>(List.of(
                new BypassRoute("1.1.1.1", "x.com"),
                new BypassRoute("2.2.2.2", "x.com")));

        HostsCompressor.compress(routes);

        assertEquals(List.of(new BypassRoute("1.1.1.1", "x.com")), routes);
    }

    private static List<String> compress(String... websites) {
        List<BypassRoute> routes = Stream.of(websites)
                .map(website -> new BypassRoute(null, website))
                .collect(Collectors.toCollection(ArrayList::new));
        HostsCompressor.compress(routes);
        return routes.stream().map(BypassRoute::website).sorted().toList();
    }

}

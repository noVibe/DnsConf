package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class HostsCompressorTest {


    @Test
    void testCompression() {
        List<BypassRoute> rawRoutes = Stream.of(
                "gemini.google.com", "v1.gemini.google.com", "google.com",
                "openai.com", "openai.com", "v2.openai.com",
                "app.app.com",
                "app.app.net",
                "app.org",
                "app.xxx.com",
                "many.levels.here.to"
        ).map(s -> new BypassRoute(null, s)).collect(Collectors.toCollection(ArrayList::new));

        List<BypassRoute> compressedRoutes = Stream.of(
                "google.com",
                "openai.com",
                "app.app.com",
                "app.app.net",
                "app.org",
                "app.xxx.com",
                "many.levels.here.to"
        ).map(s -> new BypassRoute(null, s)).collect(Collectors.toCollection(ArrayList::new));

        HostsCompressor.compress(rawRoutes);

        Assertions.assertTrue(compressedRoutes.containsAll(rawRoutes));
        Assertions.assertEquals(compressedRoutes.size(), rawRoutes.size());
    }

}
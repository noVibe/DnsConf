package com.novibe.dns.next_dns.service;

import com.novibe.App;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.config.AppConfig;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextDnsRewriteServiceTest {

    @BeforeAll
    static void initializeAppContext() {
        App.context = new AnnotationConfigApplicationContext(AppConfig.class);
    }

    private final NextDnsRewriteClient nextDnsRewriteClient = mock(NextDnsRewriteClient.class);
    private final NextDnsRewriteService service = new NextDnsRewriteService(nextDnsRewriteClient);
    private final Optional<WildcardDomainMatcher> exclusionMatcher = Optional.of(
            new WildcardDomainMatcher(List.of("*.instagram.com", "*.facebook.com"))
    );

    @Test
    void buildNewRewritesSkipsExcludedDomainsFromRepresentativeGeoHideDnsData() throws Exception {
        Map<String, CreateRewriteDto> rewriteRequests = service.buildNewRewrites(loadRepresentativeOverrides(), exclusionMatcher);

        assertEquals(2, rewriteRequests.size());
        assertTrue(rewriteRequests.containsKey("google.com"));
        assertTrue(rewriteRequests.containsKey("example.com"));
        assertFalse(rewriteRequests.containsKey("instagram.com"));
        assertFalse(rewriteRequests.containsKey("b.i.instagram.com"));
        assertFalse(rewriteRequests.containsKey("z-p42-chat-e2ee-ig.facebook.com"));
    }

    @Test
    void cleanupExistingFalseSkipsDeletingExcludedRewrites() {
        when(nextDnsRewriteClient.fetchRewrites()).thenReturn(List.of(
                new RewriteDto("instagram-id", "instagram.com", "157.240.245.174")
        ));

        Map<String, CreateRewriteDto> newRewriteRequests = new HashMap<>();
        newRewriteRequests.put("example.com", new CreateRewriteDto("example.com", "1.2.3.4"));

        List<CreateRewriteDto> createRewriteDtos = service.cleanupOutdated(newRewriteRequests, exclusionMatcher, false);

        verify(nextDnsRewriteClient).fetchRewrites();
        verify(nextDnsRewriteClient, never()).deleteRewriteById("instagram-id");
        assertEquals(1, createRewriteDtos.size());
        assertEquals("example.com", createRewriteDtos.get(0).name());
    }

    private List<HostsOverrideListsLoader.BypassRoute> loadRepresentativeOverrides() throws Exception {
        return Files.readAllLines(resourcePath())
                .stream()
                .map(this::toBypassRoute)
                .toList();
    }

    private Path resourcePath() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getResource("/geohidedns-sample.hosts")).toURI());
    }

    private HostsOverrideListsLoader.BypassRoute toBypassRoute(String line) {
        int delimiter = line.indexOf(' ');
        String ip = line.substring(0, delimiter);
        String website = line.substring(delimiter + 1).strip();
        if (website.startsWith("www.")) {
            website = website.substring("www.".length());
        }
        return new HostsOverrideListsLoader.BypassRoute(ip, website);
    }
}

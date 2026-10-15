package com.novibe.dns.next_dns.service;

import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextDnsRewriteServiceTest {

    private final NextDnsRewriteClient client = mock(NextDnsRewriteClient.class);
    private final ExcludeRedirectCheckService excludeRedirectCheckService = mock(ExcludeRedirectCheckService.class);
    private final NextDnsRewriteService service = new NextDnsRewriteService(client, excludeRedirectCheckService);

    @Test
    void removesExistingRewritesOutsideSelectedSection() {
        when(client.fetchRewrites()).thenReturn(List.of(
                new RewriteDto("gemini-id", "gemini.google.com", "1.2.3.4"),
                new RewriteDto("claude-id", "claude.ai", "1.2.3.4")));
        Map<String, CreateRewriteDto> requested = new HashMap<>();
        requested.put("gemini.google.com", new CreateRewriteDto("gemini.google.com", "1.2.3.4"));

        List<CreateRewriteDto> pending = service.cleanupOutdatedAndExcluded(requested, true);

        assertEquals(List.of(), pending);
        verify(client).deleteRewritesByIds(List.of("claude-id"));
    }

    @Test
    void keepsExistingRewritesOutsideSourceWhenSectionIsNotSelected() {
        when(client.fetchRewrites()).thenReturn(List.of(new RewriteDto("claude-id", "claude.ai", "1.2.3.4")));
        Map<String, CreateRewriteDto> requested = new HashMap<>();
        requested.put("gemini.google.com", new CreateRewriteDto("gemini.google.com", "2.3.4.5"));

        List<CreateRewriteDto> pending = service.cleanupOutdatedAndExcluded(requested, false);

        assertEquals(List.of(new CreateRewriteDto("gemini.google.com", "2.3.4.5")), pending);
        verify(client, never()).deleteRewritesByIds(anyList());
    }
}

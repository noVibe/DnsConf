package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteService {

    private final NextDnsRewriteClient nextDnsRewriteClient;

    public Map<String, CreateRewriteDto> buildNewRewrites(List<HostsOverrideListsLoader.BypassRoute> overrides) {
        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        overrides.forEach(route -> rewriteDtos.putIfAbsent(route.website(), new CreateRewriteDto(route.website(), route.ip())));
        return rewriteDtos;
    }

    /**
     * Replaces the complete Rewrite configuration with the validated source snapshot.
     * Denylist is deliberately not accessed by this service.
     */
    public void replaceAllAndVerify(Map<String, CreateRewriteDto> expectedRewrites) {
        if (expectedRewrites == null || expectedRewrites.isEmpty()) {
            throw new IllegalStateException("Rewrite source produced no valid domains; existing NextDNS Rewrite rules were left untouched.");
        }

        List<RewriteDto> existing = getExistingRewrites();
        List<String> existingIds = existing.stream().map(RewriteDto::id).toList();

        if (!existingIds.isEmpty()) {
            Log.io("Removing all %s existing Rewrite rules".formatted(existingIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(existingIds, nextDnsRewriteClient::deleteRewriteById);
        }

        List<CreateRewriteDto> requests = List.copyOf(expectedRewrites.values());
        Log.io("Creating %s Rewrite rules from source".formatted(requests.size()));
        NextDnsRateLimitedApiProcessor.callApi(requests, nextDnsRewriteClient::saveRewrite);

        Log.io("Verifying applied Rewrite rules in NextDNS");
        List<RewriteDto> actual = nextDnsRewriteClient.fetchRewrites();
        Set<String> expected = new HashSet<>();
        expectedRewrites.values().forEach(rule -> expected.add(rule.name() + "=" + rule.content()));
        Set<String> received = new HashSet<>();
        actual.forEach(rule -> received.add(rule.name() + "=" + rule.content()));

        if (!expected.equals(received)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(received);
            Set<String> unexpected = new HashSet<>(received);
            unexpected.removeAll(expected);
            throw new IllegalStateException("NextDNS Rewrite verification failed. Expected %s rules, received %s; missing=%s, unexpected=%s"
                    .formatted(expected.size(), received.size(), missing.size(), unexpected.size()));
        }
        Log.common("Rewrite successfully verified: %s rules".formatted(actual.size()));
    }

    public List<RewriteDto> getExistingRewrites() {
        Log.io("Fetching existing rewrites from NextDNS");
        return nextDnsRewriteClient.fetchRewrites();
    }

    public void saveRewrites(List<CreateRewriteDto> createRewriteDtos) {
        Log.io("Saving %s new rewrites to NextDNS...".formatted(createRewriteDtos.size()));
        NextDnsRateLimitedApiProcessor.callApi(createRewriteDtos, nextDnsRewriteClient::saveRewrite);
    }

    public void removeAll() {
        List<RewriteDto> list = getExistingRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing %s rewrites from NextDNS".formatted(ids.size()));
        NextDnsRateLimitedApiProcessor.callApi(ids, nextDnsRewriteClient::deleteRewriteById);
    }
}

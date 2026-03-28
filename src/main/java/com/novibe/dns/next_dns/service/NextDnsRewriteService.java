package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteService {
    
    private final NextDnsRewriteClient nextDnsRewriteClient;

    public Map<String, CreateRewriteDto> buildNewRewrites(
            List<HostsOverrideListsLoader.BypassRoute> overrides,
            Optional<WildcardDomainMatcher> exclusionMatcher
    ) {
        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        int excludedCounter = 0;

        for (HostsOverrideListsLoader.BypassRoute route : overrides) {
            if (matchesExclusion(exclusionMatcher, route.website())) {
                excludedCounter++;
                continue;
            }

            rewriteDtos.putIfAbsent(route.website(), new CreateRewriteDto(route.website(), route.ip()));
        }

        if (excludedCounter > 0) {
            Log.io("Skipping %s rewrite candidates due to exclusion patterns".formatted(excludedCounter));
        }

        return rewriteDtos;
    }

    public List<CreateRewriteDto> cleanupOutdated(
            Map<String, CreateRewriteDto> newRewriteRequests,
            Optional<WildcardDomainMatcher> exclusionMatcher,
            boolean cleanupExistingExcluded
    ) {
        List<RewriteDto> existingRewrites = getExistingRewrites();

        List<String> outdatedIds = new ArrayList<>();
        List<String> excludedIds = new ArrayList<>();

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            if (matchesExclusion(exclusionMatcher, domain)) {
                if (cleanupExistingExcluded) {
                    excludedIds.add(existingRewrite.id());
                }
                continue;
            }

            String oldIp = existingRewrite.content();
            CreateRewriteDto request = newRewriteRequests.get(domain);
            if (nonNull(request) && !request.content().equals(oldIp)) {
                outdatedIds.add(existingRewrite.id());
            } else {
                newRewriteRequests.remove(domain);
            }
        }
        if (!outdatedIds.isEmpty()) {
            Log.io("Removing %s outdated rewrites from NextDNS".formatted(outdatedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(outdatedIds, nextDnsRewriteClient::deleteRewriteById);
        }

        if (!excludedIds.isEmpty()) {
            Log.io("Removing %s excluded rewrites from NextDNS".formatted(excludedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(excludedIds, nextDnsRewriteClient::deleteRewriteById);
        }

        return List.copyOf(newRewriteRequests.values());
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
        Log.io("Fetching existing rewrites from NextDNS");
        List<RewriteDto> list = nextDnsRewriteClient.fetchRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing rewrites from NextDNS");
        NextDnsRateLimitedApiProcessor.callApi(ids, nextDnsRewriteClient::deleteRewriteById);
    }

    private boolean matchesExclusion(Optional<WildcardDomainMatcher> exclusionMatcher, String domain) {
        return exclusionMatcher.map(matcher -> matcher.matches(domain)).orElse(false);
    }

}

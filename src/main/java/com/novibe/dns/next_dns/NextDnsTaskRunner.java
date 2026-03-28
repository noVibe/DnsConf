package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.data_sources.HostsBlockListsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.config.NextDnsRewriteExclusionConfig;
import com.novibe.dns.next_dns.config.NextDnsRewriteExclusionParser;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsDenyService;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import com.novibe.dns.next_dns.service.WildcardDomainMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.NEXTDNS_REWRITE_EXCLUSIONS;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner implements DnsTaskRunner {

    private final HostsBlockListsLoader blockListsLoader;
    private final HostsOverrideListsLoader overrideListsLoader;
    private final NextDnsRewriteExclusionParser nextDnsRewriteExclusionParser;
    private final NextDnsRewriteService nextDnsRewriteService;
    private final NextDnsDenyService nextDnsDenyService;

    @Override
    public void run() {

        Log.global("NextDNS");
        Log.common("""
        Script behaviour: old block/redirect settings are about to be updated via provided block/redirect sources.
        If no sources provided, then all NextDNS settings will be removed.
        If provided only one type of sources, related settings will be updated; another type remain untouched.
        NextDNS API rate limiter reset config: 60 seconds after the last request""");

        List<String> blockSources = EnvParser.parse(BLOCK);
        if (!blockSources.isEmpty()) {
            Log.step("Obtain block lists from %s sources".formatted(blockSources.size()));
            List<String> blocks = blockListsLoader.fetchWebsites(blockSources);
            Log.step("Prepare denylist");
            List<String> filteredBlocklist = nextDnsDenyService.dropExistingDenys(blocks);
            Log.common("Prepared %s domains to block".formatted(filteredBlocklist.size()));
            Log.step("Save denylist");
            nextDnsDenyService.saveDenyList(filteredBlocklist);
        } else {
            Log.fail("No block sources provided");
        }

        List<String> rewriteSources = EnvParser.parse(REDIRECT);
        if (!rewriteSources.isEmpty()) {

            Log.step("Obtain rewrite lists from %s sources".formatted(rewriteSources.size()));
            List<HostsOverrideListsLoader.BypassRoute> overrides = overrideListsLoader.fetchWebsites(rewriteSources);
            Optional<NextDnsRewriteExclusionConfig> exclusionConfig = nextDnsRewriteExclusionParser.parse(NEXTDNS_REWRITE_EXCLUSIONS);
            Optional<WildcardDomainMatcher> exclusionMatcher = exclusionConfig
                    .map(NextDnsRewriteExclusionConfig::patterns)
                    .map(WildcardDomainMatcher::new);

            exclusionConfig.ifPresent(config -> Log.common(
                    "Loaded %s NextDNS rewrite exclusion patterns. Existing cleanup: %s"
                            .formatted(config.patterns().size(), config.cleanupExisting())
            ));

            Log.step("Prepare rewrites");
            Map<String, CreateRewriteDto> requests = nextDnsRewriteService.buildNewRewrites(overrides, exclusionMatcher);
            List<CreateRewriteDto> createRewriteDtos = nextDnsRewriteService.cleanupOutdated(
                    requests,
                    exclusionMatcher,
                    exclusionConfig.map(NextDnsRewriteExclusionConfig::cleanupExisting).orElse(false)
            );

            Log.step("Save rewrites");
            nextDnsRewriteService.saveRewrites(createRewriteDtos);
        } else {
            Log.fail("No rewrite sources provided");
        }

        if (blockSources.isEmpty() && rewriteSources.isEmpty()) {
            Log.step("Remove settings");
            nextDnsDenyService.removeAll();
            nextDnsRewriteService.removeAll();
        }

        Log.global("FINISHED");
    }

}

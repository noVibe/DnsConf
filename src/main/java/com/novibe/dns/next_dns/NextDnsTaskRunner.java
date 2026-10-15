package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.util.DonorDnsUtils;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsDenyService;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner extends DnsTaskRunner {

    private final NextDnsRewriteService nextDnsRewriteService;
    private final NextDnsDenyService nextDnsDenyService;

    @Override
    public void greetingMessage() {
        Log.global("Setting up Profile " + dnsProfile.number() + " (NextDNS)");
        Log.common("""
                Script behaviour: old BLOCK/REDIRECT settings are about to be updated via provided BLOCK/REDIRECT sources.
                - if no sources provided, then all NextDNS settings will be removed.
                - each line is mapped to an IP–domain pair; lines that cannot be parsed are skipped.
                - if provided only one type of sources, related settings will be updated; another type remain untouched.
                - if EXCLUDE_REDIRECT domains provided, they will affect both existing and new redirect rules.
                - if REDIRECT_SECTION is set, only that source section is used; NextDNS rewrites outside it are removed.
                NextDNS api rate limiter reset config: 60 seconds after the last request""");
    }

    @Override
    protected void process() {
        List<String> blockSources = EnvParser.parse(BLOCK);
        if (!blockSources.isEmpty()) {
            Log.step("Obtain block lists from %s sources".formatted(blockSources.size()));
            List<String> blocks = blockListsLoader.fetchWebsites(blockSources);
            Log.step("Prepare denylist");
            List<String> filteredBlocklist = nextDnsDenyService.omitExistingDenys(blocks);
            Log.common("Prepared %s domains to block".formatted(filteredBlocklist.size()));
            Log.step("Save denylist");
            nextDnsDenyService.saveDenyList(filteredBlocklist);
        } else {
            Log.fail("No block sources provided");
        }

        List<String> rewriteSources = EnvParser.parse(REDIRECT);
        if (!rewriteSources.isEmpty()) {

            Log.step("Obtain rewrite lists from %s sources".formatted(rewriteSources.size()));
            List<BypassRoute> overrides = overrideListsLoader.fetchWebsites(rewriteSources);

            if (nonNull(dnsProfile.donorDns())) {
                Log.step("Replace IP of domains via IPs from " + dnsProfile.donorDns());
                DonorDnsUtils.replaceIPs(overrides, dnsProfile);
            }

            Log.step("Prepare rewrites");
            Map<String, CreateRewriteDto> requests = nextDnsRewriteService.buildNewRewrites(overrides);
            List<CreateRewriteDto> createRewriteDtos = nextDnsRewriteService.cleanupOutdatedAndExcluded(
                    requests, overrideListsLoader.isSectionRestricted());

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
    }

    @Override
    protected void finishMessage() {
        Log.global("Profile " + dnsProfile.number() + " (NextDNS) set up successfully");
    }
}

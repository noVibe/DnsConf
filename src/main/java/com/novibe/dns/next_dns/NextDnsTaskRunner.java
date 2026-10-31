package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.REDIRECT;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner implements DnsTaskRunner {

    private final HostsOverrideListsLoader overrideListsLoader;
    private final NextDnsRewriteService nextDnsRewriteService;

    @Override
    public void run() {
        Log.global("NextDNS Rewrite");
        Log.common("""
        Script behaviour: completely rebuilds only the Rewrite rules from REDIRECT sources.
        Existing Denylist and all other NextDNS settings are left untouched.
        If REDIRECT is empty or produces no valid domains, the existing Rewrite rules are left untouched.
        """);

        List<String> rewriteSources = EnvParser.parse(REDIRECT);
        if (rewriteSources.isEmpty()) {
            throw new IllegalStateException("No REDIRECT sources configured; refusing to change existing Rewrite rules.");
        }

        Log.step("Obtain rewrite lists from %s sources".formatted(rewriteSources.size()));
        List<HostsOverrideListsLoader.BypassRoute> overrides = overrideListsLoader.fetchWebsites(rewriteSources);
        Log.common("Loaded %s valid Rewrite domains".formatted(overrides.size()));

        Log.step("Build complete Rewrite snapshot");
        Map<String, com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto> rewrites =
                nextDnsRewriteService.buildNewRewrites(overrides);
        Log.common("Prepared %s unique Rewrite rules".formatted(rewrites.size()));

        Log.step("Replace and verify NextDNS Rewrite rules");
        nextDnsRewriteService.replaceAllAndVerify(rewrites);

        Log.global("FINISHED");
    }
}

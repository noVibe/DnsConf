package com.novibe.dns.cloudflare;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.data_sources.HostsBlockListsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader.BypassRoute;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.cloudflare.http.dto.response.list.GatewayListDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import com.novibe.dns.cloudflare.service.ListService;
import com.novibe.dns.cloudflare.service.RulePrecedenceCounter;
import com.novibe.dns.cloudflare.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;


@Service
@RequiredArgsConstructor
public class CloudflareTaskRunner implements DnsTaskRunner {

    private final HostsBlockListsLoader blockListsLoader;
    private final HostsOverrideListsLoader overrideListsLoader;
    private final ListService listService;
    private final RuleService ruleService;

    @Override
    public void run() {

        Log.global("CLOUDFLARE");
        Log.common("""
        Script behaviour: previously generated data is always about to be removed.
        If you want to clear Cloudflare block/redirect settings, launch this script without providing sources in related environment variables.""");

        List<String> blocks = blockListsLoader.fetchWebsites(EnvParser.parse(BLOCK));
        List<BypassRoute> overrides = overrideListsLoader.fetchWebsites(EnvParser.parse(REDIRECT));

        Log.step("Remove old rules.");
        List<GatewayRuleDto> gatewayRuleDtos = ruleService.obtainExistingRules();
        List<GatewayRuleDto> remainingRules = ruleService.removeOldRules(gatewayRuleDtos);
        RulePrecedenceCounter precedenceCounter = RulePrecedenceCounter.providePrecedenceCounter(remainingRules);

        Log.step("Remove old lists.");
        listService.removeOldLists();

        Log.step("Creating new block lists");
        if (!blocks.isEmpty()) {
            List<GatewayListDto> gatewayListDtos = listService.createNewBlockLists(blocks);

            Log.step("Creating new blocking rule");
            ruleService.createNewBlockingRule(gatewayListDtos, precedenceCounter);
        } else {
            Log.fail("Websites to block were not provided");
        }

        Log.step("Creating new override lists");
        if (!overrides.isEmpty()) {
            Map<String, List<GatewayListDto>> newOverrideLists = listService.createNewOverrideLists(overrides);

            Log.step("Creating new override rules");
            ruleService.createNewOverrideRules(newOverrideLists, precedenceCounter);
        } else {
            Log.fail("Websites to override were not provided");
        }

        Log.global("FINISHED");
    }
}

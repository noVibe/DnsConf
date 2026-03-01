package com.novibe.dns.cloudflare.service;

import com.novibe.common.util.Log;
import com.novibe.dns.cloudflare.http.CloudflareRuleClient;
import com.novibe.dns.cloudflare.http.dto.request.CreateRuleRequest;
import com.novibe.dns.cloudflare.http.dto.response.list.GatewayListDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.SingleRuleApiResponse;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleService {

    private static final String RULES_LIST_NAME_PREFIX = "Rules set by script";

    private final CloudflareRuleClient cloudflareRuleClient;
    private final String sessionId;

    public void createNewBlockingRule(List<GatewayListDto> lists, RulePrecedenceCounter rulePrecedenceCounter) {
        String traffic = makeTrafficExpression(lists);
        CreateRuleRequest rule = CreateRuleRequest.builder()
                .name(RULES_LIST_NAME_PREFIX + ": block")
                .precedence(rulePrecedenceCounter.next())
                .action("block")
                .description(sessionId)
                .filters(List.of("dns"))
                .enabled(true)
                .traffic(traffic)
                .build();
        Log.io("Posting new blocking rule");
        SingleRuleApiResponse result = cloudflareRuleClient.createBlockingRule(rule);
        if (!result.isSuccess()) {
            Log.fail("Failed to set blocking rule: " + result.getErrors());
        }
    }

    @SneakyThrows
    @SuppressWarnings("preview")
    public void createNewOverrideRules(Map<String, List<GatewayListDto>> lists, RulePrecedenceCounter rulePrecedenceCounter) {
        @Cleanup var scope = StructuredTaskScope.open();
        for (Map.Entry<String, List<GatewayListDto>> entry : lists.entrySet()) {
            String overrideIp = entry.getKey();
            List<GatewayListDto> list = entry.getValue();
            scope.fork(() -> createNewOverrideRule(list, overrideIp, rulePrecedenceCounter.next()));
        }
        scope.join();
    }

    private void createNewOverrideRule(List<GatewayListDto> lists, String overrideIp, int precedence) {
        String traffic = makeTrafficExpression(lists);
        CreateRuleRequest rule = CreateRuleRequest.builder()
                .name(RULES_LIST_NAME_PREFIX + " override to IP -> " + overrideIp)
                .precedence(precedence)
                .action("override")
                .description(sessionId)
                .filters(List.of("dns"))
                .enabled(true)
                .traffic(traffic)
                .ruleSettings(new CreateRuleRequest.RuleSettings(List.of(overrideIp)))
                .build();
        Log.io("Posting new override rule for IP: " + overrideIp);
        SingleRuleApiResponse result = cloudflareRuleClient.createBlockingRule(rule);
        if (!result.isSuccess()) {
            Log.fail("Failed to set override rule: " + result.getErrors());
        }
    }

    public List<GatewayRuleDto> obtainExistingRules() {
        return cloudflareRuleClient.getRules().getResult();
    }

    public List<GatewayRuleDto> removeOldRules(List<GatewayRuleDto> rules) {
        List<GatewayRuleDto> removeList = rules.stream()
                .filter(rule -> rule.getName().startsWith(RULES_LIST_NAME_PREFIX))
                .filter(rule -> !sessionId.equals(rule.getDescription()))
                .toList();
        Log.io("Removing " + removeList.size() + " old rules...");
        int counter = 0;
        for (GatewayRuleDto rule : removeList) {
            String id = rule.getId();
            SingleRuleApiResponse result = cloudflareRuleClient.removeRuleById(id);
            if (!result.isSuccess()) {
                Log.fail("Failed to remove old rule with id %s: %s".formatted(id, result.getErrors()));
            } else {
                Log.progress(++counter + "/" + removeList.size());
                rules.remove(rule);
            }
        }
        Log.common("\n%s of %s old rules have been removed".formatted(counter, removeList.size()));
        return rules;
    }

    private String makeTrafficExpression(List<GatewayListDto> lists) {
        List<String> listIds = lists.stream()
                .map(GatewayListDto::getId)
                .map(UUID::toString)
                .toList();

        return listIds.stream()
                .map("any(dns.domains[*] in $%s)"::formatted)
                .collect(Collectors.joining(" or "));
    }

}

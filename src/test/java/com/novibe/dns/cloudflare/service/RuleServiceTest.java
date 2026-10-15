package com.novibe.dns.cloudflare.service;

import com.novibe.common.exception.DnsApiException;
import com.novibe.dns.cloudflare.http.CloudflareRuleClient;
import com.novibe.dns.cloudflare.http.dto.request.CreateRuleRequest;
import com.novibe.dns.cloudflare.http.dto.response.CloudflareApiMessage;
import com.novibe.dns.cloudflare.http.dto.response.rule.SingleRuleApiResponse;
import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.MultiRuleApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleServiceTest {

    @Test
    void returnsEmptyListWhenApiAnswersWithoutResult() {
        RuleService ruleService = ruleServiceAnswering(null);

        assertTrue(ruleService.obtainExistingRules().isEmpty());
    }

    @Test
    void keepsRulesMutableSoOldOnesCanBeRemoved() {
        GatewayRuleDto rule = new GatewayRuleDto();
        RuleService ruleService = ruleServiceAnswering(List.of(rule));

        List<GatewayRuleDto> rules = ruleService.obtainExistingRules();
        rules.remove(rule);

        assertEquals(List.of(), rules);
    }

    @Test
    void failsWhenApiRefusesToCreateBlockingRule() {
        SingleRuleApiResponse refused = new SingleRuleApiResponse();
        refused.setSuccess(false);
        refused.setErrors(List.of(new CloudflareApiMessage(1004, "invalid traffic expression", null, null)));
        RuleService ruleService = new RuleService(new StubRuleClient(new MultiRuleApiResponse(), refused), "session-id");

        DnsApiException exception = assertThrows(DnsApiException.class,
                () -> ruleService.createNewBlockingRule(List.of(), RulePrecedenceCounter.providePrecedenceCounter(List.of())));

        assertTrue(exception.getMessage().startsWith("Failed to set blocking rule"));
    }

    private RuleService ruleServiceAnswering(List<GatewayRuleDto> result) {
        MultiRuleApiResponse response = new MultiRuleApiResponse();
        response.setResult(result);
        return new RuleService(new StubRuleClient(response, new SingleRuleApiResponse()), "session-id");
    }

    private static class StubRuleClient extends CloudflareRuleClient {

        private final MultiRuleApiResponse rules;
        private final SingleRuleApiResponse createResponse;

        private StubRuleClient(MultiRuleApiResponse rules, SingleRuleApiResponse createResponse) {
            super(null);
            this.rules = rules;
            this.createResponse = createResponse;
        }

        @Override
        public MultiRuleApiResponse getRules() {
            return rules;
        }

        @Override
        public SingleRuleApiResponse createBlockingRule(CreateRuleRequest rule) {
            return createResponse;
        }
    }
}

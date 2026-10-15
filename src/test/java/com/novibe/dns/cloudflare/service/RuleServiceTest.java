package com.novibe.dns.cloudflare.service;

import com.novibe.common.exception.DnsApiException;
import com.novibe.dns.cloudflare.http.CloudflareRuleClient;
import com.novibe.dns.cloudflare.http.dto.response.CloudflareApiMessage;
import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.MultiRuleApiResponse;
import com.novibe.dns.cloudflare.http.dto.response.rule.SingleRuleApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    private static final String SESSION_ID = "session-id";

    @Mock
    private CloudflareRuleClient cloudflareRuleClient;

    private RuleService ruleService;

    @BeforeEach
    void setUp() {
        ruleService = new RuleService(cloudflareRuleClient, SESSION_ID);
    }

    @Test
    void returnsEmptyListWhenApiAnswersWithoutResult() {
        when(cloudflareRuleClient.getRules()).thenReturn(rulesAnswer(null));

        assertTrue(ruleService.obtainExistingRules().isEmpty());
    }

    @Test
    void keepsRulesMutableSoOldOnesCanBeRemoved() {
        GatewayRuleDto rule = new GatewayRuleDto();
        when(cloudflareRuleClient.getRules()).thenReturn(rulesAnswer(List.of(rule)));

        List<GatewayRuleDto> rules = ruleService.obtainExistingRules();
        rules.remove(rule);

        assertEquals(List.of(), rules);
    }

    @Test
    void failsWhenApiRefusesToCreateBlockingRule() {
        when(cloudflareRuleClient.createBlockingRule(any())).thenReturn(refusedAnswer());

        DnsApiException exception = assertThrows(DnsApiException.class,
                () -> ruleService.createNewBlockingRule(List.of(), RulePrecedenceCounter.providePrecedenceCounter(List.of())));

        assertTrue(exception.getMessage().startsWith("Failed to set blocking rule"));
    }

    @Test
    void failsWhenApiRefusesToCreateOverrideRule() {
        when(cloudflareRuleClient.createBlockingRule(any())).thenReturn(refusedAnswer());

        DnsApiException exception = assertThrows(DnsApiException.class,
                () -> ruleService.createNewOverrideRules(Map.of("1.2.3.4", List.of()),
                        RulePrecedenceCounter.providePrecedenceCounter(List.of())));

        assertTrue(exception.getMessage().startsWith("Failed to set override rule for IP 1.2.3.4"));
    }

    @Test
    void keepsOldRuleWhenApiRefusesToRemoveIt() {
        GatewayRuleDto oldRule = new GatewayRuleDto();
        oldRule.setId("rule-id");
        oldRule.setName("Rules set by script: block");
        oldRule.setDescription("another-session");
        when(cloudflareRuleClient.removeRuleById("rule-id")).thenReturn(refusedAnswer());

        List<GatewayRuleDto> rules = ruleService.removeOldRules(new ArrayList<>(List.of(oldRule)));

        assertEquals(List.of(oldRule), rules);
    }

    private MultiRuleApiResponse rulesAnswer(List<GatewayRuleDto> result) {
        MultiRuleApiResponse response = new MultiRuleApiResponse();
        response.setResult(result);
        return response;
    }

    private SingleRuleApiResponse refusedAnswer() {
        SingleRuleApiResponse response = new SingleRuleApiResponse();
        response.setSuccess(false);
        response.setErrors(List.of(new CloudflareApiMessage(1004, "invalid traffic expression", null, null)));
        return response;
    }

}

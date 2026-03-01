package com.novibe.dns.cloudflare.service;

import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every Cloudflare rule must have precedence. This class provides precedence value in collision aware way.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class RulePrecedenceCounter {

    private final Set<Integer> skipSet;
    private int number = 1;

    @Synchronized
    int next() {
        while (skipSet.contains(number)) {
            number++;
        }
        skipSet.add(number);
        return number++;
    }

    public static RulePrecedenceCounter providePrecedenceCounter(List<GatewayRuleDto> existingRules) {
        Set<Integer> existingValues = existingRules.stream()
                .map(GatewayRuleDto::getPrecedence)
                .collect(Collectors.toSet());
        return new RulePrecedenceCounter(existingValues);
    }
}

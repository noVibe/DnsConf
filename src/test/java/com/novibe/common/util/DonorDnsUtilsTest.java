package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.ProfileSetupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DonorDnsUtilsTest {

    private static SequencedSet<String> donorIps(String... ips) {
        return new LinkedHashSet<>(List.of(ips));
    }

    @Test
    void keepsCurrentIpWhenDonorStillReturnsIt() {
        assertEquals("1.1.1.2", DonorDnsUtils.chooseIp("1.1.1.2", donorIps("1.1.1.1", "1.1.1.2", "1.1.1.3")));
    }

    @Test
    void takesFirstDonorIpWhenCurrentIpIsOutdated() {
        assertEquals("1.1.1.1", DonorDnsUtils.chooseIp("9.9.9.9", donorIps("1.1.1.1", "1.1.1.2")));
    }

    @Test
    void keepsCurrentIpWhenDonorReturnsNothing() {
        assertEquals("9.9.9.9", DonorDnsUtils.chooseIp("9.9.9.9", donorIps()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void reportsUnexpectedWorkerFailureWithTheDomainAndCause(int failedIndex) {
        BypassRoute route = mock(BypassRoute.class);
        RuntimeException failure = new IllegalStateException("lookup failed unexpectedly");
        // First call logs submission; the second happens inside the submitted worker.
        when(route.website()).thenReturn("failed.example").thenThrow(failure);
        DnsProfile profile = DnsProfile.builder().donorDns("127.0.0.1").build();
        BypassRoute otherRoute = new BypassRoute("9.9.9.9", "invalid..example");
        List<BypassRoute> routes = failedIndex == 0 ? List.of(route, otherRoute) : List.of(otherRoute, route);

        ProfileSetupException thrown = assertThrows(ProfileSetupException.class,
                () -> DonorDnsUtils.replaceIPs(routes, profile));

        assertTrue(thrown.getMessage().contains("failed.example"));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void invalidDomainStillKeepsTheOriginalIp() {
        BypassRoute route = new BypassRoute("9.9.9.9", "invalid..example");
        DnsProfile profile = DnsProfile.builder().donorDns("127.0.0.1").build();

        DonorDnsUtils.replaceIPs(List.of(route), profile);

        assertEquals("9.9.9.9", route.ip());
    }
}

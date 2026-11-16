package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.ProfileSetupException;
import com.novibe.common.exception.UserInputException;
import lombok.Cleanup;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public class DonorDnsUtils {

    public static void replaceIPs(List<BypassRoute> bypassRoutes, DnsProfile dnsProfile) {
        Resolver dnsResolver = getDnsResolver(dnsProfile);
        dnsResolver.setTimeout(Duration.ofSeconds(5));
        @Cleanup ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Replacement> replacements = new ArrayList<>();
        for (BypassRoute bypassRoute : bypassRoutes) {
            String domain = bypassRoute.website();
            Log.io("Comparing IP for %s with DNS query to %s".formatted(domain, dnsProfile.donorDns()));
            replacements.add(new Replacement(domain, executor.submit(() -> replaceIp(bypassRoute, dnsResolver))));
        }
        for (Replacement replacement : replacements) {
            try {
                replacement.task().get();
            } catch (ExecutionException e) {
                throw new ProfileSetupException("Failed to replace donor DNS IP for " + replacement.domain(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProfileSetupException("Interrupted while replacing donor DNS IPs", e);
            }
        }
    }

    private record Replacement(String domain, Future<?> task) {
    }

    private static void replaceIp(BypassRoute bypassRoute, Resolver dnsResolver) {
        SequencedSet<String> donorIps = fetchDonorIps(bypassRoute.website(), dnsResolver);
        String newIp = chooseIp(bypassRoute.ip(), donorIps);
        if (!bypassRoute.ip().equals(newIp)) {
            Log.common("Changed IP for %s: %s -> %s".formatted(bypassRoute.website(), bypassRoute.ip(), newIp));
            bypassRoute.ip(newIp);
        }
    }

    // Donor may return several IPs in random order, so keep current IP if it is still among them
    static String chooseIp(String currentIp, SequencedSet<String> donorIps) {
        if (donorIps.isEmpty() || donorIps.contains(currentIp)) {
            return currentIp;
        }
        return donorIps.getFirst();
    }

    private static Resolver getDnsResolver(DnsProfile dnsProfile) {
        String dns = dnsProfile.donorDns();
        try {
            if (dns.startsWith("http")) {
                return new DohResolver(dns);
            } else if (Address.isDottedQuad(dns)) {
                return new SimpleResolver(dns);
            } else {
                throw new UnknownHostException();
            }
        } catch (UnknownHostException e) {
            throw UserInputException.noStackTrace("Invalid DONOR_DNS value: %s. Value must follow Ipv4 or DoH format".formatted(dns));
        }
    }

    private static SequencedSet<String> fetchDonorIps(String domain, Resolver resolver) {
        try {
            Lookup lookup = new Lookup(domain, Type.A);
            lookup.setResolver(resolver);
            Record[] records = lookup.run();
            if (isNull(records)) {
                return new LinkedHashSet<>();
            }
            return Arrays.stream(records)
                    .filter(ARecord.class::isInstance)
                    .map(record -> ((ARecord) record).getAddress().getHostAddress())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (TextParseException e) {
            Log.fail("Invalid domain address: " + domain);
            return new LinkedHashSet<>();
        }
    }

}

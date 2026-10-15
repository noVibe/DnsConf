package com.novibe;


import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.ProfileFailureException;
import com.novibe.common.exception.ProfileSetupException;
import com.novibe.common.exception.UserInputException;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.common.util.ProfileFailuresCollector;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static java.util.Objects.nonNull;

public class App {

    static void main() {
        final List<DnsProfile> dnsProfiles = EnvParser.parseProfiles();
        final AnnotationConfigApplicationContext commonContext = loadCommonApplicationContext();
        final ProfileFailuresCollector profileFailuresCollector = new ProfileFailuresCollector();

        for (DnsProfile dnsProfile : dnsProfiles) {
            AnnotationConfigApplicationContext currentContext = null;
            try {
                currentContext = loadCurrentProfileContext(dnsProfile, commonContext);

                DnsTaskRunner runner = currentContext.getBean(DnsTaskRunner.class);
                runner.run();

            } catch (ProfileSetupException profileSetupException) {
                Log.fail("%s on profile %s".formatted(profileSetupException.getClass().getSimpleName(), dnsProfile.number()));
                Log.fail(profileSetupException.getMessage());
                profileFailuresCollector.add(dnsProfile, profileSetupException);
            } catch (Exception exception) {
                Log.fail("Unexpected exception on profile " + dnsProfile.number());
                throw exception;
            } finally {
                if (nonNull(currentContext)) currentContext.close();
            }
        }
        commonContext.close();

        if (!profileFailuresCollector.isEmpty()) {
            throw ProfileFailureException.noStackTrace(profileFailuresCollector.summary(dnsProfiles.size()));
        }
    }

    private static AnnotationConfigApplicationContext loadCommonApplicationContext() {
        String commonsBasePackage = "com.novibe.common";
        return new AnnotationConfigApplicationContext(commonsBasePackage);
    }

    private static @NonNull AnnotationConfigApplicationContext loadCurrentProfileContext(DnsProfile dnsProfile, ApplicationContext commonContext) {
        String dnsBasePackage = switch (dnsProfile.dnsProvider()) {
            case "CLOUDFLARE" -> "com.novibe.dns.cloudflare";
            case "NEXTDNS" -> "com.novibe.dns.next_dns";
            default ->
                    throw UserInputException.noStackTrace("Unsupported DNS provider! Must be CLOUDFLARE or NEXTDNS. Was: " + dnsProfile.dnsProvider());
        };
        AnnotationConfigApplicationContext currentContext = new AnnotationConfigApplicationContext();
        currentContext.setParent(commonContext);
        currentContext.scan(dnsBasePackage);
        currentContext.registerBean(DnsProfile.class, () -> dnsProfile);
        currentContext.refresh();
        return currentContext;
    }

}

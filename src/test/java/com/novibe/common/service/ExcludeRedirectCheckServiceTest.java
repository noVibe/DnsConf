package com.novibe.common.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcludeRedirectCheckServiceTest {

    private static ExcludeRedirectCheckService serviceFor(String... domains) {
        ExcludeRedirectSettingsLoader loader = mock(ExcludeRedirectSettingsLoader.class);
        when(loader.loadIgnoredDomains()).thenReturn(List.of(domains));
        return new ExcludeRedirectCheckService(loader);
    }

    @Test
    void excludesDomainItself() {
        assertTrue(serviceFor("instagram.com").shouldExclude("instagram.com"));
    }

    @Test
    void excludesSubdomains() {
        ExcludeRedirectCheckService service = serviceFor("instagram.com");
        assertTrue(service.shouldExclude("api.instagram.com"));
        assertTrue(service.shouldExclude("a.b.instagram.com"));
    }

    @Test
    void doesNotExcludeOtherDomainsWithSameEnding() {
        ExcludeRedirectCheckService service = serviceFor("x.com", "ai.com");
        assertFalse(service.shouldExclude("netflix.com"));
        assertFalse(service.shouldExclude("dropbox.com"));
        assertFalse(service.shouldExclude("openai.com"));
        assertFalse(service.shouldExclude("chat.openai.com"));
    }
}

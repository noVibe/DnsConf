package com.novibe.common.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExcludeRedirectCheckService {

    private final List<String> ignoringList;

    public ExcludeRedirectCheckService(ExcludeRedirectSettingsLoader excludeRedirectSettingsLoader) {
        ignoringList = excludeRedirectSettingsLoader.loadIgnoredDomains();
        System.out.println("[DEBUG] EXCLUDE_REDIRECT loaded: " + ignoringList);
    }

    public boolean shouldExclude(String domain) {
        for (String ignored : ignoringList) {
            if (domain.endsWith(ignored)) {
                System.out.println("[DEBUG] EXCLUDED: " + domain + " (matched: " + ignored + ")");
                return true;
            }
        }
        return false;
    }
}

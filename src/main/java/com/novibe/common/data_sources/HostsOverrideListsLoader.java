package com.novibe.common.data_sources;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.exception.UserInputException;
import com.novibe.common.util.DataParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
public class HostsOverrideListsLoader extends ListLoader<BypassRoute> {

    protected String selectedSection() {
        return System.getenv("REDIRECT_SECTION");
    }

    public boolean isSectionRestricted() {
        String section = selectedSection();
        return section != null && !section.isBlank();
    }

    @Override
    protected Stream<String> sourceLines(String source) {
        if (!isSectionRestricted()) {
            return super.sourceLines(source);
        }

        String section = selectedSection().strip();
        List<String> lines = DataParser.splitByEol(source).toList();
        String heading = "# " + section;
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(heading)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            throw UserInputException.noStackTrace("Redirect section not found: " + section);
        }

        int end = start;
        while (end < lines.size() && !lines.get(end).strip().startsWith("#")) {
            end++;
        }
        return lines.subList(start, end).stream();
    }

    @Override
    public List<BypassRoute> fetchWebsites(List<String> urls) {
        List<BypassRoute> routes = super.fetchWebsites(urls);
        if (isSectionRestricted() && routes.isEmpty()) {
            throw UserInputException.noStackTrace("Redirect section has no valid routes: " + selectedSection());
        }
        return routes;
    }

    @Override
    protected String listType() {
        return "Override";
    }

    @Override
    protected Predicate<HostsLine> filterRelatedLines() {
        return line -> line.hasIpAndDomain() && !HostsBlockListsLoader.isBlockIp(line.ip());

    }

    @Override
    protected BypassRoute toObject(HostsLine line) {
        return new BypassRoute(line.ip(), line.domain());
    }

}
